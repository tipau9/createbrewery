package com.createbrewery.drugs;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiConsumer;

/**
 * A drug effect: attribute changes (added at registration) plus an optional server-side tick
 * every {@code interval} ticks. The client derives all of its visuals from which of these
 * effects are active (see DrunkClient), so the effects themselves stay small.
 */
public class DrugEffect extends MobEffect {
    private final int interval;
    private final BiConsumer<LivingEntity, Integer> serverTick;

    public DrugEffect(MobEffectCategory category, int colour, int interval, BiConsumer<LivingEntity, Integer> serverTick) {
        super(category, colour);
        this.interval = interval;
        this.serverTick = serverTick;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Server only: the client runs effect ticks too, and random rolls there would disagree.
        if (!entity.level().isClientSide && serverTick != null) serverTick.accept(entity, amplifier);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return serverTick != null && interval > 0 && duration % interval == 0;
    }
}
