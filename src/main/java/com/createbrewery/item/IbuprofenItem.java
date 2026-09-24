package com.createbrewery.item;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.effect.PainkillerEffect;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Ibu 400: the cure for the Kater. Swallowed in a second, works after 25 s (see PainkillerEffect).
 * Not a free pass either:
 * - together with alcohol (either way round) it hurts the stomach at once, and on top of a lot of
 *   alcohol it comes back up (with everything in it);
 * - a third pill while the others still work is an overdose and hurts badly.
 */
public class IbuprofenItem extends Item {
    private static final int COOLDOWN = 200;

    public IbuprofenItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 20;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        if (!level.isClientSide) {
            MobEffectInstance before = player.getEffect(ModEffects.PAINKILLER);
            int pills = before == null ? 0 : before.getAmplifier() + 1;
            player.addEffect(new MobEffectInstance(ModEffects.PAINKILLER, PainkillerEffect.DURATION, pills, false, false, true));
            player.getCooldowns().addCooldown(this, COOLDOWN);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6f, 1.4f);

            if (pills >= 2) {
                player.hurt(DrunkServer.overdoseSource(level), 6f);
            }
            // Ibu on top of alcohol: damage right away, and with a lot of it the stomach gives up.
            if (DrunkServer.state(player).hasAlcohol()) DrunkServer.irritateStomach(player);
            if (DrunkServer.state(player).blood >= Intoxication.DRUNK) {
                DrunkServer.vomit(player);
            }
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }
}
