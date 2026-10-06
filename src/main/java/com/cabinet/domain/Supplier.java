package com.cabinet.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "supplier",
        uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier extends BaseEntity {

    private String code;
    private String name;
    private String contactPerson;
    private String contactPhone;

    /** 结算账期（天），仅展示用 */
    @Builder.Default
    private Integer paymentTermDays = 30;

    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal balancePayable = BigDecimal.ZERO;
}
