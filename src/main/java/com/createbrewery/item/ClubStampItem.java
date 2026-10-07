package com.createbrewery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Club stamp used to stamp players for re-entry.
 */
public class ClubStampItem extends Item {
    public ClubStampItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof Player targetPlayer) {
            if (targetPlayer.level().isClientSide) return InteractionResult.SUCCESS;
            targetPlayer.getPersistentData().putLong("createbrewery:club_stamp", targetPlayer.level().getGameTime() + 24000L);
            targetPlayer.level().playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.0f, 1.4f);
            if (!targetPlayer.level().isClientSide) {
                targetPlayer.displayClientMessage(Component.translatable("createbrewery.club.stamped"), false);
                player.displayClientMessage(Component.translatable("createbrewery.club.stamped_other", targetPlayer.getName()), true);
            }
            return InteractionResult.sidedSuccess(targetPlayer.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("createbrewery.club_stamp.tooltip.0").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("createbrewery.club_stamp.tooltip.1").withStyle(ChatFormatting.GRAY));
    }
}
