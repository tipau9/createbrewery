package com.createbrewery.effect;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kater des Todes. Arrives after the drinking, not during it: when the blood level runs out
 * or when you wake up after a heavy night (see DrunkServer). Server side here: shaking hands,
 * a body burning through food, and a head that cannot stand daylight. The client adds the
 * throbbing headache and the painful glare on screen, and DrunkServer slows mining.
 */
public class HangoverEffect extends MobEffect {
    public HangoverEffect() {
        super(MobEffectCategory.HARMFUL, 0x784421);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || !(entity instanceof Player player)) return true;

        // A hungover body is running on empty.
        player.causeFoodExhaustion(0.005f);

        // Photophobia: the glare itself is drawn client-side; this is the grumbling.
        if (player.tickCount % 200 == 0 && player.level().isDay() && player.level().canSeeSky(player.blockPosition())) {
            player.displayClientMessage(Component.literal(
                "§6§l*KOPFWEH* §cDie Sonne sticht dir direkt ins Hirn..."), true);
        }

        // Shaking hands: every 10 s a chance to lose grip of whatever you hold.
        if (player.tickCount % 200 == 0 && !player.isCreative() && !player.isSpectator()) {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty() && player.getRandom().nextFloat() < 0.12f + amplifier * 0.06f) {
                player.drop(held.split(1), false);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 0.4f);
                player.displayClientMessage(Component.literal(
                    "§c§lUpps! §6Deine zittrigen Hände lassen es fallen!"), true);
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
