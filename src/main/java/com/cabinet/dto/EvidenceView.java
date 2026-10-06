package com.cabinet.dto;

import com.cabinet.domain.*;

import java.util.List;

/**
 * 售后证据回放视图：客服在一个页面拿到
 * 开门记录 + 识别结果（含视频片段）+ 订单明细 + 批次台账（库存变化）+ 温控记录。
 */
public record EvidenceView(
        Complaint complaint,
        DoorSession session,
        OrderRecord order,
        List<OrderItem> orderItems,
        List<RecognitionEvent> recognitionEvents,
        List<BatchLedger> batchLedgers,
        List<TempLog> tempLogs) {
}
