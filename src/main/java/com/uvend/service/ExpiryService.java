package com.uvend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uvend.domain.entity.Batch;
import com.uvend.domain.entity.Inventory;
import com.uvend.domain.entity.Machine;
import com.uvend.domain.entity.OffShelfTask;
import com.uvend.domain.enums.OffShelfReason;
import com.uvend.domain.enums.OffShelfStatus;
import com.uvend.domain.repo.*;
import com.uvend.event.DomainEvent;
import com.uvend.event.EventPublisher;
import com.uvend.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临期/过期扫描：对在柜库存按批次保质期生成下架任务。
 * - expiry &lt;= today+warningDays：NEAR_EXPIRY 临期下架任务
 * - expiry &lt; today：EXPIRED（过期严禁售卖）
 * 幂等：同 slot+batch+reason 已有未完成任务则不重复生成。
 */
@Service
public class ExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ExpiryService.class);

    private final InventoryRepository inventoryRepository;
    private final BatchRepository batchRepository;
    private final MachineRepository machineRepository;
    private final OffShelfTaskRepository taskRepository;
    private final EventPublisher eventPublisher;
    private final Numbers numbers;
    private final ObjectMapper mapper;
    private final int warningDays;

    public ExpiryService(InventoryRepository inventoryRepository, BatchRepository batchRepository,
                         MachineRepository machineRepository, OffShelfTaskRepository taskRepository,
                         EventPublisher eventPublisher, Numbers numbers, ObjectMapper mapper,
                         @Value("${uvend.expiry.warning-days:1}") int warningDays) {
        this.inventoryRepository = inventoryRepository;
        this.batchRepository = batchRepository;
        this.machineRepository = machineRepository;
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
        this.numbers = numbers;
        this.mapper = mapper;
        this.warningDays = warningDays;
    }

    /** 每天 02:00 自动扫描；也可通过管理接口手动触发。 */
    @Scheduled(cron = "${uvend.expiry.cron:0 0 2 * * *}")
    @Transactional
    public int scheduledScan() {
        int created = scan();
        if (created > 0) log.info("临期扫描生成下架任务 {} 个", created);
        return created;
    }

    @Transactional
    public int scan() {
        LocalDate today = LocalDate.now();
        LocalDate nearLine = today.plusDays(warningDays);
        int created = 0;
        for (Inventory inv : inventoryRepository.findAll()) {
            if (inv.getQty() <= 0) continue;
            Batch batch = batchRepository.findById(inv.getBatchId()).orElse(null);
            if (batch == null) continue;

            OffShelfReason reason;
            if (batch.getExpiryDate().isBefore(today)) reason = OffShelfReason.EXPIRED;
            else if (!batch.getExpiryDate().isAfter(nearLine)) reason = OffShelfReason.NEAR_EXPIRY;
            else continue;

            boolean exists = taskRepository.findByBatchId(batch.getId()).stream()
                    .anyMatch(t -> t.getStatus() == OffShelfStatus.PENDING
                            && t.getSlotId().equals(inv.getSlotId())
                            && t.getReason() == reason);
            if (exists) continue;

            OffShelfTask task = new OffShelfTask();
            task.setTaskNo(numbers.next("OS"));
            task.setMachineId(inv.getMachineId());
            task.setSlotId(inv.getSlotId());
            task.setBatchId(batch.getId());
            task.setQty(inv.getQty());
            task.setReason(reason);
            task.setNote("保质期 " + batch.getExpiryDate() + "，扫描日 " + today);
            taskRepository.save(task);
            created++;

            Machine machine = machineRepository.findById(inv.getMachineId()).orElse(null);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskNo", task.getTaskNo());
            payload.put("machineCode", machine == null ? null : machine.getCode());
            payload.put("batchNo", batch.getBatchNo());
            payload.put("expiryDate", batch.getExpiryDate().toString());
            payload.put("qty", inv.getQty());
            payload.put("reason", reason.name());
            payload.put("occurredAt", Instant.now().toString());
            eventPublisher.publish(new DomainEvent(Topics.EXPIRY,
                    machine == null ? String.valueOf(inv.getMachineId()) : machine.getCode(),
                    "OffShelfTaskCreated", json(payload)));
        }
        return created;
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}
