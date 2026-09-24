package com.createbrewery.effect;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Schmerzmittel, from an Ibu 400. Like the real thing it takes a while: after 25 seconds it kicks
 * in, clears a hangover and keeps a new one from starting for as long as it lasts (5 minutes) -
 * so one taken before the headache arrives works too. The amplifier counts pills taken while it
 * is still active; see IbuprofenItem for what happens if you keep swallowing them.
 */
public class PainkillerEffect extends MobEffect {
    public static final int DURATION = 6000;
    /** 25 s until it works. */
    public static final int ONSET = 500;

    public PainkillerEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xDDE6F5);
    }

    /** Whether the painkiller has kicked in: taken, and the onset time has passed. */
    public static boolean working(LivingEntity entity) {
        MobEffectInstance instance = entity.getEffect(ModEffects.PAINKILLER);
        return instance != null && instance.getDuration() <= DURATION - ONSET;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide && working(entity) && entity.hasEffect(ModEffects.HANGOVER)) {
            entity.removeEffect(ModEffects.HANGOVER);
            // The headache lets go: a long, relieved breath.
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}
