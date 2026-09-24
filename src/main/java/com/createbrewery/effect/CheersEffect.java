package com.createbrewery.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Geselligkeit: earned by clinking glasses - two players drinking next to each other at the
 * same moment (see DrunkServer). Good company heals: half a heart every 5 seconds, but only
 * while you are still merry, not once you are wasted.
 */
public class CheersEffect extends MobEffect {
    public CheersEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE85C9A);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide && !entity.hasEffect(ModEffects.STUMBLE)) entity.heal(1.0f);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 100 == 0;
    }
}
