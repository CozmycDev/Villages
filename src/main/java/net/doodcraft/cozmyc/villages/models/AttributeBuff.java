package net.doodcraft.cozmyc.villages.models;

public class AttributeBuff {
    public enum ScalingType { ADDITIVE, MULTIPLICATIVE, EXPONENTIAL, NONE }
    private final ScalingType type;
    private final double value;

    public AttributeBuff(ScalingType type, double value) {
        this.type = type;
        this.value = value;
    }

    public ScalingType getType() { return type; }
    public double getValue() { return value; }
} 