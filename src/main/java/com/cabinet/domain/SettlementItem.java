package com.cabinet.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** 结算单批次明细：每个批次的售出/报损与金额，供按批次对账 */
@Entity
@Table(name = "settlement_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SettlementBatch settlement;

    @ManyToOne(fetch = FetchType.LAZY)
    private ProductBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private Integer soldQty;
    private Integer lossQty;

    @Column(precision = 14, scale = 2)
    private BigDecimal soldCost;
    @Column(precision = 14, scale = 2)
    private BigDecimal lossCost;

    @Column(precision = 14, scale = 2)
    private BigDecimal payableAmount;
}
