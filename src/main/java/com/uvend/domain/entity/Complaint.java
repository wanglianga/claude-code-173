package com.uvend.domain.entity;

import com.uvend.domain.enums.ComplaintStatus;
import com.uvend.domain.enums.ComplaintType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "complaint")
@Getter @Setter @NoArgsConstructor
public class Complaint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "complaint_no", nullable = false, unique = true, length = 48)
    private String complaintNo;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplaintType type;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ComplaintStatus status = ComplaintStatus.OPEN;
    @Column(name = "handler_id")
    private Long handlerId;
    private String resolution;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
