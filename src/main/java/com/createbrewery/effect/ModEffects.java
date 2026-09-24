package com.createbrewery.effect;

import com.createbrewery.CreateBrewery;
import net.minecraft.core.registries.BuiltInRegistries;
import com.createbrewery.CreateBrewery;
import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.drugs.DrugServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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

    public static final DeferredHolder<MobEffect, MobEffect> COKE_HIGH = EFFECTS.register("coke_high", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0xF4F4FF, 20, DrugServer::cokeTick)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("coke_speed"), 0.12, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, id("coke_mining"), 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> COKE_CRASH = EFFECTS.register("coke_crash", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x5A5A66, 20, DrugServer::crashTick)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("crash_speed"), -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, id("crash_mining"), -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> KETA_HIGH = EFFECTS.register("keta_high", () ->
        new DrugEffect(MobEffectCategory.NEUTRAL, 0x9FB8E8, 20, DrugServer::ketaTick)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("keta_speed"), -0.12, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> K_HOLE = EFFECTS.register("k_hole", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x2B2F5A, 0, null)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("k_hole_speed"), -0.7, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> DAZED = EFFECTS.register("dazed", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0x8A8FA8, 0, null)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("dazed_speed"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> CK_MIX = EFFECTS.register("ck_mix", () ->
        new DrugEffect(MobEffectCategory.HARMFUL, 0xC44A7A, 20, DrugServer::mixTick));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "effect." + path);
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
