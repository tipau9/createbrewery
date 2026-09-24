package com.createbrewery;

import com.createbrewery.data.ModDataMaps;
import com.createbrewery.data.ModLootModifiers;
import com.createbrewery.data.ModRecipeProvider;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CreateBrewery.MOD_ID)
public class CreateBrewery {
    public static final String MOD_ID = "createbrewery";
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    public CreateBrewery(IEventBus modEventBus, ModContainer modContainer) {
        REGISTRATE.registerEventListeners(modEventBus);
        ModFluids.register();
        ModItems.register();
        ModBlocks.register();
        ModBlockEntities.register();
        // Hand-written strings with no registry object of their own to hang a .lang() call
        // off of. addRawLang feeds the same RegistrateLangProvider as every other entry, so
        // this stays in the one generated en_us.json rather than a hand file that would
        // collide with it (that file is 100% datagen output already - see ModFluids, item(),
        // block()).
        REGISTRATE.addRawLang("createbrewery.goggles.fermenter.idle", "Idle");
        REGISTRATE.addRawLang("createbrewery.goggles.fermenter.progress", "Fermenting: %s%%");
        REGISTRATE.addRawLang("createbrewery.goggles.fermenter.remaining", "%s days remaining");
        ModRecipeTypes.register(modEventBus);
        modEventBus.addListener(ModRecipeProvider::gatherData);
        ModLootModifiers.register(modEventBus);
        ModDataMaps.register(modEventBus);
        ModVillagerTrades.register();
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation ID(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
