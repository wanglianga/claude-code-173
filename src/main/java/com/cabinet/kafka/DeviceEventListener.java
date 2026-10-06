package com.cabinet.kafka;

import com.cabinet.config.KafkaConfig;
import com.cabinet.kafka.dto.DeviceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 设备事件 Kafka 消费端。按 cabinetNo 分区（生产端用 cabinetNo 作 key），
 * 保证同一柜机事件有序；失败重试 2 次后进入 cabinet.device.event.DLT。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceEventListener {

    private final DeviceEventProcessor processor;

    @KafkaListener(topics = KafkaConfig.TOPIC_DEVICE,
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "3")
    public void onEvent(@Payload DeviceEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        try {
            processor.process(event);
        } catch (Exception e) {
            log.error("处理设备事件失败 session={} type={}: {}",
                    event.getSessionNo(), event.getType(), e.getMessage());
            throw e;
        }
    }
}
