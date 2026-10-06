package com.uvend.domain.entity;

import com.uvend.domain.enums.AlertResolution;
import com.uvend.domain.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "temperature_alert")
@Getter @Setter @NoArgsConstructor
public class TemperatureAlert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "alert_no", nullable = false, unique = true, length = 48)
    private String alertNo;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "min_temp", precision = 5, scale = 2)
    private BigDecimal minTemp;
    @Column(name = "max_temp", precision = 5, scale = 2)
    private BigDecimal maxTemp;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AlertStatus status = AlertStatus.OPEN;
    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private AlertResolution resolution;
    private String note;
}
