package com.uvend.domain.entity;

import com.uvend.domain.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "settlement")
@Getter @Setter @NoArgsConstructor
public class Settlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "settlement_no", nullable = false, unique = true, length = 48)
    private String settlementNo;
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(name = "sales_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal salesAmount = BigDecimal.ZERO;
    @Column(name = "loss_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lossAmount = BigDecimal.ZERO;
    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal payableAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SettlementStatus status = SettlementStatus.DRAFT;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
