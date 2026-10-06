package com.uvend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "domain_event_log")
@Getter @Setter @NoArgsConstructor
public class DomainEventLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String topic;
    @Column(name = "event_key", length = 128)
    private String eventKey;
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    @Column(nullable = false, length = 4000)
    private String payload;
    @Column(name = "occurred_at")
    private Instant occurredAt = Instant.now();
    @Column(name = "published_at")
    private Instant publishedAt;
}
