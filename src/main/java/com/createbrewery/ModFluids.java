package com.createbrewery;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.FluidEntry;
import com.simibubi.create.content.fluids.VirtualFluid;

public class ModFluids {
    private static final CreateRegistrate REGISTRATE = CreateBrewery.REGISTRATE;

    public static final FluidEntry<VirtualFluid> WORT =
        REGISTRATE.virtualFluid("wort").lang("Wort").register();

    public static final FluidEntry<VirtualFluid> HOPPED_WORT =
        REGISTRATE.virtualFluid("hopped_wort").lang("Hopped Wort").register();

    public static final FluidEntry<VirtualFluid> BEER =
        REGISTRATE.virtualFluid("beer").lang("Beer").register();

    /** Lachgas: filled into balloons by a Spout. */
    public static final FluidEntry<VirtualFluid> LACHGAS =
        REGISTRATE.virtualFluid("lachgas").lang("Nitrous Oxide").register();

    // Wine and spirits: mashes, washes, first and second distillates, and what goes into the bottle.
    public static final FluidEntry<VirtualFluid> GRAPE_MUST =
        REGISTRATE.virtualFluid("grape_must").lang("Grape Must").register();
    public static final FluidEntry<VirtualFluid> WINE =
        REGISTRATE.virtualFluid("wine").lang("Wine").register();
    public static final FluidEntry<VirtualFluid> BROUILLIS =
        REGISTRATE.virtualFluid("brouillis").lang("Brouillis").register();
    public static final FluidEntry<VirtualFluid> EAU_DE_VIE =
        REGISTRATE.virtualFluid("eau_de_vie").lang("Eau de Vie").register();
    public static final FluidEntry<VirtualFluid> COGNAC =
        REGISTRATE.virtualFluid("cognac").lang("Cognac").register();
    public static final FluidEntry<VirtualFluid> WASH =
        REGISTRATE.virtualFluid("wash").lang("Whiskey Wash").register();
    public static final FluidEntry<VirtualFluid> LOW_WINES =
        REGISTRATE.virtualFluid("low_wines").lang("Low Wines").register();
    public static final FluidEntry<VirtualFluid> NEW_MAKE =
        REGISTRATE.virtualFluid("new_make").lang("New Make Spirit").register();
    public static final FluidEntry<VirtualFluid> WHISKEY =
        REGISTRATE.virtualFluid("whiskey").lang("Whiskey").register();
    public static final FluidEntry<VirtualFluid> GRAIN_MASH =
        REGISTRATE.virtualFluid("grain_mash").lang("Grain Mash").register();
    public static final FluidEntry<VirtualFluid> GRAIN_WASH =
        REGISTRATE.virtualFluid("grain_wash").lang("Grain Wash").register();
    public static final FluidEntry<VirtualFluid> RAW_KORN =
        REGISTRATE.virtualFluid("raw_korn").lang("Raw Grain Spirit").register();
    public static final FluidEntry<VirtualFluid> DOPPELKORN =
        REGISTRATE.virtualFluid("doppelkorn").lang("Doppelkorn").register();
    public static final FluidEntry<VirtualFluid> POTATO_MASH =
        REGISTRATE.virtualFluid("potato_mash").lang("Potato Mash").register();
    public static final FluidEntry<VirtualFluid> POTATO_WASH =
        REGISTRATE.virtualFluid("potato_wash").lang("Potato Wash").register();
    public static final FluidEntry<VirtualFluid> NEUTRAL_SPIRIT =
        REGISTRATE.virtualFluid("neutral_spirit").lang("Neutral Spirit").register();
    public static final FluidEntry<VirtualFluid> VODKA =
        REGISTRATE.virtualFluid("vodka").lang("Vodka").register();
    public static final FluidEntry<VirtualFluid> GIN =
        REGISTRATE.virtualFluid("gin").lang("Gin").register();
    public static final FluidEntry<VirtualFluid> MOLASSES =
        REGISTRATE.virtualFluid("molasses").lang("Molasses").register();
    public static final FluidEntry<VirtualFluid> RUM_WASH =
        REGISTRATE.virtualFluid("rum_wash").lang("Rum Wash").register();
    public static final FluidEntry<VirtualFluid> RAW_RUM =
        REGISTRATE.virtualFluid("raw_rum").lang("Raw Rum").register();
    public static final FluidEntry<VirtualFluid> RUM =
        REGISTRATE.virtualFluid("rum").lang("Rum").register();
    public static final FluidEntry<VirtualFluid> AGAVE_JUICE =
        REGISTRATE.virtualFluid("agave_juice").lang("Agave Juice").register();
    public static final FluidEntry<VirtualFluid> AGAVE_WASH =
        REGISTRATE.virtualFluid("agave_wash").lang("Agave Wash").register();
    public static final FluidEntry<VirtualFluid> ORDINARIO =
        REGISTRATE.virtualFluid("ordinario").lang("Ordinario").register();
    public static final FluidEntry<VirtualFluid> TEQUILA =
        REGISTRATE.virtualFluid("tequila").lang("Tequila").register();

    public static void register() {}
}
