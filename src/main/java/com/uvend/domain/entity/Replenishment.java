package com.uvend.domain.entity;

import com.uvend.domain.enums.ReplenishmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "replenishment")
@Getter @Setter @NoArgsConstructor
public class Replenishment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "replenish_no", nullable = false, unique = true, length = 48)
    private String replenishNo;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "staff_id", nullable = false)
    private Long staffId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReplenishmentStatus status = ReplenishmentStatus.PLANNED;
    @Column(name = "photo_url", length = 512)
    private String photoUrl;
    private String note;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "completed_at")
    private Instant completedAt;
}
