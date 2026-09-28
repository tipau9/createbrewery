package com.createbrewery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A spyglass with its lens taped by a club bouncer.
 * Sneak + right-click peels the sticker off.
 */
public class TapedSpyglassItem extends Item {
    public TapedSpyglassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.3f);
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("§7[Club] Sticker abgezogen. Die Linse ist wieder frei."), true);
                ItemStack normal = new ItemStack(Items.SPYGLASS);
                if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                    normal.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
                }
                return InteractionResultHolder.sidedSuccess(normal, level.isClientSide);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8f, 0.8f);
        if (level.isClientSide) {
            player.displayClientMessage(Component.literal("§c[!] Die Linse ist abgeklebt! Keine Fotos im Club."), true);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Mit einem Club-Sticker abgeklebt.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Keine Fotos im Club!").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Sneak + Rechtsklick zum Abziehen.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
