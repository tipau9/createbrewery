package com.createbrewery.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class HiccupsEffect extends MobEffect {
    public HiccupsEffect() {
        super(MobEffectCategory.HARMFUL, 0x44DD55);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            Vec3 currentMovement = player.getDeltaMovement();
            double randX = (player.getRandom().nextDouble() - 0.5) * 0.25;
            double randZ = (player.getRandom().nextDouble() - 0.5) * 0.25;
            double upwardJolt = 0.44 + (amplifier * 0.08);

            player.setDeltaMovement(currentMovement.x + randX, upwardJolt, currentMovement.z + randZ);
            player.hurtMarked = true;

            // Sudden camera jerk upwards and sideways
            player.setXRot(player.getXRot() - 12.0f - player.getRandom().nextFloat() * 8.0f);
            player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * 20.0f);

            // Sounds: comical hiccup squeak & pop
            float pitch = 1.6f + player.getRandom().nextFloat() * 0.5f;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.5f, 0.6f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.2f, pitch);

            // Particles
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.BUBBLE_POP,
                    player.getX(), player.getEyeY(), player.getZ(),
                    10, 0.2, 0.2, 0.2, 0.05);
            }

            player.displayClientMessage(
                Component.literal("\u00a7a\u00a7l*HIIICK!* \u00a72(Schluckauf kickt rein!)"), true);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(30, 48 - amplifier * 8);
        return duration % interval == 0;
    }
}
