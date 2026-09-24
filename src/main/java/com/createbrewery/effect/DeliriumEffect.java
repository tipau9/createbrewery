package com.createbrewery.effect;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class DeliriumEffect extends MobEffect {
    private static final String[] SHOUTS = {
        "\u00a76\u00a7l*LALL* \u00a7e'ICH KANN ES MIT ZEHN CREEPERN GLEICHZEITIG AUFNEHMEN!!'",
        "\u00a76\u00a7l*GR\u00d6L* \u00a7e'WER WILL PR\u00dcGEL?! KOMMT DOCH HER, IHR MONSTER!!'",
        "\u00a76\u00a7l*PROST* \u00a7e'SCHWERKRAFT IST NUR EINE THEORIE!'",
        "\u00a7e\u00a7l*BR\u00dcLL* \u00a76'DER N\u00c4CHSTE ENDERDRACHE GEHT AUF MEINEN NACKEN!!'",
        "\u00a7c\u00a7l*HICKS* \u00a7e'ICH BIN UNBESIEGBAR! H\u00d6RT IHR MICH?! UNBESIEGBAR!!'"
    };

    public DeliriumEffect() {
        super(MobEffectCategory.HARMFUL, 0xFFAA00);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Server only: the client runs this tick too, and would print every shout twice.
        if (!entity.level().isClientSide && entity instanceof Player player) {
            // Loud boastful yelling every 5 seconds
            if (player.tickCount % 100 == 0) {
                String shout = SHOUTS[player.getRandom().nextInt(SHOUTS.length)];
                player.displayClientMessage(Component.literal(shout), true);

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 1.9f);
            }

            // Provoke nearby monsters: drunk shouting attracts hostiles within 18 blocks
            if (player.tickCount % 60 == 0) {
                List<Mob> nearbyMonsters = player.level().getEntitiesOfClass(
                    Mob.class,
                    player.getBoundingBox().inflate(18.0),
                    m -> m instanceof Enemy && m.isAlive()
                );
                for (Mob monster : nearbyMonsters) {
                    if (monster.getTarget() != player) {
                        monster.setTarget(player);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
