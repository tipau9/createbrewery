package com.createbrewery.item;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import java.util.Locale;
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

    /** Past the limit the body refuses. The state is synced, so both sides agree. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!DrunkServer.canDrink(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal(
                    "§4§lNein. §cDu kriegst keinen einzigen Tropfen mehr runter."), true);
            }
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
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

    /** The burp and a line of inner monologue, both getting worse with every beer. */
    private static void reactToDrink(Player player, Level level, float perMille) {
        String amount = String.format(Locale.GERMAN, "%.1f ‰", perMille);
        float volume;
        float pitch;
        String line;
        if (perMille < Intoxication.MERRY) {
            volume = 0.8f; pitch = 1.2f;
            line = "§e§l*ZISCH!* §aEin angenehmes Prickeln... gleich wird's lustig.";
        } else if (perMille < Intoxication.DRUNK) {
            volume = 1.0f; pitch = 0.9f;
            line = "§6§l*HICK* §eDie Welt fängt langsam an zu schaukeln...";
        } else if (perMille < Intoxication.WASTED) {
            volume = 1.2f; pitch = 0.7f;
            line = "§c§l*RÜÜÜLPS!* §6Das Bier steigt dir gehörig zu Kopf!";
        } else {
            volume = 1.4f; pitch = 0.5f;
            line = "§4§lUff... §cDas hättest du nicht mehr trinken sollen.";
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, volume, pitch);
        player.displayClientMessage(Component.literal(line + " §7(~" + amount + ")"), true);
    }
}
