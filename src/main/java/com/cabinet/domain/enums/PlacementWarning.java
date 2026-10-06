package com.cabinet.domain.enums;

/** 补货上架校验告警 */
public enum PlacementWarning {
    NONE,
    WRONG_CHANNEL,      // 放错货道（与货道计划商品不符）
    BATCH_EXPIRED,      // 上架批次已过期/临期不允许
    TEMP_MISMATCH,      // 温度要求与柜机温区不符
    UNKNOWN_CHANNEL     // 货道不存在
}
