package com.cabinet.domain;

import com.cabinet.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户关门结算订单 */
@Entity
@Table(name = "order_record",
        indexes = @Index(columnList = "session_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRecord extends BaseEntity {

    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private DoorSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    private Cabinet cabinet;

    private String userCode;

    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus status = OrderStatus.CHARGED;

    private LocalDateTime chargedAt;

    /** 退款金额累计 */
    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    /** 支付流水号 */
    private String paymentTxnNo;

    @Column(length = 1000)
    private String note;
}
