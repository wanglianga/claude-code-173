package com.cabinet.domain;

import com.cabinet.domain.enums.AlertStatus;
import com.cabinet.domain.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** 运营告警：放错货道/卡货/温控/临期/盘点差异/识别存疑 */
@Entity
@Table(name = "alert",
        indexes = @Index(columnList = "status,alert_type"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Cabinet cabinet;

    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    /** INFO / WARN / CRITICAL */
    private String severity;

    @Column(length = 1000)
    private String message;

    private String bizType;
    private Long bizId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    private LocalDateTime resolvedAt;
    private String resolution;
}
