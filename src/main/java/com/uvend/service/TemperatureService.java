package com.uvend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uvend.domain.entity.*;
import com.uvend.domain.enums.*;
import com.uvend.domain.repo.*;
import com.uvend.event.DomainEvent;
import com.uvend.event.EventPublisher;
import com.uvend.event.Topics;
import com.uvend.web.dto.TemperatureRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 温控服务（食品安全）：
 * - 每次温度上报落 temperature_log
 * - 越界（低于 temp_min / 高于 temp_max）开启告警并锁柜，柜内所有批次质量冻结（quality_hold）
 * - 恢复正常：关闭告警、解锁；处置=RELEASED 解冻，或 CONDEMNED 整柜在柜批次报损下架
 * - 冻结期间补货被拦截（见 RestockService）、拿取不可自动售卖（见 ShoppingService）
 */
@Service
public class TemperatureService {

    private final MachineRepository machineRepository;
    private final TemperatureLogRepository logRepository;
    private final TemperatureAlertRepository alertRepository;
    private final QualityHoldRepository holdRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final LossService lossService;
    private final EventPublisher eventPublisher;
    private final Numbers numbers;
    private final ObjectMapper mapper;

    public TemperatureService(MachineRepository machineRepository, TemperatureLogRepository logRepository,
                              TemperatureAlertRepository alertRepository, QualityHoldRepository holdRepository,
                              InventoryRepository inventoryRepository, InventoryService inventoryService,
                              LossService lossService, EventPublisher eventPublisher, Numbers numbers,
                              ObjectMapper mapper) {
        this.machineRepository = machineRepository;
        this.logRepository = logRepository;
        this.alertRepository = alertRepository;
        this.holdRepository = holdRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.lossService = lossService;
        this.eventPublisher = eventPublisher;
        this.numbers = numbers;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> report(TemperatureRequest req) {
        Machine machine = findMachine(req.machineCode());
        BigDecimal t = req.tempC();
        TempStatus status = classify(machine, t);
        TemperatureLog log = new TemperatureLog();
        log.setMachineId(machine.getId());
        log.setTempC(t);
        log.setStatus(status);
        logRepository.save(log);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("machineCode", machine.getCode());
        out.put("tempC", t);
        out.put("status", status.name());

        var openAlert = alertRepository
                .findFirstByMachineIdAndStatusOrderByIdDesc(machine.getId(), AlertStatus.OPEN);

        if (status != TempStatus.NORMAL) {
            if (openAlert.isEmpty()) {
                TemperatureAlert alert = new TemperatureAlert();
                alert.setAlertNo(numbers.next("TA"));
                alert.setMachineId(machine.getId());
                alert.setStartedAt(Instant.now());
                alert.setMinTemp(t);
                alert.setMaxTemp(t);
                alert.setStatus(AlertStatus.OPEN);
                alertRepository.save(alert);
                machine.setLocked(true);
                machineRepository.save(machine);
                freezeInventory(machine, alert);
                out.put("alertNo", alert.getAlertNo());
                out.put("action", "OPEN_ALERT_AND_LOCK");
                publishTemp(machine, "TemperatureAlertOpened", Map.of(
                        "alertNo", alert.getAlertNo(), "tempC", t, "status", status.name()));
            } else {
                TemperatureAlert a = openAlert.get();
                a.setMinTemp(a.getMinTemp() == null ? t : a.getMinTemp().min(t));
                a.setMaxTemp(a.getMaxTemp() == null ? t : a.getMaxTemp().max(t));
                alertRepository.save(a);
                out.put("alertNo", a.getAlertNo());
                out.put("action", "ALERT_CONTINUES");
            }
        } else if (openAlert.isPresent()) {
            out.put("alertNo", openAlert.get().getAlertNo());
            out.put("action", "TEMP_RESTORED_AWAIT_DISPOSITION");
        }
        return out;
    }

    /**
     * 处置温控告警。
     * @param resolution RESTORED 放行解冻；CONDEMNED 判定在柜商品全部报损（温控损耗）并下架
     */
    @Transactional
    public Map<String, Object> resolve(String alertNo, String resolution, String note) {
        TemperatureAlert alert = alertRepository.findAll().stream()
                .filter(a -> a.getAlertNo().equals(alertNo)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("温控告警不存在: " + alertNo));
        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new IllegalStateException("告警已处置: " + alertNo);
        }
        AlertResolution r = AlertResolution.valueOf(resolution);
        Machine machine = machineRepository.findById(alert.getMachineId()).orElseThrow();

        int condemned = 0;
        if (r == AlertResolution.RELEASED || r == AlertResolution.RESTORED) {
            // 放行：解冻所有 ACTIVE 温控冻结
            holdRepository.findByMachineIdAndStatus(machine.getId(), HoldStatus.ACTIVE)
                    .stream().filter(h -> h.getReason() == HoldReason.TEMP_ABUSE)
                    .forEach(h -> {
                        h.setStatus(HoldStatus.RELEASED);
                        h.setResolvedAt(Instant.now());
                        h.setNote(note);
                        holdRepository.save(h);
                    });
            machine.setLocked(false);
            machineRepository.save(machine);
        } else if (r == AlertResolution.CONDEMNED) {
            List<Inventory> invs = inventoryRepository.findByMachineId(machine.getId());
            for (Inventory inv : invs) {
                if (inv.getQty() <= 0) continue;
                int qty = inv.getQty();
                // 下架库存
                inventoryService.apply(machine.getId(), inv.getSlotId(), inv.getBatchId(), -qty,
                        LedgerReason.LOSS, "TEMPERATURE_ALERT", alert.getId());
                LossRecord loss = lossService.record(inv.getBatchId(), machine.getId(), inv.getSlotId(),
                        qty, LossReason.TEMP_ABUSE, "TEMPERATURE_ALERT", alert.getId());
                holdRepository.findByBatchIdAndStatus(inv.getBatchId(), HoldStatus.ACTIVE)
                        .forEach(h -> {
                            h.setStatus(HoldStatus.CONDEMNED);
                            h.setResolvedAt(Instant.now());
                            holdRepository.save(h);
                        });
                condemned += qty;
                publishTemp(machine, "BatchCondemned", Map.of(
                        "alertNo", alertNo, "batchId", inv.getBatchId(), "qty", qty,
                        "lossNo", loss.getLossNo()));
            }
            machine.setLocked(false);
            machineRepository.save(machine);
        } else {
            throw new IllegalArgumentException("处置方式仅支持 RESTORED/RELEASED（放行）或 CONDEMNED（报损）");
        }

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolution(r);
        alert.setEndedAt(Instant.now());
        alert.setNote(note);
        alertRepository.save(alert);

        return Map.of("alertNo", alertNo, "resolution", r.name(),
                "condemnedQty", condemned, "machineLocked", machine.getLocked());
    }

