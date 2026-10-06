package com.uvend.domain.entity;

import com.uvend.domain.enums.TempClass;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String sku;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String category;
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;
    @Column(name = "shelf_life_days", nullable = false)
    private Integer shelfLifeDays;
    @Enumerated(EnumType.STRING)
    @Column(name = "temp_requirement", nullable = false, length = 16)
    private TempClass tempRequirement = TempClass.AMBIENT;
    private Boolean active = true;
}
