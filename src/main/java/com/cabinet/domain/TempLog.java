package com.cabinet.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** 柜机温度采集记录（冷藏/冷冻柜温控） */
@Entity
@Table(name = "temp_log",
        indexes = @Index(columnList = "cabinet_id,recorded_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TempLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Cabinet cabinet;

    private Double tempC;

    /** 本次上报是否越限 */
    private boolean alarm;

    private LocalDateTime recordedAt;
}
