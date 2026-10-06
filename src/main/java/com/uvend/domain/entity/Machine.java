package com.uvend.domain.entity;

import com.uvend.domain.enums.LocationType;
import com.uvend.domain.enums.TempClass;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "machine")
@Getter @Setter @NoArgsConstructor
public class Machine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 16)
    private LocationType locationType;
    private String address;
    @Enumerated(EnumType.STRING)
    @Column(name = "machine_type", nullable = false, length = 16)
    private TempClass machineType;
    @Column(name = "temp_min", precision = 5, scale = 2)
    private BigDecimal tempMin;
    @Column(name = "temp_max", precision = 5, scale = 2)
    private BigDecimal tempMax;
    private Boolean locked = false;
    @Column(length = 16)
    private String status = "ONLINE";
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
