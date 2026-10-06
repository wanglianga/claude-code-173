package com.uvend.domain.entity;

import com.uvend.domain.enums.Bearer;
import com.uvend.domain.enums.LossReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "loss_record")
@Getter @Setter @NoArgsConstructor
public class LossRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "loss_no", nullable = false, unique = true, length = 48)
    private String lossNo;
    @Column(name = "batch_id", nullable = false)
    private Long batchId;
    @Column(name = "machine_id")
    private Long machineId;
    @Column(name = "slot_id")
    private Long slotId;
    @Column(nullable = false)
    private Integer qty;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LossReason reason;
    @Column(name = "ref_type", length = 24)
    private String refType;
    @Column(name = "ref_id")
    private Long refId;
    @Column(name = "cost_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal costAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Bearer bearer = Bearer.SUPPLIER;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
