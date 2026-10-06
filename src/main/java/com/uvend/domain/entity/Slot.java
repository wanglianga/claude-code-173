package com.uvend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "slot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"machine_id", "code"}))
@Getter @Setter @NoArgsConstructor
public class Slot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(nullable = false, length = 16)
    private String code;
    @Column(name = "product_id")
    private Long productId;
    private Integer capacity = 10;
}
