package com.uvend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "batch")
@Getter @Setter @NoArgsConstructor
public class Batch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "batch_no", nullable = false, unique = true, length = 48)
    private String batchNo;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;
    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;
    @Column(name = "inbound_qty", nullable = false)
    private Integer inboundQty;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
