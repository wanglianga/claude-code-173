package com.cabinet.domain;

import com.cabinet.domain.enums.LedgerType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 批次台账：每个批次的补货入、售卖出、下架出、报损出、
 * 盘点盘盈盘亏、退款回库与结算，逐笔留痕、双向可追。
 */
@Entity
@Table(name = "batch_ledger",
        indexes = {
                @Index(columnList = "batch_id"),
                @Index(columnList = "biz_type,biz_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchLedger extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    private Cabinet cabinet;

    @Enumerated(EnumType.STRING)
    private LedgerType ledgerType;

    /** 数量变化：入库为正，出库为负 */
    private Integer changeQty;

    /** 记账后批次全局在途/在库余量（该批次全部货道余量汇总） */
    private Integer balanceQty;

    /** 金额（成本口径，结算/报损用） */
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    /** 关联业务类型与主键（开门会话/订单/下架单/盘点单/结算单） */
    private String bizType;
    private Long bizId;

    private String remark;
}
