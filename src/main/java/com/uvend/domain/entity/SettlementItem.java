package com.uvend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "settlement_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"settlement_id", "batch_id"}))
@Getter @Setter @NoArgsConstructor
public class SettlementItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;
    @Column(name = "batch_id", nullable = false)
    private Long batchId;
    @Column(name = "sold_qty", nullable = false)
    private Integer soldQty = 0;
    @Column(name = "sales_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal salesAmount = BigDecimal.ZERO;
    @Column(name = "loss_qty", nullable = false)
    private Integer lossQty = 0;
    @Column(name = "loss_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lossAmount = BigDecimal.ZERO;
    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal payableAmount = BigDecimal.ZERO;
}
