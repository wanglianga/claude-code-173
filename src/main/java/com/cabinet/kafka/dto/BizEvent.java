package com.cabinet.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 服务对外发布的业务事件（结算、退款、告警、下架、报损、结算） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizEvent {

    /** ORDER_CHARGED / ORDER_PENDING_REVIEW / ORDER_REFUNDED / REMOVAL_CREATED /
     *  LOSS_RECORDED / ALERT / SETTLEMENT_CONFIRMED / TEMP_LOCK */
    private String type;
    private String refNo;
    private String cabinetNo;
    private String batchNo;
    private String message;
    private BigDecimal amount;
    private LocalDateTime occurredAt;
}
