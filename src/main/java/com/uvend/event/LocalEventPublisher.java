package com.uvend.event;

import com.uvend.domain.repo.DomainEventLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * 默认实现：事件落 outbox 后，在当前事务提交后以 Spring 进程内事件广播，
 * 无需 Kafka 即可运行；事件仍完整持久化，支持事后重放。
 */
@Component
@ConditionalOnProperty(name = "uvend.eventbus", havingValue = "local", matchIfMissing = true)
public class LocalEventPublisher extends AbstractEventPublisher {

    private final ApplicationEventPublisher springPublisher;

    public LocalEventPublisher(DomainEventLogRepository eventLogRepository,
                               ApplicationEventPublisher springPublisher) {
        super(eventLogRepository);
        this.springPublisher = springPublisher;
    }

    @Override
    protected void dispatch(DomainEvent event, Long outboxId) {
        // 事务提交后再广播，避免消费者读到未提交数据
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    springPublisher.publishEvent(new InProcessDomainEvent(this, event));
                    markPublished(outboxId);
                }
            });
        } else {
            springPublisher.publishEvent(new InProcessDomainEvent(this, event));
            markPublished(outboxId);
        }
    }

    private void markPublished(Long outboxId) {
        eventLogRepository.findById(outboxId).ifPresent(r -> {
            if (r.getPublishedAt() == null) {
                r.setPublishedAt(Instant.now());
                eventLogRepository.save(r);
            }
        });
    }
}
