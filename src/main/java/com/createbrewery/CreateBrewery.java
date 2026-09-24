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
        com.createbrewery.effect.ModEffects.register(modEventBus);
        com.createbrewery.drunk.ModAttachments.register(modEventBus);
        com.createbrewery.particle.ModParticles.register(modEventBus);
        com.createbrewery.sound.ModSounds.register(modEventBus);
        REGISTRATE.addRawLang("effect.createbrewery.inebriation", "Trunkenheit");
        REGISTRATE.addRawLang("effect.createbrewery.hangover", "Kater des Todes");
        REGISTRATE.addRawLang("effect.createbrewery.hiccups", "Schluckauf");
        REGISTRATE.addRawLang("effect.createbrewery.stumble", "Schlingerkurs");
        REGISTRATE.addRawLang("effect.createbrewery.delirium", "Größenwahn");
        REGISTRATE.addRawLang("effect.createbrewery.blackout", "Filmriss");
        REGISTRATE.addRawLang("effect.createbrewery.alcohol_poisoning", "Alkoholvergiftung");
        REGISTRATE.addRawLang("effect.createbrewery.good_mood", "Bierlaune");
        REGISTRATE.addRawLang("effect.createbrewery.cheers", "Geselligkeit");
        REGISTRATE.addRawLang("effect.createbrewery.vomiting", "Kotzanfall");
        REGISTRATE.addRawLang("subtitles.createbrewery.glass_clink", "Gläser klirren");
        REGISTRATE.addRawLang("subtitles.createbrewery.hiccup", "Hicks");
        REGISTRATE.addRawLang("subtitles.createbrewery.heartbeat", "Herzklopfen");
        REGISTRATE.addRawLang("subtitles.createbrewery.beer_open", "Bier zischt");
        REGISTRATE.addRawLang("subtitles.createbrewery.ear_ringing", "Ohrenpfeifen");
        REGISTRATE.addRawLang("death.attack.createbrewery.alcohol_poisoning", "%1$s hat sich zu Tode gesoffen");
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
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_1", "The Fermenter turns Hopped Wort and Yeast into Beer over time.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_2", "Unlike most Create machines, the Fermenter requires NO shaft or rotational force. It is completely passive.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_3", "Hopped Wort can be piped into the Fermenter from any side.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_4", "Yeast can be inserted with a Funnel or by hand.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_5", "Engineer's Goggles display fermentation progress.");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_6", "Fermenting: 20% (0.8 days remaining)");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_7", "Fermenting: 60% (0.4 days remaining)");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_8", "Fermenting: 100% (Ready)");
        REGISTRATE.addRawLang("createbrewery.ponder.fermenter.text_9", "Finished Beer can then be piped out and bottled.");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.header", "Brewing Heat Requirements");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.text_1", "Brewing requires different heat tiers for different stages of the process.");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.text_2", "Mashing (Grist + Water -> Wort) requires standard HEAT (Kindled Blaze Burner).");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.text_3", "Boiling (Wort + Hops -> Hopped Wort) requires SUPERHEATED heat (fed with Blaze Cake).");
        REGISTRATE.addRawLang("createbrewery.ponder.brewing_heat.text_4", "Remember: Mashing needs standard heat, while Boiling hops must be Superheated!");
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(CreateBreweryClient::onClientSetup);
            modEventBus.addListener(com.createbrewery.drunk.DrunkClient::onRegisterClientExtensions);
            modEventBus.addListener(com.createbrewery.particle.BreweryParticle::register);
        }
        ModRecipeTypes.register(modEventBus);
        modEventBus.addListener(ModRecipeProvider::gatherData);
        ModLootModifiers.register(modEventBus);
        ModDataMaps.register(modEventBus);
        ModVillagerTrades.register();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.createbrewery.event.BreweryCommonEvents.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.createbrewery.drunk.DrunkServer.class);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    public static ResourceLocation ID(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
