package com.uvend.domain.entity;

import com.uvend.domain.enums.HoldReason;
import com.uvend.domain.enums.HoldStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "quality_hold")
@Getter @Setter @NoArgsConstructor
public class QualityHold {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "batch_id", nullable = false)
    private Long batchId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldReason reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private HoldStatus status = HoldStatus.ACTIVE;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    private String note;
}
