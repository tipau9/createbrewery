package com.createbrewery.item;

import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

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

        if (entity instanceof Player player) {
            // Hilarious deep burp
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.2f, 0.55f);

            // Stumble: push player in a random sideways direction
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            Vec3 currentMovement = player.getDeltaMovement();
            player.setDeltaMovement(currentMovement.add(Math.cos(angle) * 0.4, 0.12, Math.sin(angle) * 0.4));
            player.hurtMarked = true;

            // Funny drunk/hangover messages
            if (!level.isClientSide) {
                String[] quotes = {
                    "\u00a7c\u00a7l*R\u00dc\u00dc\u00dcLPS*... \u00a76Uff, wer hat die Welt gedreht?!",
                    "\u00a7c\u00a7lFilmriss! \u00a76Deine Beine gehorchen dir nicht mehr...",
                    "\u00a74\u00a7lKater des Todes: \u00a76Dein Sch\u00e4del h\u00e4mmert wie eine Create-Presse!",
                    "\u00a7c\u00a7lUnerwarteter Seegang: \u00a76Der Boden schwankt verd\u00e4chtig!",
                    "\u00a7e\u00a7lBierchen zischt! \u00a7c...aber dein Magen rebelliert sofort.",
                    "\u00a76\u00a7lSelbst\u00fcbersch\u00e4tzung: \u00a7cDu stolperst \u00fcber deine eigenen F\u00fc\u00dfe!"
                };
                String quote = quotes[player.getRandom().nextInt(quotes.length)];
                player.displayClientMessage(Component.literal(quote), true);
            }

            // Return empty container if in survival
            if (!player.getAbilities().instabuild && returnItemSupplier != null) {
                ItemLike returnItem = returnItemSupplier.get();
                if (returnItem != null) {
                    ItemStack returnStack = new ItemStack(returnItem);
                    if (stack.isEmpty()) {
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
}
