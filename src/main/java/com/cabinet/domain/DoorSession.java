package com.cabinet.domain;

import com.cabinet.domain.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 开门会话：一次用户开门购物的全流程证据容器。
 * 开门记录、识别结果、视频片段、库存变化、订单均挂在会话上，供售后回放。
 */
@Entity
@Table(name = "door_session",
        indexes = @Index(columnList = "cabinet_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoorSession extends BaseEntity {

    private String sessionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Cabinet cabinet;

    /** 用户标识（手机号/会员号） */
    private String userCode;
    private String userName;

    private LocalDateTime openedAt;
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SessionStatus status = SessionStatus.OPEN;

    /** 识别置信度（融合后最低置信度，<阈值则挂起人工复核） */
    private Double minConfidence;

    /** 称重净变化(g)，用于与识别结果交叉校验 */
    private Integer weightDeltaGram;

    /** 结算说明/复核结论 */
    @Column(length = 1000)
    private String settleNote;
}
