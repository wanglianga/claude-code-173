package com.cabinet.service;

import com.cabinet.config.KafkaConfig;
import com.cabinet.kafka.dto.BizEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BizEventPublisher {

    private final KafkaTemplate<String, BizEvent> kafkaTemplate;

    public void publish(String type, String refNo, String cabinetNo, String batchNo,
                        String message, java.math.BigDecimal amount) {
        BizEvent event = BizEvent.builder()
                .type(type)
                .refNo(refNo)
                .cabinetNo(cabinetNo)
                .batchNo(batchNo)
                .message(message)
                .amount(amount)
                .occurredAt(LocalDateTime.now())
                .build();
        kafkaTemplate.send(KafkaConfig.TOPIC_BIZ, refNo == null ? cabinetNo : refNo, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.warn("发布业务事件失败 type={} ref={}", type, refNo, ex);
                    }
                });
    }
}
