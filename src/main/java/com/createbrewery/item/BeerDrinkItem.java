package com.createbrewery.item;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.phys.Vec3;
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

import java.util.List;
import java.util.function.Supplier;

/**
 * A drinkable beer. Drinking only puts alcohol in the stomach (see {@link DrunkServer}); every
 * symptom follows from the blood level as it is absorbed, so each beer makes things worse
 * gradually instead of flipping a switch.
 */
public class BeerDrinkItem extends Item {
    private final Supplier<? extends ItemLike> returnItemSupplier;
    /** Filled on first use, once all items are registered. */
    private static List<Item> alcoholicItems;

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

    /** Zisch: the bottle or can is opened as you lift it - unless the drink cooldown still runs. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BEER_OPEN.get(),
                SoundSource.PLAYERS, 0.8f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (entity instanceof Player player) {
            // Apply cooldown on BOTH client and server (like Ender Pearl) so the white
            // sweep animation displays immediately and rapid chugging is blocked.
            startCooldown(player);

            if (!level.isClientSide) {
                float after = DrunkServer.state(player).total() + Intoxication.PER_BEER;
                DrunkServer.drink(player, Intoxication.PER_BEER);
                reactToDrink(player, level, after);
            if (level instanceof ServerLevel serverLevel) {
                Vec3 look = player.getLookAngle();
                serverLevel.sendParticles(ModParticles.BEER_FOAM.get(), player.getX() + look.x * 0.35,
                    player.getEyeY() - 0.2, player.getZ() + look.z * 0.35, 5, 0.08, 0.03, 0.08, 0.01);
            }

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
        }

        return result;
    }

    /**
     * One shared cooldown for every alcoholic drink (like an Ender Pearl):
     * after a beer, no drink can be chugged immediately. Shows the white sweep animation.
     */
    private void startCooldown(Player player) {
        player.getCooldowns().addCooldown(this, Intoxication.DRINK_COOLDOWN);
        if (alcoholicItems == null) {
            alcoholicItems = BuiltInRegistries.ITEM.stream().filter(i -> i instanceof BeerDrinkItem).toList();
        }
        for (Item item : alcoholicItems) {
            player.getCooldowns().addCooldown(item, Intoxication.DRINK_COOLDOWN);
        }
    }

    /** The burp gets deeper and louder with every beer. */
    private static void reactToDrink(Player player, Level level, float perMille) {
        float volume = 0.8f + Math.min(perMille, 3f) * 0.2f;
        float pitch = Math.max(0.5f, 1.2f - perMille * 0.25f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, volume, pitch);
    }
}
