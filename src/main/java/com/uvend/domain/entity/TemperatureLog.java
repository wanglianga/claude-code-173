package com.uvend.domain.entity;

import com.uvend.domain.enums.TempStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "temperature_log")
@Getter @Setter @NoArgsConstructor
public class TemperatureLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "temp_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal tempC;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TempStatus status;
    @Column(name = "occurred_at")
    private Instant occurredAt = Instant.now();
}
