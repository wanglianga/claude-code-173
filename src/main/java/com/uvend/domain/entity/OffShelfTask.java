package com.uvend.domain.entity;

import com.uvend.domain.enums.OffShelfReason;
import com.uvend.domain.enums.OffShelfStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "off_shelf_task")
@Getter @Setter @NoArgsConstructor
public class OffShelfTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_no", nullable = false, unique = true, length = 48)
    private String taskNo;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "slot_id", nullable = false)
    private Long slotId;
    @Column(name = "batch_id", nullable = false)
    private Long batchId;
    @Column(nullable = false)
    private Integer qty;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OffShelfReason reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OffShelfStatus status = OffShelfStatus.PENDING;
    @Column(name = "operator_id")
    private Long operatorId;
    private String note;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "completed_at")
    private Instant completedAt;
}
