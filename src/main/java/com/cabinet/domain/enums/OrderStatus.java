package com.cabinet.domain.enums;

public enum OrderStatus {
    CHARGED,              // 已扣款
    PENDING_REVIEW,       // 识别存疑挂起（未扣款）
    REFUNDED,             // 全额退款
    PARTIALLY_REFUNDED,   // 部分退款
    CANCELLED             // 取消（无商品净拿取等）
}
