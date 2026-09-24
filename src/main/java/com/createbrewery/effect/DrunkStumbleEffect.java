package com.createbrewery.effect;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class DrunkStumbleEffect extends MobEffect {
    public DrunkStumbleEffect() {
        super(MobEffectCategory.HARMFUL, 0xDD5511);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            Vec3 movement = player.getDeltaMovement();
            boolean isMoving = movement.horizontalDistanceSqr() > 0.005;

            // 1. Sideways drunken sway while moving
            if (isMoving && player.tickCount % 20 == 0) {
                float yaw = player.getYRot();
                double angle = Math.toRadians(yaw + (player.getRandom().nextBoolean() ? 90.0 : -90.0));
                double swayForce = 0.35 + amplifier * 0.1;
                player.setDeltaMovement(movement.add(Math.sin(-angle) * swayForce, 0.08, Math.cos(-angle) * swayForce));
                player.hurtMarked = true;
            }

            // 2. Periodic trip / faceplant every 120 ticks
            if (player.tickCount % 120 == 0) {
                player.hurt(player.damageSources().generic(), 1.0f);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.2f, 0.8f);
                player.displayClientMessage(
                    Component.literal("\u00a7c\u00a7l*PATSCH!* \u00a76\u00dcber die eigenen Beine gestolpert!"), true);
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
