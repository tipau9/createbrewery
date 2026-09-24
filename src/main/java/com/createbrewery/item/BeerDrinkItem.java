package com.createbrewery.item;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * A drinkable beer. Drinking only puts alcohol in the stomach (see {@link DrunkServer}); every
 * symptom follows from the blood level as it is absorbed, so each beer makes things worse
 * gradually instead of flipping a switch.
 */
public class BeerDrinkItem extends Item {
    private final Supplier<? extends ItemLike> returnItemSupplier;

    public BeerDrinkItem(Properties properties, Supplier<? extends ItemLike> returnItemSupplier) {
        super(properties);
        this.returnItemSupplier = returnItemSupplier;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (entity instanceof Player player && !level.isClientSide) {
            float after = DrunkServer.state(player).total() + Intoxication.PER_BEER;
            DrunkServer.drink(player, Intoxication.PER_BEER);
            reactToDrink(player, level, after);

            if (!player.getAbilities().instabuild && returnItemSupplier != null) {
                ItemLike returnItem = returnItemSupplier.get();
                if (returnItem != null) {
                    ItemStack returnStack = new ItemStack(returnItem);
                    if (result.isEmpty()) {
                        return returnStack;
                    }
                    if (!player.getInventory().add(returnStack)) {
                        player.drop(returnStack, false);
                    }
                }
            }
        }

        return result;
    }

    /** The burp gets deeper and louder with every beer. */
    private static void reactToDrink(Player player, Level level, float perMille) {
        float volume = 0.8f + Math.min(perMille, 3f) * 0.2f;
        float pitch = Math.max(0.5f, 1.2f - perMille * 0.25f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, volume, pitch);
    }
}
