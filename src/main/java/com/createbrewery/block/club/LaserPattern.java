package com.createbrewery.block.club;

import net.minecraft.util.StringRepresentable;

public enum LaserPattern implements StringRepresentable {
    BEAM("beam"),
    SWEEP("sweep"),
    FAN("fan"),
    BURST("burst");

    private final String name;

    LaserPattern(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public LaserPattern next() {
        LaserPattern[] vals = values();
        return vals[(ordinal() + 1) % vals.length];
    }
}
