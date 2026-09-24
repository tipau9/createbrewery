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
        REGISTRATE.addRawLang("createbrewery.recipe.fermenting", "Fermenting");
        REGISTRATE.addRawLang("createbrewery.jei.fermenting.one_day", "1 day");
        REGISTRATE.addRawLang("createbrewery.jei.fermenting.days", "%s days");
        REGISTRATE.addRawLang("createbrewery.jei.fermenting.days_decimal", "%s days");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.header", "Fermenting Beer in the Fermenter");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_1", "The Fermenter turns Wort and Yeast into Beer over time.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_2", "Unlike most Create machines, the Fermenter requires NO shaft or rotational force. It is completely passive.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_3", "Wort can be piped into the Fermenter from any side.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_4", "Yeast can be inserted with a Funnel or by hand.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_5", "Engineer's Goggles display fermentation progress.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_6", "Fermenting: 33% (2 days remaining)");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_7", "Fermenting: 67% (1 day remaining)");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_8", "Fermenting: 100% (Ready)");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_9", "Finished Beer can then be piped out and bottled.");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.header", "Brewing Heat Requirements");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.text_1", "Brewing requires different heat tiers for different stages of the process.");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.text_2", "Mashing (Barley + Water -> Sweet Wort) requires standard HEAT (Kindled Blaze Burner).");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.text_3", "Boiling (Sweet Wort + Hops -> Wort) requires SUPERHEATED heat (fed with Blaze Cake).");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.text_4", "Remember: Mashing needs standard heat, while Boiling hops must be Superheated!");
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(CreateBreweryClient::onClientSetup);
        }
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
