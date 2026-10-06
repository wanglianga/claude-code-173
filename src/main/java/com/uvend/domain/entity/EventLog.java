package com.uvend.domain.entity;

import com.uvend.domain.enums.DetectMethod;
import com.uvend.domain.enums.EventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "event_log")
@Getter @Setter @NoArgsConstructor
public class EventLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id")
    private Long sessionId;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(nullable = false)
    private Integer seq;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private EventType eventType;
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private DetectMethod method;
    @Column(nullable = false, length = 4000)
    private String payload = "{}";
    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;
    @Column(name = "occurred_at")
    private Instant occurredAt = Instant.now();
}
