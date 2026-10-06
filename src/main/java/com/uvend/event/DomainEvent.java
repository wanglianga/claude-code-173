package com.uvend.event;

/** 领域事件：所有跨模块/对外事件统一落 domain_event_log（outbox，可回放）。 */
public record DomainEvent(String topic, String key, String type, String payloadJson) {
}
