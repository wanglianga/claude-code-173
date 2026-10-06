package com.cabinet.domain;

import com.cabinet.domain.enums.TempZone;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** 商品 SKU 主数据 */
@Entity
@Table(name = "product",
        uniqueConstraints = @UniqueConstraint(columnNames = "sku"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    private String sku;
    private String name;
    private String category;
    private String barcode;

    /** 存储温区要求：常温/冷藏/冷冻 */
    @Enumerated(EnumType.STRING)
    private TempZone tempZone;

    /** 建议保质期天数（创建批次时参考） */
    private Integer shelfLifeDays;

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    /** 供货价（成本） */
    @Column(precision = 12, scale = 2)
    private BigDecimal costPrice;

    /** 建议零售价 */
    @Column(precision = 12, scale = 2)
    private BigDecimal retailPrice;

    /** 单件重量(g)，用于关门称重交叉校验 */
    private Integer unitWeightGram;

    @Builder.Default
    private boolean active = true;
}
