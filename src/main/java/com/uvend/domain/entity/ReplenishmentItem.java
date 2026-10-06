package com.uvend.domain.entity;

import com.uvend.domain.enums.ItemState;
import com.uvend.domain.enums.TempClass;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "replenishment_item")
@Getter @Setter @NoArgsConstructor
public class ReplenishmentItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "replenishment_id", nullable = false)
    private Long replenishmentId;
    @Column(name = "batch_id", nullable = false)
    private Long batchId;
    @Column(name = "slot_id", nullable = false)
    private Long slotId;
    @Column(name = "actual_slot_id")
    private Long actualSlotId;
    @Column(nullable = false)
    private Integer qty;
    @Enumerated(EnumType.STRING)
    @Column(name = "temp_requirement", nullable = false, length = 16)
    private TempClass tempRequirement;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemState state = ItemState.PLACED;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
