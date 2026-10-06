package com.uvend.domain.entity;

import com.uvend.domain.enums.DetectMethod;
import com.uvend.domain.enums.Verdict;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recognition_result")
@Getter @Setter @NoArgsConstructor
public class RecognitionResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DetectMethod method;
    @Column(name = "product_id")
    private Long productId;
    @Column(name = "batch_id")
    private Long batchId;
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Verdict verdict;
    @Column(nullable = false, length = 4000)
    private String detail = "{}";
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
