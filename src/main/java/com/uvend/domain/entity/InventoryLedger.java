package com.uvend.domain.entity;

import com.uvend.domain.enums.LedgerReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "inventory_ledger")
@Getter @Setter @NoArgsConstructor
public class InventoryLedger {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "slot_id", nullable = false)
    private Long slotId;
    @Column(name = "batch_id", nullable = false)
    private Long batchId;
    @Column(name = "change_qty", nullable = false)
    private Integer changeQty;
    @Column(name = "balance_qty", nullable = false)
    private Integer balanceQty;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LedgerReason reason;
    @Column(name = "ref_type", length = 24)
    private String refType;
    @Column(name = "ref_id")
    private Long refId;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
