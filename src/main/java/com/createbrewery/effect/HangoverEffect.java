package com.createbrewery.effect;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class HangoverEffect extends MobEffect {
    public HangoverEffect() {
        super(MobEffectCategory.HARMFUL, 0x784421);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            // 1. Photophobia (Lichtempfindlichkeit): sunlight hurts the throbbing head!
            if (player.level().isDay() && player.level().canSeeSky(player.blockPosition())) {
                if (player.tickCount % 60 == 0) {
                    player.hurt(player.damageSources().dryOut(), 1.0f);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.6f);
                    player.displayClientMessage(
                        Component.literal("\u00a76\u00a7l*KOPFWEH* \u00a7cDie Sonne blendet unmenschlich! Aua mein Sch\u00e4del..."), true);
                }
            }

            // 2. Butterfinger Tremors (Zittrige H\u00e4nde): randomly drop held item
            if (player.tickCount % 100 == 0) {
                ItemStack held = player.getMainHandItem();
                if (!held.isEmpty() && player.getRandom().nextFloat() < (0.35f + amplifier * 0.15f)) {
                    ItemStack dropped = held.split(1);
                    player.drop(dropped, false);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 0.4f);
                    player.displayClientMessage(
                        Component.literal("\u00a7c\u00a7lUpps! \u00a76Deine zittrigen H\u00e4nde lassen das Werkzeug fallen!"), true);
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
