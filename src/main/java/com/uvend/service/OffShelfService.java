package com.uvend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uvend.domain.entity.*;
import com.uvend.domain.enums.*;
import com.uvend.domain.repo.*;
import com.uvend.event.DomainEvent;
import com.uvend.event.EventPublisher;
import com.uvend.event.Topics;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 下架任务执行：
 * - complete：实物下架（OFF_SHELF 流水），可转报损（过期/损坏/温控），批次可结算追踪
 * - relocate：放错货道归位（actual 货道 MISPLACE_OUT → 正确货道 MISPLACE_IN）
 * - skip：现场核实无需下架（如临期商品已被买走，任务数量与库存不符时）
 */
@Service
public class OffShelfService {

    private final OffShelfTaskRepository taskRepository;
    private final InventoryRepository inventoryRepository;
    private final SlotRepository slotRepository;
    private final MachineRepository machineRepository;
    private final BatchRepository batchRepository;
    private final InventoryService inventoryService;
    private final LossService lossService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper mapper;

    public OffShelfService(OffShelfTaskRepository taskRepository, InventoryRepository inventoryRepository,
                           SlotRepository slotRepository, MachineRepository machineRepository,
                           BatchRepository batchRepository, InventoryService inventoryService,
                           LossService lossService, EventPublisher eventPublisher, ObjectMapper mapper) {
        this.taskRepository = taskRepository;
        this.inventoryRepository = inventoryRepository;
        this.slotRepository = slotRepository;
        this.machineRepository = machineRepository;
        this.batchRepository = batchRepository;
        this.inventoryService = inventoryService;
        this.lossService = lossService;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<OffShelfTask> pending(String machineCode) {
        if (machineCode == null || machineCode.isBlank()) {
            return taskRepository.findByStatus(OffShelfStatus.PENDING);
        }
        Machine m = findMachine(machineCode);
        return taskRepository.findByMachineId(m.getId()).stream()
                .filter(t -> t.getStatus() == OffShelfStatus.PENDING).toList();
    }

    @Transactional
    public Map<String, Object> complete(String taskNo, Long operatorId, boolean convertToLoss, String note) {
        OffShelfTask task = getPending(taskNo);
        int onHand = inventoryService.qtyOfSlotBatch(task.getSlotId(), task.getBatchId());
        int qty = Math.min(task.getQty(), onHand);
        if (qty <= 0) {
            task.setStatus(OffShelfStatus.SKIPPED);
            task.setOperatorId(operatorId);
            task.setCompletedAt(Instant.now());
            task.setNote("执行时该货道该批次库存为 0：" + note);
            taskRepository.save(task);
            return Map.of("taskNo", taskNo, "status", task.getStatus().name(), "message", "库存已为 0，跳过");
        }

        inventoryService.apply(task.getMachineId(), task.getSlotId(), task.getBatchId(), -qty,
                LedgerReason.OFF_SHELF, "OFF_SHELF_TASK", task.getId());

        String lossNo = null;
        if (convertToLoss) {
            LossReason lr = switch (task.getReason()) {
                case EXPIRED, NEAR_EXPIRY -> LossReason.EXPIRED;
                case TEMP_ABUSE -> LossReason.TEMP_ABUSE;
                case DAMAGED -> LossReason.DAMAGED;
                case MISPLACED -> LossReason.MISPLACED;
            };
            LossRecord loss = lossService.record(task.getBatchId(), task.getMachineId(), task.getSlotId(),
                    qty, lr, "OFF_SHELF_TASK", task.getId());
            lossNo = loss.getLossNo();
            task.setStatus(OffShelfStatus.CONVERTED_LOSS);
        } else {
            task.setStatus(OffShelfStatus.DONE);
        }
        task.setOperatorId(operatorId);
        task.setCompletedAt(Instant.now());
        task.setNote(note);
        taskRepository.save(task);

        publish(task, "OffShelfCompleted", Map.of(
                "taskNo", taskNo, "qty", qty, "convertedToLoss", convertToLoss,
                "lossNo", lossNo == null ? "" : lossNo));
        return Map.of("taskNo", taskNo, "status", task.getStatus().name(), "qty", qty,
                "lossNo", lossNo == null ? "" : lossNo);
    }

    @Transactional
    public Map<String, Object> relocate(String taskNo, String targetSlotCode, Long operatorId) {
        OffShelfTask task = getPending(taskNo);
        if (task.getReason() != OffShelfReason.MISPLACED) {
            throw new IllegalArgumentException("仅放错货道任务可归位");
        }
        Slot target = slotRepository.findByMachineIdAndCode(task.getMachineId(), targetSlotCode)
                .orElseThrow(() -> new EntityNotFoundException("目标货道不存在: " + targetSlotCode));
        int qty = Math.min(task.getQty(), inventoryService.qtyOfSlotBatch(task.getSlotId(), task.getBatchId()));
        if (qty <= 0) throw new IllegalStateException("错放货道已无库存，无法归位");

        inventoryService.apply(task.getMachineId(), task.getSlotId(), task.getBatchId(), -qty,
                LedgerReason.MISPLACE_OUT, "OFF_SHELF_TASK", task.getId());
        inventoryService.apply(task.getMachineId(), target.getId(), task.getBatchId(), qty,
                LedgerReason.MISPLACE_IN, "OFF_SHELF_TASK", task.getId());

        task.setStatus(OffShelfStatus.DONE);
        task.setOperatorId(operatorId);
        task.setCompletedAt(Instant.now());
        task.setNote("归位至 " + targetSlotCode);
        taskRepository.save(task);
        publish(task, "MisplacedRelocated", Map.of(
                "taskNo", taskNo, "qty", qty, "targetSlot", targetSlotCode));
        return Map.of("taskNo", taskNo, "status", task.getStatus().name(),
                "qty", qty, "targetSlot", targetSlotCode);
    }

    @Transactional
    public Map<String, Object> skip(String taskNo, Long operatorId, String note) {
        OffShelfTask task = getPending(taskNo);
        task.setStatus(OffShelfStatus.SKIPPED);
        task.setOperatorId(operatorId);
        task.setCompletedAt(Instant.now());
        task.setNote(note);
        taskRepository.save(task);
        return Map.of("taskNo", taskNo, "status", task.getStatus().name());
    }

    private OffShelfTask getPending(String taskNo) {
        OffShelfTask task = taskRepository.findAll().stream()
                .filter(t -> t.getTaskNo().equals(taskNo)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("下架任务不存在: " + taskNo));
        if (task.getStatus() != OffShelfStatus.PENDING) {
            throw new IllegalStateException("任务非待处理状态: " + task.getStatus());
        }
        return task;
    }

    private Machine findMachine(String code) {
        return machineRepository.findAll().stream().filter(m -> m.getCode().equals(code)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("货柜不存在: " + code));
    }

    private void publish(OffShelfTask task, String type, Map<String, Object> extra) {
        Machine m = machineRepository.findById(task.getMachineId()).orElse(null);
        Batch b = batchRepository.findById(task.getBatchId()).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.put("machineCode", m == null ? "" : m.getCode());
        payload.put("batchNo", b == null ? "" : b.getBatchNo());
        payload.put("reason", task.getReason().name());
        payload.put("occurredAt", Instant.now().toString());
        eventPublisher.publish(new DomainEvent(Topics.EXPIRY,
                m == null ? String.valueOf(task.getMachineId()) : m.getCode(), type, json(payload)));
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}
