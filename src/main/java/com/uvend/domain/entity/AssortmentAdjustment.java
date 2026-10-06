package com.uvend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "assortment_adjustment")
@Getter @Setter @NoArgsConstructor
public class AssortmentAdjustment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "slot_id", nullable = false)
    private Long slotId;
    @Column(name = "old_product_id")
    private Long oldProductId;
    @Column(name = "new_product_id")
    private Long newProductId;
    private String reason;
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
