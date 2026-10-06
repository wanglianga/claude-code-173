package com.cabinet.domain.enums;

/** 温度要求/柜机温区 */
public enum TempZone {
    AMBIENT("常温", 0, 35),
    CHILLED("冷藏", 0, 8),
    FROZEN("冷冻", -22, -15);

    private final String label;
    private final double defaultMin;
    private final double defaultMax;

    TempZone(String label, double defaultMin, double defaultMax) {
        this.label = label;
        this.defaultMin = defaultMin;
        this.defaultMax = defaultMax;
    }

    public String getLabel() { return label; }
    public double getDefaultMin() { return defaultMin; }
    public double getDefaultMax() { return defaultMax; }
}
