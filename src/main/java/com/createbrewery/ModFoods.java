package com.createbrewery;

import com.createbrewery.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;

public class ModFoods {
    public static final FoodProperties BEER = new FoodProperties.Builder()
        .nutrition(1)
        .saturationModifier(0.1f)
        .alwaysEdible()
        // 1. Drunken Stumbling (Schlingerkurs) (30 seconds)
        .effect(() -> new MobEffectInstance(ModEffects.STUMBLE, 600, 0), 1.0f)
        // 2. Violent Hiccups (Schluckauf) (25 seconds)
        .effect(() -> new MobEffectInstance(ModEffects.HICCUPS, 500, 0), 1.0f)
        // 3. Delirium / Liquid Courage (Größenwahn & Mob Aggro) (20 seconds)
        .effect(() -> new MobEffectInstance(ModEffects.DELIRIUM, 400, 0), 1.0f)
        // 4. Hangover of Doom (Kater des Todes: Lichtempfindlichkeit & Zittrige Hände) (45 seconds)
        .effect(() -> new MobEffectInstance(ModEffects.HANGOVER, 900, 0), 1.0f)
        .build();
}
