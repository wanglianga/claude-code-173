package com.cabinet.domain;

import com.cabinet.domain.enums.Disposition;
import jakarta.persistence.*;
import lombok.*;

/** 下架明细：精确到货道与批次，记录执行数量与去向（报损/退供） */
@Entity
@Table(name = "removal_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemovalItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RemovalOrder removalOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    private ProductBatch batch;

    /** 计划下架数量 */
    private Integer plannedQty;

    /** 实际执行数量 */
    private Integer executedQty;

    @Enumerated(EnumType.STRING)
    private Disposition disposition;
}
