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
        REGISTRATE.addRawLang("effect.createbrewery.painkiller", "Schmerzmittel");
        REGISTRATE.addRawLang("effect.createbrewery.coke_high", "Koks-Rausch");
        REGISTRATE.addRawLang("effect.createbrewery.coke_crash", "Absturz");
        REGISTRATE.addRawLang("effect.createbrewery.keta_high", "Keta-Rausch");
        REGISTRATE.addRawLang("effect.createbrewery.k_hole", "K-Loch");
        REGISTRATE.addRawLang("effect.createbrewery.dazed", "Benommen");
        REGISTRATE.addRawLang("effect.createbrewery.weed_high", "Bekifft");
        REGISTRATE.addRawLang("effect.createbrewery.greening_out", "Kreislauf am Ende");
        REGISTRATE.addRawLang("effect.createbrewery.lsd_trip", "LSD-Trip");
        REGISTRATE.addRawLang("effect.createbrewery.shroom_trip", "Pilz-Trip");
        REGISTRATE.addRawLang("effect.createbrewery.mescaline_trip", "Meskalin-Trip");
        REGISTRATE.addRawLang("effect.createbrewery.psy_tolerance", "Toleranz");
        REGISTRATE.addRawLang("effect.createbrewery.bad_trip", "Horrortrip");
        REGISTRATE.addRawLang("effect.createbrewery.afterglow", "Nachglühen");
        REGISTRATE.addRawLang("effect.createbrewery.flashback", "Flashback");
        REGISTRATE.addRawLang("effect.createbrewery.flashback_pending", "Nachhall");
        REGISTRATE.addRawLang("effect.createbrewery.breakthrough", "Durchbruch");
        REGISTRATE.addRawLang("effect.createbrewery.rolling", "Ecstasy-Rausch");
        REGISTRATE.addRawLang("effect.createbrewery.comedown", "Tiefpunkt");
        REGISTRATE.addRawLang("effect.createbrewery.tweak", "Crystal-Rausch");
        REGISTRATE.addRawLang("effect.createbrewery.meth_crash", "Crystal-Absturz");
        REGISTRATE.addRawLang("effect.createbrewery.hyperthermia", "Hitzschlag");
        REGISTRATE.addRawLang("effect.createbrewery.psychosis", "Psychose");
        REGISTRATE.addRawLang("effect.createbrewery.nod", "Heroin-Rausch");
        REGISTRATE.addRawLang("effect.createbrewery.calm", "Sediert");
        REGISTRATE.addRawLang("effect.createbrewery.respiratory_depression", "Atemlähmung");
        REGISTRATE.addRawLang("effect.createbrewery.withdrawal", "Entzug");
        REGISTRATE.addRawLang("effect.createbrewery.wah", "Lachgas");
        REGISTRATE.addRawLang("effect.createbrewery.candyflip", "Candyflip");
        REGISTRATE.addRawLang("effect.createbrewery.speedball", "Speedball");
        REGISTRATE.addRawLang("effect.createbrewery.stoned_trip", "Nachgelegt");
        REGISTRATE.addRawLang("effect.createbrewery.nitrous_peak", "Gipfelsturm");
        REGISTRATE.addRawLang("effect.createbrewery.dehydrated", "Ausgetrocknet");
        REGISTRATE.addRawLang("death.attack.createbrewery.overdose", "%1$s hat aufgehört zu atmen");
        REGISTRATE.addRawLang("death.attack.createbrewery.hyperthermia", "%1$s hat sich zu Tode getanzt");
        REGISTRATE.addRawLang("death.attack.createbrewery.skin_picking", "%1$s hat sich blutig gekratzt");
        REGISTRATE.addRawLang("subtitles.createbrewery.cough", "Husten");
        REGISTRATE.addRawLang("subtitles.createbrewery.giggle", "Kichern");
        REGISTRATE.addRawLang("effect.createbrewery.ck_mix", "CK-Mix");
        REGISTRATE.addRawLang("effect.createbrewery.tachycardia", "Herzrasen");
        REGISTRATE.addRawLang("effect.createbrewery.cottonmouth", "Pappmaul");
        REGISTRATE.addRawLang("effect.createbrewery.snack_bliss", "Genuss");
        REGISTRATE.addRawLang("effect.createbrewery.heart_attack", "Herzinfarkt");
        REGISTRATE.addRawLang("effect.createbrewery.aspiration", "Erstickt am Erbrochenen");
        REGISTRATE.addRawLang("death.attack.createbrewery.aspiration", "%1$s ist an seinem Erbrochenen erstickt");
        REGISTRATE.addRawLang("death.attack.createbrewery.nosebleed", "%1$s ist an Nasenbluten gestorben");
        REGISTRATE.addRawLang("death.attack.createbrewery.heart_attack", "%1$s hatte einen Herzinfarkt");
        REGISTRATE.addRawLang("subtitles.createbrewery.sniff", "Schniefen");
        REGISTRATE.addRawLang("death.attack.createbrewery.stomach_bleeding", "%1$s hat Ibu mit Bier runtergespült");
        REGISTRATE.addRawLang("death.attack.createbrewery.painkiller_overdose", "%1$s hat zu viele Ibus geschluckt");
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
        // The "Apotheke" advancement tab: JSON in data/createbrewery/advancement/drugs.
        // advancements:begin
        advancement("root", "Apotheke", "Nimm irgendeine Droge");
        advancement("bier", "Feierabendbier", "Trink ein Bier");
        advancement("joint", "Erstmal einen bauen", "Zieh an einem Joint");
        advancement("greening_out", "Kreislauf am Ende", "Rauch so viel, dass dir der Kreislauf wegkippt");
        advancement("koks", "Nasenpuder", "Zieh eine Line Koks");
        advancement("herzrasen", "Bumm Bumm Bumm", "Bring dein Herz zum Rasen");
        advancement("herzinfarkt", "Das war zu viel", "Erleide einen Herzinfarkt");
        advancement("zweites_leben", "Zweites Leben", "Überlebe einen Herzinfarkt");
        advancement("keta", "Pferdebetäubung", "Zieh eine Line Keta");
        advancement("k_loch", "Willkommen im K-Loch", "Nimm so viel Keta, dass du im K-Loch landest");
        advancement("ck", "Schnee und Pferde", "Hab Koks und Keta gleichzeitig im Blut");
        advancement("ibu", "Hilft gegen alles", "Nimm eine Ibu");
        advancement("lsd", "Farben haben jetzt Geräusche", "Leg dir eine LSD-Pappe auf die Zunge");
        advancement("ego_tod", "Ego-Tod", "Erreiche den Gipfel einer Heldendosis LSD");
        advancement("pilze", "Fun Guy", "Iss einen Zauberpilz");
        advancement("heldendosis", "Heroische Dosis", "Iss drei Zauberpilze und bleib still im Dunkeln");
        advancement("meskalin", "Wüstenprophet", "Kau einen Peyote-Kaktus");
        advancement("purga", "La Purga", "Erlebe die reinigende Katharsis eines Peyote-Trips");
        advancement("pforten", "Pforten der Wahrnehmung", "Erlebe die Istigkeit eines Peyote-Trips in der Wüste");
        advancement("horrortrip", "Falsches Set, falsches Setting", "Rutsch in einen Horrortrip");
        advancement("dmt", "Durch den Vorhang", "Rauch DMT");
        advancement("maschinenelfen", "Hallo Maschinenelfen", "Durchbrich in den Hyperraum und triff seine Bewohner");
        advancement("mdma", "Alle umarmen", "Wirf eine Ecstasy-Pille ein");
        advancement("hitzschlag", "Wasser, Wasser!", "Überhitze beim Tanzen auf Ecstasy");
        advancement("kuschelmonster", "Kuschelmonster", "Umarme auf Ecstasy einen Creeper (schleich dich ran)");
        advancement("meth", "Say My Name", "Rauch Crystal");
        advancement("psychose", "Schattenmenschen", "Bleib auf Crystal so lange wach, bis du Dinge siehst");
        advancement("heroin", "Nicht einnicken", "Setz dir einen Schuss Heroin");
        advancement("atemstillstand", "Atme!", "Nimm so viel, dass dein Atem aussetzt");
        advancement("entzug", "Der Affe sitzt dir im Nacken", "Mach einen Entzug durch");
        advancement("xanax", "Alles egal", "Nimm eine Xanax");
        advancement("lachgas", "Wah Wah", "Atme einen Ballon Lachgas ein");
        advancement("candyflip", "Candyflip", "Wirf Ecstasy auf einem Trip ein");
        advancement("dehydriert", "Ausgetrocknet", "Trink Alkohol auf Ecstasy");
        advancement("speedball", "Speedball", "Misch Heroin mit Koks oder Crystal");
        advancement("nachgelegt", "Nachgelegt", "Kiff auf einem Trip");
        advancement("gipfelsturm", "Gipfelsturm", "Atme Lachgas auf einem Trip ein");
        advancement("apotheke_leer", "Apotheke leergeräumt", "Nimm jede Droge mindestens einmal");
        // advancements:end
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.createbrewery.drugs.DrugServer.class);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private static void advancement(String path, String title, String description) {
        REGISTRATE.addRawLang("advancements.createbrewery.drugs." + path + ".title", title);
        REGISTRATE.addRawLang("advancements.createbrewery.drugs." + path + ".description", description);
    }

    public static ResourceLocation ID(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
