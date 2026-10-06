package com.cabinet.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 货道-批次库存。补货上架后在对应货道形成批次库存；
 * 售卖按 FEFO（先到期先出）扣减。
 */
@Entity
@Table(name = "channel_stock",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "batch_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelStock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @Builder.Default
    private Integer qtyOnHand = 0;

    /** 因温控异常/临期被隔离的数量（不可售卖） */
    @Builder.Default
    private Integer qtyQuarantined = 0;
}
