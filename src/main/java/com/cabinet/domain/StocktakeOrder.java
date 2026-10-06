package com.cabinet.domain;

import com.cabinet.domain.enums.StocktakeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** 盘点单 */
@Entity
@Table(name = "stocktake_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeOrder extends BaseEntity {

    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Cabinet cabinet;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser operator;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StocktakeStatus status = StocktakeStatus.DRAFT;

    /** 盘点总差异数 */
    @Builder.Default
    private Integer totalDiff = 0;

    private String remark;
}
