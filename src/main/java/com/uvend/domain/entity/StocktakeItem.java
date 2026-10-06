package com.uvend.domain.entity;

import com.uvend.domain.enums.StocktakeItemState;
import com.uvend.domain.enums.StocktakeResolution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stocktake_item")
@Getter @Setter @NoArgsConstructor
public class StocktakeItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "stocktake_id", nullable = false)
    private Long stocktakeId;
    @Column(name = "slot_id", nullable = false)
    private Long slotId;
    @Column(name = "batch_id")
    private Long batchId;
    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;
    @Column(name = "actual_qty")
    private Integer actualQty;
    @Column(name = "diff_qty")
    private Integer diffQty;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StocktakeItemState state = StocktakeItemState.PENDING;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StocktakeResolution resolution;
}
