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

    public static void register() {}
}
