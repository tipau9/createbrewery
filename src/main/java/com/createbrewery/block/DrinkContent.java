package com.createbrewery.block;

import com.createbrewery.ModFluids;
import com.createbrewery.ModItems;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

public enum DrinkContent implements StringRepresentable {
    EMPTY("empty", "createbrewery.drink_content.empty", 0.0f, false, 0x000000),
    BEER("beer", "createbrewery.drink_content.beer", 0.20f, true, 0xE5A623),
    WINE("wine", "createbrewery.drink_content.wine", 0.25f, false, 0x7E1822),
    COGNAC("cognac", "createbrewery.drink_content.cognac", 0.35f, false, 0x9E4712),
    WHISKEY("whiskey", "createbrewery.drink_content.whiskey", 0.35f, false, 0xB8651B),
    DOPPELKORN("doppelkorn", "createbrewery.drink_content.doppelkorn", 0.35f, false, 0xE8ECEF),
    VODKA("vodka", "createbrewery.drink_content.vodka", 0.35f, false, 0xEDF2F4),
    GIN("gin", "createbrewery.drink_content.gin", 0.35f, false, 0xD4EFF7),
    RUM("rum", "createbrewery.drink_content.rum", 0.35f, false, 0x5C240E),
    TEQUILA("tequila", "createbrewery.drink_content.tequila", 0.35f, false, 0xD4B04C),
    COCKTAIL("cocktail", "createbrewery.drink_content.cocktail", 0.30f, false, 0xE91E63);

    private final String name;
    private final String translationKey;
    private final float perMille;
    private final boolean isBeer;
    private final int color;

    DrinkContent(String name, String translationKey, float perMille, boolean isBeer, int color) {
        this.name = name;
        this.translationKey = translationKey;
        this.perMille = perMille;
        this.isBeer = isBeer;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public float getPerMille() {
        return perMille;
    }

    public boolean isBeer() {
        return isBeer;
    }

    public int getColor() {
        return color;
    }

    public static DrinkContent fromItem(ItemStack stack) {
        if (stack.isEmpty()) return EMPTY;
        if (stack.is(ModItems.BEER_BOTTLE.get()) || stack.is(ModItems.SEALED_CAN.get())) return BEER;
        if (stack.is(ModItems.WINE_BOTTLE.get())) return WINE;
        if (stack.is(ModItems.COGNAC_BOTTLE.get())) return COGNAC;
        if (stack.is(ModItems.WHISKEY_BOTTLE.get())) return WHISKEY;
        if (stack.is(ModItems.DOPPELKORN_BOTTLE.get())) return DOPPELKORN;
        if (stack.is(ModItems.VODKA_BOTTLE.get())) return VODKA;
        if (stack.is(ModItems.GIN_BOTTLE.get())) return GIN;
        if (stack.is(ModItems.RUM_BOTTLE.get())) return RUM;
        if (stack.is(ModItems.TEQUILA_BOTTLE.get())) return TEQUILA;
        return EMPTY;
    }

    public static DrinkContent fromFluid(Fluid fluid) {
        if (fluid == null) return EMPTY;
        if (fluid.isSame(ModFluids.BEER.getSource())) return BEER;
        if (fluid.isSame(ModFluids.WINE.getSource())) return WINE;
        if (fluid.isSame(ModFluids.COGNAC.getSource())) return COGNAC;
        if (fluid.isSame(ModFluids.WHISKEY.getSource())) return WHISKEY;
        if (fluid.isSame(ModFluids.DOPPELKORN.getSource())) return DOPPELKORN;
        if (fluid.isSame(ModFluids.VODKA.getSource())) return VODKA;
        if (fluid.isSame(ModFluids.GIN.getSource())) return GIN;
        if (fluid.isSame(ModFluids.RUM.getSource())) return RUM;
        if (fluid.isSame(ModFluids.TEQUILA.getSource())) return TEQUILA;
        return EMPTY;
    }
}
