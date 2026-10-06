package com.cabinet.domain;

import com.cabinet.domain.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 供应商结算单：按周期汇总真实售卖成本与报损 */
@Entity
@Table(name = "settlement_batch",
        indexes = @Index(columnList = "supplier_id,period_start"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementBatch extends BaseEntity {

    private String settlementNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.DRAFT;

    /** 真实售出数量/成本 */
    @Builder.Default
    private Integer soldQty = 0;
    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal soldCost = BigDecimal.ZERO;

    /** 供应商责任报损数量/金额 */
    @Builder.Default
    private Integer lossQty = 0;
    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal lossCost = BigDecimal.ZERO;

    /** 应付款 = 售卖成本 - 供应商责任报损 */
    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal payableAmount = BigDecimal.ZERO;

    private LocalDateTime confirmedAt;
    private LocalDateTime paidAt;

    private String remark;
}
