package com.createbrewery.item;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
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
 * A drink: beer, wine or a spirit. Drinking only puts alcohol in the stomach (see
 * {@link DrunkServer}); every symptom follows from the blood level as it is absorbed, so each sip
 * makes things worse gradually instead of flipping a switch. A bottle holds several sips (its
 * durability); each one is {@link #perSip} per mille, the last leaves the empty bottle.
 */
public class BeerDrinkItem extends Item {
    private final Supplier<? extends ItemLike> returnItemSupplier;
    /** Blood alcohol one sip brings, in per mille. */
    private final float perSip;
    /** Beer hisses as it is opened and foams; wine and spirits do neither. */
    private final boolean beer;
    /** Filled on first use, once all items are registered. */
    private static List<Item> alcoholicItems;

    public BeerDrinkItem(Properties properties, Supplier<? extends ItemLike> returnItemSupplier, float perSip, boolean beer) {
        super(properties);
        this.returnItemSupplier = returnItemSupplier;
        this.perSip = perSip;
        this.beer = beer;
    }

    /**
     * {@code sips} sips of {@code volume} litres at {@code abv} (0..1), in per mille each: Widmark
     * for an adult man, where a 0.5 l beer at 5 % (20 g of alcohol) is {@link Intoxication#PER_BEER}.
     */
    public static float perSip(float volume, float abv, int sips) {
        float grams = volume * 1000f * abv * 0.8f;
        return grams / 20f * Intoxication.PER_BEER / sips;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        if (stack.isDamageableItem()) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("createbrewery.drink.sips",
                stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
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

    /** Zisch: a beer is opened as you lift it for the first sip - unless the drink cooldown still runs. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        if (!level.isClientSide && beer && player.getItemInHand(hand).getDamageValue() == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BEER_OPEN.get(),
                SoundSource.PLAYERS, 0.8f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Not the last sip: the bottle is not used up (no food eaten), there is just less in it.
        if (entity instanceof Player player && stack.isDamageableItem() && stack.getDamageValue() + 1 < stack.getMaxDamage()) {
            drink(player, level);
            if (!player.getAbilities().instabuild) stack.setDamageValue(stack.getDamageValue() + 1);
            return stack;
        }
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (entity instanceof Player player) {
            drink(player, level);
            if (!level.isClientSide) {

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

    private void drink(Player player, Level level) {
        // Apply cooldown on BOTH client and server (like Ender Pearl) so the white
        // sweep animation displays immediately and rapid chugging is blocked.
        startCooldown(player);
        // Alcohol on top of Ibu: the stomach pays for it immediately.
        if (player.hasEffect(ModEffects.PAINKILLER)) DrunkServer.irritateStomach(player);
        if (level.isClientSide) return;
        float after = DrunkServer.state(player).total() + perSip;
        DrunkServer.drink(player, perSip);
        reactToDrink(player, level, after);
        if (beer && level instanceof ServerLevel serverLevel) {
            Vec3 look = player.getLookAngle();
            serverLevel.sendParticles(ModParticles.BEER_FOAM.get(), player.getX() + look.x * 0.35,
                player.getEyeY() - 0.2, player.getZ() + look.z * 0.35, 5, 0.08, 0.03, 0.08, 0.01);
        }
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
