package com.uvend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"slot_id", "batch_id"}))
@Getter @Setter @NoArgsConstructor
public class Inventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "slot_id", nullable = false)
    private Long slotId;
    @Column(name = "batch_id", nullable = false)
    private Long batchId;
    @Column(nullable = false)
    private Integer qty = 0;
    @Version
    private Long version;
}
