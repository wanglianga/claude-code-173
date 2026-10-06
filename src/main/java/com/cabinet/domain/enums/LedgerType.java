package com.cabinet.domain.enums;

/** 批次台账流水类型：补货入/售卖出/下架出/报损出/盘盈盘亏/退款回库/结算 */
public enum LedgerType {
    RESTOCK_IN,
    SALE_OUT,
    REMOVAL_OUT,
    LOSS_OUT,
    ADJUST_PLUS,
    ADJUST_MINUS,
    REFUND_RETURN,
    SETTLE
}
