package com.cabinet.domain;

import com.cabinet.domain.enums.RemovalReason;
import com.cabinet.domain.enums.RemovalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** 下架单：临期/过期/温控异常/卡货损坏/放错货道纠正等 */
@Entity
@Table(name = "removal_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemovalOrder extends BaseEntity {

    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Cabinet cabinet;

    /** 触发来源：SYSTEM(临期/温控定时扫描) / OPERATOR */
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    private RemovalReason reason;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RemovalStatus status = RemovalStatus.PENDING;

    private LocalDateTime scheduledAt;
    private LocalDateTime executedAt;

    /** 执行的补货员/运营 */
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser executor;

    private String remark;
}
