package com.createbrewery;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

public class ModFoods {
    public static final FoodProperties BEER = new FoodProperties.Builder()
        .nutrition(1)
        .saturationModifier(0.1f)
        .alwaysEdible()
        // 1. Extreme Nausea (20s): camera tilts and sways wildly
        .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 400, 0), 1.0f)
        // 2. Severe Slowness III (15s): heavy drunken stumbling
        .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 2), 1.0f)
        // 3. Heavy Mining Fatigue III (20s): heavy arms, can't mine
        .effect(() -> new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 400, 2), 1.0f)
        // 4. Sudden Blackout / Blindness (5s): total vision loss
        .effect(() -> new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), 1.0f)
        // 5. Ravenous Hangover Hunger III (10s): stomach drains fast
        .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 200, 2), 1.0f)
        // 6. Creepy Darkness (10s): eerie pulsing light
        .effect(() -> new MobEffectInstance(MobEffects.DARKNESS, 200, 0), 1.0f)
        // 7. Bad Luck / Pech (30s)
        .effect(() -> new MobEffectInstance(MobEffects.UNLUCK, 600, 1), 1.0f)
        // 8. 5 seconds of false "Liquid Courage" (Strength II) followed by Weakness (15s)
        .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 1), 1.0f)
        .effect(() -> new MobEffectInstance(MobEffects.WEAKNESS, 300, 1), 1.0f)
        .build();
}