    private TempStatus classify(Machine m, BigDecimal t) {
        if (m.getTempMin() == null || m.getTempMax() == null) return TempStatus.NORMAL;
        if (t.compareTo(m.getTempMin()) < 0 || t.compareTo(m.getTempMax()) > 0) return TempStatus.CRITICAL;
        // 边界 1 度内预警
        if (t.subtract(m.getTempMin()).abs().compareTo(BigDecimal.ONE) <= 0
                || m.getTempMax().subtract(t).compareTo(BigDecimal.ONE) <= 0) {
            return TempStatus.WARNING;
        }
        return TempStatus.NORMAL;
    }

    private void freezeInventory(Machine machine, TemperatureAlert alert) {
        for (Inventory inv : inventoryRepository.findByMachineId(machine.getId())) {
            if (inv.getQty() <= 0) continue;
            boolean exists = holdRepository.findByBatchIdAndStatus(inv.getBatchId(), HoldStatus.ACTIVE)
                    .stream().anyMatch(h -> h.getReason() == HoldReason.TEMP_ABUSE
                            && h.getMachineId().equals(machine.getId()));
            if (exists) continue;
            QualityHold hold = new QualityHold();
            hold.setMachineId(machine.getId());
            hold.setBatchId(inv.getBatchId());
            hold.setReason(HoldReason.TEMP_ABUSE);
            hold.setStatus(HoldStatus.ACTIVE);
            hold.setNote("告警 " + alert.getAlertNo() + " 触发热链冻结");
            holdRepository.save(hold);
        }
    }

    private void publishTemp(Machine machine, String type, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.put("machineCode", machine.getCode());
        payload.put("occurredAt", Instant.now().toString());
        eventPublisher.publish(new DomainEvent(Topics.TEMPERATURE, machine.getCode(), type,
                json(payload)));
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private Machine findMachine(String code) {
        return machineRepository.findAll().stream().filter(m -> m.getCode().equals(code)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("货柜不存在: " + code));
    }
}
