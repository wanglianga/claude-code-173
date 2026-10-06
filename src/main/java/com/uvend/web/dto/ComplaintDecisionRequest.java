package com.uvend.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/** 客服处理：REFUND（可全额/部分金额或按订单项退）或 REJECT。 */
public record ComplaintDecisionRequest(
        @NotBlank String decision,
        BigDecimal refundAmount,
        List<Long> refundItemIds,
        String resolution) {
}
