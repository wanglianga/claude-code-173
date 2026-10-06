package com.uvend.event;

import com.uvend.domain.entity.DomainEventLog;
import com.uvend.domain.repo.DomainEventLogRepository;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 事件发布基类：任何模式下都先写 outbox（domain_event_log），保证可回放，
 * 再按实现投递（本地进程事件 / Kafka）。
 */
public abstract class AbstractEventPublisher implements EventPublisher {

    protected final DomainEventLogRepository eventLogRepository;

    protected AbstractEventPublisher(DomainEventLogRepository eventLogRepository) {
        this.eventLogRepository = eventLogRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void publish(DomainEvent event) {
        DomainEventLog row = new DomainEventLog();
        row.setTopic(event.topic());
        row.setEventKey(event.key());
        row.setEventType(event.type());
        row.setPayload(event.payloadJson());
        row.setOccurredAt(Instant.now());
        eventLogRepository.save(row);
        dispatch(event, row.getId());
    }

    /** 投递钩子：成功后置 publishedAt（Kafka 实现在 ack 回调中标记）。 */
    protected abstract void dispatch(DomainEvent event, Long outboxId);

    /** 本地进程事件（供本模块 @EventListener 消费）。 */
    public static class InProcessDomainEvent extends ApplicationEvent {
        private final DomainEvent domainEvent;
        public InProcessDomainEvent(Object source, DomainEvent domainEvent) {
            super(source);
            this.domainEvent = domainEvent;
        }
        public DomainEvent getDomainEvent() { return domainEvent; }
    }
}
