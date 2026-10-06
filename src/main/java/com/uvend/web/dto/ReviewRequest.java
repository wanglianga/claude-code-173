package com.uvend.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/** 人工复核结论：CONFIRM=确认购买（可按批次修正数量）；REJECT=用户未拿，回滚库存且不扣款。 */
public record ReviewRequest(
        @NotBlank String decision,
        String note,
        List<ItemAdjust> items) {
    public record ItemAdjust(String batchNo, Integer qty) {}
}
