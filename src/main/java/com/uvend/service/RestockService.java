package com.uvend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uvend.domain.entity.*;
import com.uvend.domain.enums.*;
import com.uvend.domain.repo.*;
import com.uvend.event.DomainEvent;
import com.uvend.event.EventPublisher;
import com.uvend.event.Topics;
import com.uvend.web.dto.RestockItemRequest;
import com.uvend.web.dto.RestockRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 补货服务：
 * - 记录柜机、批次、保质期、数量、货道、温度要求、补货照片
 * - 处理：放错货道（设备复核 actualSlotCode）、货道卡货、温度要求不符、冷藏柜温度异常锁定
 * - 每次补货写入库存流水（RESTOCK），并发布 uvend.restock 事件
 */
@Service
public class RestockService {

    private final ReplenishmentRepository replenishmentRepository;
    private final ReplenishmentItemRepository itemRepository;
    private final MachineRepository machineRepository;
    private final SlotRepository slotRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final OffShelfTaskRepository offShelfTaskRepository;
    private final InventoryService inventoryService;
    private final EventPublisher eventPublisher;
    private final Numbers numbers;
    private final ObjectMapper objectMapper;

    public RestockService(ReplenishmentRepository replenishmentRepository,
                          ReplenishmentItemRepository itemRepository,
                          MachineRepository machineRepository, SlotRepository slotRepository,
                          BatchRepository batchRepository, ProductRepository productRepository,
                          OffShelfTaskRepository offShelfTaskRepository,
                          InventoryService inventoryService, EventPublisher eventPublisher,
                          Numbers numbers, ObjectMapper objectMapper) {
        this.replenishmentRepository = replenishmentRepository;
        this.itemRepository = itemRepository;
        this.machineRepository = machineRepository;
        this.slotRepository = slotRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.offShelfTaskRepository = offShelfTaskRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
        this.numbers = numbers;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> restock(Long staffId, RestockRequest req) {
        Machine machine = machineRepository.findAll().stream()
                .filter(m -> m.getCode().equals(req.machineCode())).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("货柜不存在: " + req.machineCode()));
        if (Boolean.TRUE.equals(machine.getLocked())) {
            throw new IllegalStateException("货柜因温度异常已锁定，禁止补货，请先处理温控告警: " + machine.getCode());
        }

        Replenishment r = new Replenishment();
        r.setReplenishNo(numbers.next("RP"));
        r.setMachineId(machine.getId());
        r.setStaffId(staffId);
        r.setStatus(ReplenishmentStatus.IN_PROGRESS);
        r.setPhotoUrl(req.photoUrl());
        r.setNote(req.note());
        replenishmentRepository.save(r);

        List<Map<String, Object>> itemResults = new ArrayList<>();
        for (RestockItemRequest it : req.items()) {
            itemResults.add(applyItem(machine, r, it));
        }

        r.setStatus(ReplenishmentStatus.COMPLETED);
        r.setCompletedAt(Instant.now());
        replenishmentRepository.save(r);

        return Map.of(
                "replenishNo", r.getReplenishNo(),
                "machineCode", machine.getCode(),
                "status", r.getStatus().name(),
                "items", itemResults);
    }

    private Map<String, Object> applyItem(Machine machine, Replenishment r, RestockItemRequest it) {
        Batch batch = batchRepository.findAll().stream()
                .filter(b -> b.getBatchNo().equals(it.batchNo())).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("批次不存在: " + it.batchNo()));
        Product product = productRepository.findById(batch.getProductId()).orElseThrow();
        Slot planned = slotRepository.findByMachineIdAndCode(machine.getId(), it.slotCode())
                .orElseThrow(() -> new EntityNotFoundException("货道不存在: " + it.slotCode()));

        TempClass itemTemp = TempClass.valueOf(it.tempRequirement());
        if (itemTemp != machine.getMachineType() && itemTemp != TempClass.AMBIENT) {
            throw new IllegalArgumentException(
                    "温度要求不符: 商品需 " + itemTemp + "，货柜为 " + machine.getMachineType()
                            + "（食品安全拦截，批次 " + batch.getBatchNo() + "）");
        }

