package com.uvend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "supplier")
@Getter @Setter @NoArgsConstructor
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    private String contact;
    @Column(name = "bear_loss")
    private Boolean bearLoss = true;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
