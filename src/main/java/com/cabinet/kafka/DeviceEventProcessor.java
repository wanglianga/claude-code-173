package com.cabinet.kafka;

import com.cabinet.domain.Cabinet;
import com.cabinet.domain.enums.AlertType;
import com.cabinet.domain.enums.RemovalReason;
import com.cabinet.kafka.dto.DeviceEvent;
import com.cabinet.repo.CabinetRepository;
import com.cabinet.service.AlertService;
import com.cabinet.service.SessionSettlementService;
import com.cabinet.service.TemperatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 设备事件处理器：Kafka 消费端与 HTTP 模拟入口共用同一套业务逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceEventProcessor {

    private final CabinetRepository cabinetRepository;
    private final SessionSettlementService sessionService;
    private final TemperatureService temperatureService;
    private final AlertService alertService;

    @Transactional
    public void process(DeviceEvent event) {
        LocalDateTime at = event.getEventTime() != null ? event.getEventTime() : LocalDateTime.now();
        switch (event.getType()) {
            case "DOOR_OPEN" -> sessionService.openDoor(
                    event.getCabinetNo(), event.getSessionNo(),
                    event.getUserCode(), event.getUserName(), at);
            case "RECOGNITION" -> sessionService.recordRecognition(
                    event.getSessionNo(), event.getCabinetNo(), event.getChannelCode(),
                    event.getSku(), event.getBatchNo(), event.getSource(), event.getAction(),
                    event.getQty(), event.getConfidence(), event.getWeightGram(),
                    event.getVideoClipUrl(), event.getClipStartSec(), event.getClipEndSec(),
                    at, null);
            case "DOOR_CLOSE" -> sessionService.closeDoor(
                    event.getSessionNo(), event.getWeightDeltaGram(), at);
            case "TEMP" -> {
                Cabinet cabinet = cabinetRepository.findByCabinetNo(event.getCabinetNo()).orElse(null);
                if (cabinet != null && event.getTempC() != null) {
                    temperatureService.record(cabinet, event.getTempC(), at);
                } else {
                    log.warn("温度事件柜机不存在: {}", event.getCabinetNo());
                }
            }
            case "HEARTBEAT" -> cabinetRepository.findByCabinetNo(event.getCabinetNo())
                    .ifPresent(c -> {
                        c.setLastHeartbeatAt(at);
                        if (event.getTempC() != null) c.setLastTempC(event.getTempC());
                        cabinetRepository.save(c);
                    });
            case "CHANNEL_JAM" -> cabinetRepository.findByCabinetNo(event.getCabinetNo())
                    .ifPresent(c -> alertService.raise(c, AlertType.CHANNEL_JAM, "WARN",
                            "货道 " + event.getJamChannelCode() + " 卡货", "CHANNEL_JAM", null));
            default -> log.warn("未知设备事件类型: {}", event.getType());
        }
    }
}
