package com.createbrewery.effect;

import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Schluckauf. Effect ticks run on both sides with the same synced duration, so the hiccup
 * lands on the same tick everywhere: the server does the physical jolt, sound and particles,
 * and the owning client jerks the camera (a server-side rotation change never reaches the
 * client, which owns its own view).
 */
public class HiccupsEffect extends MobEffect {
    /** Set by the client at startup; a no-op on a dedicated server. */
    public static Consumer<LivingEntity> clientKick = entity -> {};

    public HiccupsEffect() {
        super(MobEffectCategory.HARMFUL, 0x44DD55);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            clientKick.accept(entity);
            return true;
        }
        if (entity instanceof Player player) {
            if (player.onGround()) {
                Vec3 m = player.getDeltaMovement();
                double randX = (player.getRandom().nextDouble() - 0.5) * 0.2;
                double randZ = (player.getRandom().nextDouble() - 0.5) * 0.2;
                player.setDeltaMovement(m.x + randX, Math.max(m.y, 0.3 + amplifier * 0.06), m.z + randZ);
                player.hurtMarked = true;
            }

            float pitch = 0.9f + player.getRandom().nextFloat() * 0.25f;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.HICCUP.get(), SoundSource.PLAYERS, 1.2f, pitch);

            if (player.level() instanceof ServerLevel serverLevel) {
                Vec3 look = player.getLookAngle();
                serverLevel.sendParticles(ModParticles.BEER_FOAM.get(), player.getX() + look.x * 0.4,
                    player.getEyeY() - 0.15, player.getZ() + look.z * 0.4, 6, 0.1, 0.05, 0.1, 0.02);
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(30, 48 - amplifier * 8);
        return duration % interval == 0;
    }
}
