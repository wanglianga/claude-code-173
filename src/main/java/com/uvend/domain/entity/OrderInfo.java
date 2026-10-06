package com.uvend.domain.entity;

import com.uvend.domain.enums.EvidenceLevel;
import com.uvend.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_info")
@Getter @Setter @NoArgsConstructor
public class OrderInfo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_no", nullable = false, unique = true, length = 48)
    private String orderNo;
    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceLevel evidence = EvidenceLevel.SUFFICIENT;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "settled_at")
    private Instant settledAt;
}
