package com.createbrewery.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class InebriationEffect extends MobEffect {
    public InebriationEffect() {
        super(MobEffectCategory.NEUTRAL, 0xF0A020);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
