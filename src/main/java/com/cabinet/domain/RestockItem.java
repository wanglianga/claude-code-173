package com.cabinet.domain;

import com.cabinet.domain.enums.PlacementWarning;
import com.cabinet.domain.enums.TempZone;
import jakarta.persistence.*;
import lombok.*;

/** 补货明细：柜机编号 + 商品批次 + 保质期 + 数量 + 货道 + 温度要求 + 照片 */
@Entity
@Table(name = "restock_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestockItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RestockOrder restockOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    private ProductBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    /** 实际放入货道（可能与计划货道不一致 -> 放错货道） */
    private String actualChannelCode;

    private Integer qty;

    /** 该商品要求温区（快照） */
    @Enumerated(EnumType.STRING)
    private TempZone requiredTempZone;

    /** 上架时柜机温度（快照） */
    private Double cabinetTempC;

    /** 上架照片 */
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PlacementWarning warning = PlacementWarning.NONE;

    private String warningDetail;
}