        ReplenishmentItem row = new ReplenishmentItem();
        row.setReplenishmentId(r.getId());
        row.setBatchId(batch.getId());
        row.setSlotId(planned.getId());
        row.setQty(it.qty());
        row.setTempRequirement(itemTemp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batch.getBatchNo());
        result.put("product", product.getName());
        result.put("expiryDate", batch.getExpiryDate().toString());
        result.put("slotCode", planned.getCode());
        result.put("qty", it.qty());

        // 1) 货道卡货：商品未正常入位，不增库存，记录 JAMMED
        if (it.jammed()) {
            row.setState(ItemState.JAMMED);
            itemRepository.save(row);
            result.put("state", ItemState.JAMMED.name());
            result.put("message", "货道卡货，商品未入库存，需现场处理");
            publishRestock(r, machine, batch, planned, planned, it.qty(), ItemState.JAMMED.name());
            return result;
        }

        // 2) 设备复核实际货道：与申报不一致 = 放错货道
        Slot actual = planned;
        if (it.actualSlotCode() != null && !it.actualSlotCode().isBlank()) {
            actual = slotRepository.findByMachineIdAndCode(machine.getId(), it.actualSlotCode())
                    .orElseThrow(() -> new EntityNotFoundException("实际货道不存在: " + it.actualSlotCode()));
        }

        if (!actual.getId().equals(planned.getId())) {
            // 放错货道：实物在 actual 货道，库存记到 actual，同时生成 MISPLACED 下架/归位任务
            row.setActualSlotId(actual.getId());
            row.setState(ItemState.MISPLACED);
            itemRepository.save(row);
            inventoryService.apply(machine.getId(), actual.getId(), batch.getId(), it.qty(),
                    LedgerReason.MISPLACE_IN, "REPLENISHMENT", r.getId());

            OffShelfTask task = new OffShelfTask();
            task.setTaskNo(numbers.next("OS"));
            task.setMachineId(machine.getId());
            task.setSlotId(actual.getId());
            task.setBatchId(batch.getId());
            task.setQty(it.qty());
            task.setReason(OffShelfReason.MISPLACED);
            task.setNote("补货员申报 " + planned.getCode() + "，设备复核实际在 " + actual.getCode());
            offShelfTaskRepository.save(task);

            result.put("state", ItemState.MISPLACED.name());
            result.put("actualSlotCode", actual.getCode());
            result.put("offShelfTaskNo", task.getTaskNo());
            result.put("message", "放错货道，已生成归位任务 " + task.getTaskNo());
            publishRestock(r, machine, batch, planned, actual, it.qty(), ItemState.MISPLACED.name());
            return result;
        }

        // 3) 正常上架
        row.setState(ItemState.PLACED);
        itemRepository.save(row);
        inventoryService.apply(machine.getId(), planned.getId(), batch.getId(), it.qty(),
                LedgerReason.RESTOCK, "REPLENISHMENT", r.getId());
        result.put("state", ItemState.PLACED.name());
        publishRestock(r, machine, batch, planned, planned, it.qty(), ItemState.PLACED.name());
        return result;
    }

    private void publishRestock(Replenishment r, Machine machine, Batch batch, Slot planned, Slot actual,
                                int qty, String state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("replenishNo", r.getReplenishNo());
        payload.put("machineCode", machine.getCode());
        payload.put("batchNo", batch.getBatchNo());
        payload.put("plannedSlot", planned.getCode());
        payload.put("actualSlot", actual.getCode());
        payload.put("qty", qty);
        payload.put("state", state);
        payload.put("photoUrl", r.getPhotoUrl());
        payload.put("occurredAt", Instant.now().toString());
        eventPublisher.publish(new DomainEvent(Topics.RESTOCK, machine.getCode(), "Restocked",
                json(payload)));
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
