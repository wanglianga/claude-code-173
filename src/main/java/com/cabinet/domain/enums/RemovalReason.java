package com.cabinet.domain.enums;

/** 下架原因 */
public enum RemovalReason {
    NEAR_EXPIRY,    // 临期下架
    EXPIRED,        // 过期（未及时下架）
    TEMP_ABUSE,     // 温控异常食品安全下架
    DAMAGED,        // 破损/卡货损坏
    RECALLED,       // 召回
    WRONG_CHANNEL   // 放错货道纠正下架
}
