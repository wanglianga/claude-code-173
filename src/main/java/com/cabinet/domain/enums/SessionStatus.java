package com.cabinet.domain.enums;

public enum SessionStatus {
    OPEN,             // 开门进行中
    SETTLED,          // 关门结算完成（已扣款）
    REVIEW_PENDING,   // 识别存疑，挂起待人工复核（未扣款）
    CANCELLED,        // 取消（如温控锁定拒绝开门、未拿取商品）
    SETTLED_REFUNDED  // 已结算后投诉退款
}
