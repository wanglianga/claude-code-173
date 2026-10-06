package com.uvend.event;

/** Kafka 主题常量（本地事件总线模式下仅做逻辑分类）。 */
public final class Topics {
    private Topics() {}
    public static final String RESTOCK = "uvend.restock";
    public static final String SHOPPING = "uvend.shopping";
    public static final String ORDER = "uvend.order";
    public static final String INVENTORY = "uvend.inventory";
    public static final String TEMPERATURE = "uvend.temperature";
    public static final String EXPIRY = "uvend.expiry";
    public static final String STOCKTAKE = "uvend.stocktake";
    public static final String COMPLAINT = "uvend.complaint";
    public static final String SETTLEMENT = "uvend.settlement";
}
