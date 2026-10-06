package com.cabinet.domain.enums;

public enum RestockStatus {
    IN_PROGRESS,
    COMPLETED,
    COMPLETED_MISMATCH,   // 存在放错货道等告警但已完成
    CANCELLED
}
