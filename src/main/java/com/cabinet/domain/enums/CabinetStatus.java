package com.cabinet.domain.enums;

/** 柜机运行状态 */
public enum CabinetStatus {
    NORMAL,             // 正常
    ALARM_LOCKED,       // 温控报警锁定（食品安全，禁止开门/售卖）
    OFFLINE             // 离线
}
