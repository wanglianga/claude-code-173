package com.uvend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refund")
@Getter @Setter @NoArgsConstructor
public class Refund {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "refund_no", nullable = false, unique = true, length = 48)
    private String refundNo;
    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    private String reason;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
