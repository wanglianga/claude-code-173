package com.cabinet.domain;

import jakarta.persistence.*;
import lombok.*;

/** 盘点明细：系统库存 vs 实盘数量，差异经审批后调整库存并记账 */
@Entity
@Table(name = "stocktake_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private StocktakeOrder stocktake;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    private ProductBatch batch;

    private Integer systemQty;
    private Integer countedQty;
    private Integer diffQty;

    /** 差异是否已调整 */
    private boolean adjusted;
}
