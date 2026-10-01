package com.createbrewery.block;

import net.minecraft.util.StringRepresentable;

public enum GlassType implements StringRepresentable {
    BEER_MUG("beer_mug", 1.0f),
    SHOT_GLASS("shot_glass", 1.2f),
    COCKTAIL_GLASS("cocktail_glass", 1.0f);

    private final String name;
    private final float alcoholMultiplier;

    GlassType(String name, float alcoholMultiplier) {
        this.name = name;
        this.alcoholMultiplier = alcoholMultiplier;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public float getAlcoholMultiplier() {
        return alcoholMultiplier;
    }
}
