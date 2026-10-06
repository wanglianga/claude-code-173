package com.cabinet.domain;

import com.cabinet.domain.enums.RestockStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** 补货单：补货员到柜补货 */
@Entity
@Table(name = "restock_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestockOrder extends BaseEntity {

    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Cabinet cabinet;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser replenisher;

    private LocalDateTime arrivedAt;
    private LocalDateTime completedAt;

    /** 补货照片 URL（可多张，逗号分隔） */
    @Column(length = 1000)
    private String photoUrls;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RestockStatus status = RestockStatus.IN_PROGRESS;

    /** 备注/异常说明 */
    private String remark;
}
