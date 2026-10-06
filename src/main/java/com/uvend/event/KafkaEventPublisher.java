package com.uvend.event;

import com.uvend.domain.entity.DomainEventLog;
import com.uvend.domain.repo.DomainEventLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Kafka 实现：业务事务内事件写 outbox；afterCommit 立即投递；
 * 另有定时 relay 兜底重放 published_at IS NULL 的事件（至少一次，消费端需幂等）。
 */
@Component
@ConditionalOnProperty(name = "uvend.eventbus", havingValue = "kafka")
public class KafkaEventPublisher extends AbstractEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventPublisher(DomainEventLogRepository eventLogRepository,
                               KafkaTemplate<String, String> kafkaTemplate) {
        super(eventLogRepository);
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    protected void dispatch(DomainEvent event, Long outboxId) {
        // afterCommit 发送，失败也不影响 outbox；relay 会兜底
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        send(event, outboxId);
                    }
                });
    }

    private void send(DomainEvent event, Long outboxId) {
        kafkaTemplate.send(event.topic(), event.key(), event.payloadJson())
                .whenComplete((r, ex) -> {
                    if (ex == null) {
                        markPublished(outboxId);
                    }
                });
    }

    @Transactional
    @Scheduled(fixedDelayString = "${uvend.kafka.relay-ms:5000}")
    public void relayUnpublished() {
        List<DomainEventLog> pending = eventLogRepository.findFirst100ByPublishedAtIsNullOrderByIdAsc();
        for (DomainEventLog row : pending) {
            kafkaTemplate.send(row.getTopic(), row.getEventKey(), row.getPayload())
                    .whenComplete((r, ex) -> {
                        if (ex == null) {
                            markPublished(row.getId());
                        }
                    });
        }
    }

    private void markPublished(Long id) {
        eventLogRepository.findById(id).ifPresent(r -> {
            if (r.getPublishedAt() == null) {
                r.setPublishedAt(Instant.now());
                eventLogRepository.save(r);
            }
        });
    }
}
