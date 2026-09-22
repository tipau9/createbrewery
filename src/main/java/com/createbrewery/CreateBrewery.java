package com.createbrewery;

import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(CreateBrewery.MOD_ID)
public class CreateBrewery {
    public static final String MOD_ID = "createbrewery";
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    public CreateBrewery(IEventBus modEventBus) {
        REGISTRATE.registerEventListeners(modEventBus);
        ModFluids.register();
        ModItems.register();
        ModBlocks.register();
        ModRecipeTypes.register(modEventBus);
    }

    public static ResourceLocation ID(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
