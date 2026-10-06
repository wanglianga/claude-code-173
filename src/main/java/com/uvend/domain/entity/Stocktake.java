package com.uvend.domain.entity;

import com.uvend.domain.enums.StocktakeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "stocktake")
@Getter @Setter @NoArgsConstructor
public class Stocktake {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "stocktake_no", nullable = false, unique = true, length = 48)
    private String stocktakeNo;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StocktakeStatus status = StocktakeStatus.OPEN;
    private String note;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "closed_at")
    private Instant closedAt;
}
