package com.uvend.event;

/**
 * 事件发布端口。
 * 实现必须保证：事件先持久化到 outbox(domain_event_log)，再对外投递，至少一次。
 */
public interface EventPublisher {
    void publish(DomainEvent event);
}
