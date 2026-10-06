package com.cabinet.domain;

import com.cabinet.domain.enums.CabinetStatus;
import com.cabinet.domain.enums.TempZone;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** 无人货柜 */
@Entity
@Table(name = "cabinet",
        uniqueConstraints = @UniqueConstraint(columnNames = "cabinetNo"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cabinet extends BaseEntity {

    /** 柜机编号 */
    private String cabinetNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Point point;

    private String model;

    /** 柜机温区 */
    @Enumerated(EnumType.STRING)
    private TempZone tempZone;

    /** 允许温度下限 */
    private Double tempMin;
    /** 允许温度上限 */
    private Double tempMax;

    /** 最近一次上报温度 */
    private Double lastTempC;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CabinetStatus status = CabinetStatus.NORMAL;

    private LocalDateTime lastHeartbeatAt;

    /** 温控异常开始时间（用于计算失控时长） */
    private LocalDateTime tempAlarmSince;
}
