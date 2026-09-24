package com.createbrewery.effect;

import com.createbrewery.particle.ModParticles;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Kotzanfall: a few seconds of heaving, not a single puff. Every heave sprays a stream of
 * chunks forward out of the mouth, with a retch sound; the client bends the view down, shakes
 * it and tints the screen (see DrunkClient). The stomach itself is emptied in DrunkServer.vomit.
 */
public class VomitingEffect extends MobEffect {
    public static final int DURATION = 60;
    /** Ticks between two heaves. */
    public static final int HEAVE = 12;

    public VomitingEffect() {
        super(MobEffectCategory.HARMFUL, 0x7A8A1E);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return true;
        RandomSource random = entity.getRandom();
        Vec3 look = entity.getLookAngle();
        // The stream leaves the mouth slightly downwards, whatever you were looking at.
        Vec3 dir = new Vec3(look.x, Math.min(look.y, 0.0) - 0.35, look.z).normalize();
        Vec3 mouth = entity.getEyePosition().add(look.x * 0.35, -0.2, look.z * 0.35);
        for (int i = 0; i < 24; i++) {
            double speed = 0.25 + random.nextDouble() * 0.3;
            double sx = dir.x + (random.nextDouble() - 0.5) * 0.35;
            double sy = dir.y + (random.nextDouble() - 0.5) * 0.25;
            double sz = dir.z + (random.nextDouble() - 0.5) * 0.35;
            // count 0: the offsets are the particle's velocity.
            SimpleParticleType kind = random.nextFloat() < 0.7f ? ModParticles.VOMIT_CHUNK.get() : ModParticles.VOMIT_SPLASH.get();
            level.sendParticles(kind, mouth.x, mouth.y, mouth.z, 0, sx, sy, sz, speed);
        }
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.2f, 0.4f + random.nextFloat() * 0.15f);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.9f, 0.28f + random.nextFloat() * 0.08f);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
            SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0f, 0.5f + random.nextFloat() * 0.2f);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
            SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.9f, 0.6f);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // A heave every 12 ticks; the last half second is only gasping.
        return duration % HEAVE == 0 && duration > HEAVE;
    }
}
