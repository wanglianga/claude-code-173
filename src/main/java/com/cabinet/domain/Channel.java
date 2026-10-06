package com.cabinet.domain;

import jakarta.persistence.*;
import lombok.*;

/** 货道：属于某柜机，计划售卖指定商品（点位品类配置） */
@Entity
@Table(name = "channel",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Channel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id")
    private Cabinet cabinet;

    /** 货道位置，如 A01 */
    private String code;

    /** 货道计划商品；补货放错货道即与该值不一致 */
    @ManyToOne(fetch = FetchType.LAZY)
    private Product plannedProduct;

    private Integer capacity;
}
