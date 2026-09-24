package com.createbrewery.effect;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Schlingerkurs. The constant weaving walk is done client-side on the movement input (see the
 * client drunk code), where it feels smooth and can be steered against. This effect adds the
 * server-side accidents on top: sprinting drunk trips you, and now and then your legs just go.
 * Server only - effect ticks also run on the client, and a second random shove there fights
 * the server's and makes the player jitter.
 */
public class DrunkStumbleEffect extends MobEffect {
    public DrunkStumbleEffect() {
        super(MobEffectCategory.HARMFUL, 0xDD5511);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || !(entity instanceof Player player) || !player.onGround()) return true;

        float roll = player.getRandom().nextFloat();
        if (player.isSprinting() && roll < 0.15f + amplifier * 0.1f) {
            // Faceplant: thrown forward, sprint broken, and it hurts once you are properly smashed.
            Vec3 look = player.getLookAngle();
            player.setSprinting(false);
            player.setDeltaMovement(look.x * 0.5, 0.12, look.z * 0.5);
            player.hurtMarked = true;
            if (amplifier >= 1) player.hurt(player.damageSources().fall(), 1.0f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.2f, 0.8f);
        } else if (roll < 0.04f + amplifier * 0.03f) {
            // The legs forget which way is down for a moment: a sideways stagger.
            double side = Math.toRadians(player.getYRot() + (player.getRandom().nextBoolean() ? 90.0 : -90.0));
            player.setDeltaMovement(player.getDeltaMovement().add(-Math.sin(side) * 0.3, 0.05, Math.cos(side) * 0.3));
            player.hurtMarked = true;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 1.0f, 0.7f);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}
