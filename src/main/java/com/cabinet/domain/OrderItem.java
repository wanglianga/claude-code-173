package com.cabinet.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** 订单明细：精确到批次，支撑批次售卖追溯与结算 */
@Entity
@Table(name = "order_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private OrderRecord order;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    private ProductBatch batch;

    private String channelCode;

    private Integer qty;

    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;

    /** 本行是否已退款 */
    @Builder.Default
    private Integer refundedQty = 0;
}
