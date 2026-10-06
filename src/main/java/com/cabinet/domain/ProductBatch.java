package com.cabinet.domain;

import com.cabinet.domain.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 商品批次：保质期与供应商结算、损耗追溯的最小单元 */
@Entity
@Table(name = "product_batch",
        uniqueConstraints = @UniqueConstraint(columnNames = "batchNo"),
        indexes = @Index(columnList = "expiryDate"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBatch extends BaseEntity {

    /** 批次号 */
    private String batchNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    private LocalDate productionDate;
    /** 保质期到期日（到期日当天 00:00 视为过期） */
    private LocalDate expiryDate;

    /** 本批次收货总数（入站数量） */
    private Integer receivedQty;

    /** 批次成本单价（结算依据，快照） */
    @Column(precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BatchStatus status = BatchStatus.IN_STOCK;
}
