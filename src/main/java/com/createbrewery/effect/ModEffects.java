package com.createbrewery.effect;

import com.createbrewery.CreateBrewery;
import net.minecraft.core.registries.BuiltInRegistries;
import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.drugs.DrugServer;
import com.createbrewery.drugs.Psychedelics;
import com.createbrewery.drugs.Stimulants;
import com.createbrewery.drugs.Opioids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
        DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, CreateBrewery.MOD_ID);

    public static final DeferredHolder<MobEffect, InebriationEffect> INEBRIATION =
        EFFECTS.register("inebriation", InebriationEffect::new);

    public static final DeferredHolder<MobEffect, HangoverEffect> HANGOVER =
        EFFECTS.register("hangover", HangoverEffect::new);

    public static final DeferredHolder<MobEffect, HiccupsEffect> HICCUPS =
        EFFECTS.register("hiccups", HiccupsEffect::new);

    public static final DeferredHolder<MobEffect, DrunkStumbleEffect> STUMBLE =
        EFFECTS.register("stumble", DrunkStumbleEffect::new);

    public static final DeferredHolder<MobEffect, DeliriumEffect> DELIRIUM =
        EFFECTS.register("delirium", DeliriumEffect::new);

    public static final DeferredHolder<MobEffect, BlackoutEffect> BLACKOUT =
        EFFECTS.register("blackout", BlackoutEffect::new);

    public static final DeferredHolder<MobEffect, AlcoholPoisoningEffect> POISONING =
        EFFECTS.register("alcohol_poisoning", AlcoholPoisoningEffect::new);

    public static final DeferredHolder<MobEffect, GoodMoodEffect> GOOD_MOOD =
        EFFECTS.register("good_mood", GoodMoodEffect::new);

    public static final DeferredHolder<MobEffect, CheersEffect> CHEERS =
        EFFECTS.register("cheers", CheersEffect::new);

    public static final DeferredHolder<MobEffect, VomitingEffect> VOMITING =
        EFFECTS.register("vomiting", VomitingEffect::new);

    public static final DeferredHolder<MobEffect, PainkillerEffect> PAINKILLER =
        EFFECTS.register("painkiller", PainkillerEffect::new);

    // ---- Koks and Keta (see drugs/DrugServer) ----

    // Each comes up, holds and fades (DrugEffect/Pharmacology). Game time runs ~20x real time:
    // a Koks line of about an hour is three minutes here, its come-up of a few minutes 15 s.

    public static final DeferredHolder<MobEffect, MobEffect> COKE_HIGH = EFFECTS.register("coke_high", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xF4F4FF, 20, DrugServer::cokeTick, DrugServer.COKE_TICKS, 300, 900).stacks()
            .scaled(Attributes.MOVEMENT_SPEED, id("coke_speed"), 0.15)
            .scaled(Attributes.BLOCK_BREAK_SPEED, id("coke_mining"), 0.35));

    public static final DeferredHolder<MobEffect, MobEffect> COKE_CRASH = EFFECTS.register("coke_crash", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x5A5A66, 20, DrugServer::crashTick, 0, 0, 400)
            .scaled(Attributes.MOVEMENT_SPEED, id("crash_speed"), -0.2)
            .scaled(Attributes.BLOCK_BREAK_SPEED, id("crash_mining"), -0.35));

    public static final DeferredHolder<MobEffect, MobEffect> KETA_HIGH = EFFECTS.register("keta_high", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x9FB8E8, 20, DrugServer::ketaTick, DrugServer.KETA_TICKS, 400, 600).stacks()
            .scaled(Attributes.MOVEMENT_SPEED, id("keta_speed"), -0.15));

    public static final DeferredHolder<MobEffect, MobEffect> K_HOLE = EFFECTS.register("k_hole", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x2B2F5A, 0, null, DrugServer.K_HOLE_TICKS, 100, 200)
            .scaled(Attributes.MOVEMENT_SPEED, id("k_hole_speed"), -0.7));

    public static final DeferredHolder<MobEffect, MobEffect> DAZED = EFFECTS.register("dazed", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x8A8FA8, 0, null, 0, 0, 400)
            .scaled(Attributes.MOVEMENT_SPEED, id("dazed_speed"), -0.15));

    public static final DeferredHolder<MobEffect, MobEffect> WEED_HIGH = EFFECTS.register("weed_high", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x6FAF4A, 20, DrugServer::weedTick, DrugServer.WEED_TICKS, 300, 1600).stacks(DrugServer.HITS_PER_JOINT)
            .scaled(Attributes.MOVEMENT_SPEED, id("weed_speed"), -0.12)); // couch lock, strongest with stacked joints

    public static final DeferredHolder<MobEffect, MobEffect> GREENING_OUT = EFFECTS.register("greening_out", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x9CC25A, 20, DrugServer::greeningTick, DrugServer.GREENING_TICKS, 60, 200)
            .scaled(Attributes.MOVEMENT_SPEED, id("greening_speed"), -0.45));

    public static final DeferredHolder<MobEffect, MobEffect> CK_MIX = EFFECTS.register("ck_mix", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0xC44A7A, 20, DrugServer::mixTick, 0, 0, 0));

    /** Herzrasen: the heart is overloaded (amplifier 1: strained). The warning before it gives out. */
    public static final DeferredHolder<MobEffect, MobEffect> TACHYCARDIA = EFFECTS.register("tachycardia", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0xE0304A, 0, null, 0, 0, 0));

    /** Herzinfarkt: collapsed, the heart failing; a friend doing CPR (sneaking next to you) keeps you alive. */
    public static final DeferredHolder<MobEffect, MobEffect> HEART_ATTACK = EFFECTS.register("heart_attack", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x7A0010, 40, DrugServer::heartAttackTick, 0, 0, 0)
            .scaled(Attributes.MOVEMENT_SPEED, id("heart_attack_speed"), -0.85)
            .scaled(Attributes.BLOCK_BREAK_SPEED, id("heart_attack_mining"), -0.8));

    /** Throwing up while out cold: the airway fills. A friend turning you on your side saves you. */
    public static final DeferredHolder<MobEffect, MobEffect> ASPIRATION = EFFECTS.register("aspiration", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x6B7A1E, 20, DrugServer::aspirationTick, 0, 0, 0));

    /** Pappmaul: no spit left after smoking. Drinking anything takes it away. */
    public static final DeferredHolder<MobEffect, MobEffect> COTTONMOUTH = EFFECTS.register("cottonmouth", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0xD8C49A, 0, null, 0, 0, 0));

    /** Genuss: something sweet while high - the best thing you ever ate. Fixed, so the hearts do not shrink. */
    public static final DeferredHolder<MobEffect, MobEffect> SNACK_BLISS = EFFECTS.register("snack_bliss", () ->
        new MobEffect(MobEffectCategory.BENEFICIAL, 0xF2A65A) {
            @Override
            public void onEffectStarted(net.minecraft.world.entity.LivingEntity entity, int amplifier) {
                super.onEffectStarted(entity, amplifier);
                entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), 4f));
            }
        }.addAttributeModifier(Attributes.LUCK, id("snack_bliss_luck"), 1.0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("snack_bliss_speed"), 0.1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.MAX_ABSORPTION, id("snack_bliss_absorption"), 4.0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));

    // ---- LSD, Pilze, Meskalin (see drugs/Psychedelics) ----

    // Real trips last 6-12 hours; here 6-12 minutes, with a slow come-up of one to two.

    public static final DeferredHolder<MobEffect, MobEffect> LSD_TRIP = EFFECTS.register("lsd_trip", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xE040FB, 20, Psychedelics::tripTick, Psychedelics.LSD_TICKS, 1800, 3600).stacks());

    public static final DeferredHolder<MobEffect, MobEffect> SHROOM_TRIP = EFFECTS.register("shroom_trip", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x7CC47A, 20, Psychedelics::tripTick, Psychedelics.SHROOM_TICKS, 1200, 2400).stacks()
            .scaled(Attributes.MOVEMENT_SPEED, id("shroom_speed"), -0.06));

    public static final DeferredHolder<MobEffect, MobEffect> MESCALINE_TRIP = EFFECTS.register("mescaline_trip", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xE8A13A, 20, Psychedelics::tripTick, Psychedelics.MESCALINE_TICKS, 2400, 3600).stacks());

    /** Durchbruch (DMT): the body stays behind while the mind leaves. */
    public static final DeferredHolder<MobEffect, MobEffect> BREAKTHROUGH = EFFECTS.register("breakthrough", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x00E5FF, 20, Psychedelics::breakthroughTick, Psychedelics.DMT_TICKS, 60, 300)
            .scaled(Attributes.MOVEMENT_SPEED, id("breakthrough_speed"), -0.9));

    /** Toleranz: after a trip, any psychedelic does little for about a day. */
    public static final DeferredHolder<MobEffect, MobEffect> PSY_TOLERANCE = EFFECTS.register("psy_tolerance", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x8E7CA8, 0, null, 0, 0, 0));

    /** Nachglühen: the day after a trip - clear colours, a quiet mind, a little luck. */
    public static final DeferredHolder<MobEffect, MobEffect> AFTERGLOW = EFFECTS.register("afterglow", () ->
        new DrugEffect(MobEffectCategory.BENEFICIAL, 0xFFE9A8, 0, null, Psychedelics.AFTERGLOW_TICKS, 200, 3000)
            .addAttributeModifier(Attributes.LUCK, id("afterglow_luck"), 1.0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));

    /** Flashback: a few seconds of the old trip, out of nowhere, days later. */
    public static final DeferredHolder<MobEffect, MobEffect> FLASHBACK = EFFECTS.register("flashback", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xC080FF, 0, null, Psychedelics.FLASHBACK_TICKS, 40, 100));

    /** Nachhall: a flashback still waiting to happen. Never shown. */
    public static final DeferredHolder<MobEffect, MobEffect> FLASHBACK_PENDING = EFFECTS.register("flashback_pending", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x6A5A80, 0, null, 0, 0, 0));

    /** Horrortrip: fear takes over. Somewhere bright and safe, it passes faster. */
    public static final DeferredHolder<MobEffect, MobEffect> BAD_TRIP = EFFECTS.register("bad_trip", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x5A1030, 20, Psychedelics::badTripTick, Psychedelics.BAD_TRIP_TICKS, 100, 400)
            .scaled(Attributes.MOVEMENT_SPEED, id("bad_trip_speed"), 0.1));

    // ---- MDMA and Meth (see drugs/Stimulants) ----

    public static final DeferredHolder<MobEffect, MobEffect> ROLLING = EFFECTS.register("rolling", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xFF7EB6, 20, Stimulants::rollTick, Stimulants.MDMA_TICKS, 900, 1800).stacks()
            .scaled(Attributes.MOVEMENT_SPEED, id("rolling_speed"), 0.1));

    /** Tiefpunkt: the serotonin is used up. Sad, weak, grey. */
    public static final DeferredHolder<MobEffect, MobEffect> COMEDOWN = EFFECTS.register("comedown", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x6A6A8A, 0, null, 0, 0, 600)
            .scaled(Attributes.MOVEMENT_SPEED, id("comedown_speed"), -0.1)
            .scaled(Attributes.ATTACK_DAMAGE, id("comedown_damage"), -0.25));

    public static final DeferredHolder<MobEffect, MobEffect> TWEAK = EFFECTS.register("tweak", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x9FE3FF, 20, Stimulants::tweakTick, Stimulants.METH_TICKS, 200, 3000).stacks()
            .scaled(Attributes.MOVEMENT_SPEED, id("tweak_speed"), 0.2)
            .scaled(Attributes.BLOCK_BREAK_SPEED, id("tweak_mining"), 0.5)
            .scaled(Attributes.ATTACK_SPEED, id("tweak_attack_speed"), 0.3));

    public static final DeferredHolder<MobEffect, MobEffect> METH_CRASH = EFFECTS.register("meth_crash", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x44485A, 20, DrugServer::crashTick, 0, 0, 800)
            .scaled(Attributes.MOVEMENT_SPEED, id("meth_crash_speed"), -0.3)
            .scaled(Attributes.BLOCK_BREAK_SPEED, id("meth_crash_mining"), -0.4));

    /** Hitzschlag: overheated from dancing on MDMA. Water, shade and rest. */
    public static final DeferredHolder<MobEffect, MobEffect> HYPERTHERMIA = EFFECTS.register("hyperthermia", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0xFF5A1E, 20, Stimulants::hyperthermiaTick, 0, 0, 0));

    /** Psychose: too long awake on meth. The client shows it like a bad trip, shadow people included. */
    public static final DeferredHolder<MobEffect, MobEffect> PSYCHOSIS = EFFECTS.register("psychosis", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x3A2A4A, 0, null, Stimulants.PSYCHOSIS_TICKS, 100, 400));

    // ---- Heroin and Xanax (see drugs/Opioids) ----

    public static final DeferredHolder<MobEffect, MobEffect> NOD = EFFECTS.register("nod", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xC8A060, 0, null, Opioids.HEROIN_TICKS, 60, 2400).stacks()
            .scaled(Attributes.MOVEMENT_SPEED, id("nod_speed"), -0.2));

    public static final DeferredHolder<MobEffect, MobEffect> CALM = EFFECTS.register("calm", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xB8C8E0, 20, Opioids::calmTick, Opioids.XANAX_TICKS, 400, 2000).stacks()
            .scaled(Attributes.MOVEMENT_SPEED, id("calm_speed"), -0.1));

    /** Atemlähmung: the breath stops. Someone crouched next to you breathes for you. */
    public static final DeferredHolder<MobEffect, MobEffect> RESPIRATORY_DEPRESSION = EFFECTS.register("respiratory_depression", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x3050A0, 20, Opioids::breathTick, 0, 0, 0)
            .scaled(Attributes.MOVEMENT_SPEED, id("respiratory_depression_speed"), -0.8));

    /** Entzug: the body wants heroin. */
    public static final DeferredHolder<MobEffect, MobEffect> WITHDRAWAL = EFFECTS.register("withdrawal", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x7A8A5A, 20, Opioids::withdrawalTick, 0, 0, 0)
            .scaled(Attributes.ATTACK_DAMAGE, id("withdrawal_damage"), -0.3)
            .scaled(Attributes.BLOCK_BREAK_SPEED, id("withdrawal_mining"), -0.3));

    /** Naloxon: it pushes the opioid off the receptors - for a minute. Whatever is left comes back after. */
    public static final DeferredHolder<MobEffect, MobEffect> NALOXONE = EFFECTS.register("naloxone", () ->
        new DrugEffect(MobEffectCategory.BENEFICIAL, 0xF08A30, 0, null, 0, 0, 0));

    /** Gewöhnung: the heroin high wears thin (hidden; amplifier 0..2). */
    public static final DeferredHolder<MobEffect, MobEffect> OPIOID_HABIT = EFFECTS.register("opioid_habit", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x8A7050, 0, null, 0, 0, 0));

    /** Krampfanfall: benzo withdrawal (or water poisoning) - the body convulses, nothing obeys. */
    public static final DeferredHolder<MobEffect, MobEffect> SEIZURE = EFFECTS.register("seizure", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x9040C0, 20, Opioids::seizureTick, 0, 0, 0)
            .scaled(Attributes.MOVEMENT_SPEED, id("seizure_speed"), -1.0));

    /** Lachgas: seconds of giggling, wah-wah and a world far away. */
    public static final DeferredHolder<MobEffect, MobEffect> WAH = EFFECTS.register("wah", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xC8DCFF, 20, DrugServer::wahTick, DrugServer.WAH_TICKS, 20, 160)
            .scaled(Attributes.MOVEMENT_SPEED, id("wah_speed"), -0.4));

    // ---- Named mixes (see drugs/Mixes): shown while both are in the body ----

    public static final DeferredHolder<MobEffect, MobEffect> CANDYFLIP = EFFECTS.register("candyflip", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xFF9AE0, 0, null, 0, 0, 0));
    public static final DeferredHolder<MobEffect, MobEffect> SPEEDBALL = EFFECTS.register("speedball", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0xB03020, 0, null, 0, 0, 0));
    public static final DeferredHolder<MobEffect, MobEffect> STONED_TRIP = EFFECTS.register("stoned_trip", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x8FCF5A, 0, null, 0, 0, 0));
    public static final DeferredHolder<MobEffect, MobEffect> NITROUS_PEAK = EFFECTS.register("nitrous_peak", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x9AF0FF, 0, null, 0, 0, 0));
    public static final DeferredHolder<MobEffect, MobEffect> DEHYDRATED = EFFECTS.register("dehydrated", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0xD8A060, 0, null, 0, 0, 0));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "effect." + path);
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
