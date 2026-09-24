package com.createbrewery.effect;

import com.createbrewery.CreateBrewery;
import net.minecraft.core.registries.BuiltInRegistries;
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

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
