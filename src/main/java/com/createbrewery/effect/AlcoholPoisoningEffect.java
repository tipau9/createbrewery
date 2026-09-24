package com.createbrewery.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Alkoholvergiftung: the status icon only. DrunkServer deals the damage from the blood level,
 * so the harm follows the actual alcohol and not an effect timer.
 */
public class AlcoholPoisoningEffect extends MobEffect {
    public AlcoholPoisoningEffect() {
        super(MobEffectCategory.HARMFUL, 0x5A7A10);
    }
}
