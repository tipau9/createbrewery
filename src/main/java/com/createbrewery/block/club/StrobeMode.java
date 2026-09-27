package com.createbrewery.block.club;

import net.minecraft.util.StringRepresentable;

public enum StrobeMode implements StringRepresentable {
    BEAT("beat"),
    STROBE("strobe"),
    REDSTONE("redstone");

    private final String name;

    StrobeMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public StrobeMode next() {
        StrobeMode[] vals = values();
        return vals[(ordinal() + 1) % vals.length];
    }
}
