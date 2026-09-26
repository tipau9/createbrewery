package com.createbrewery.drugs;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** A bag of Koks or Keta held up to the nose, or a joint to the mouth (see DrugServer). */
public class DrugItem extends Item {
    private static final int COOLDOWN = 100;
    private final DrugServer.Kind kind;

    public DrugItem(Properties properties, DrugServer.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return switch (kind) {
            case SHROOMS, MESCALINE, MDMA, XANAX -> UseAnim.EAT; // chewed, and they taste awful
            default -> UseAnim.TOOT_HORN;           // raised to the face (or a tab on the tongue)
        };
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return switch (kind) {
            case WEED -> 30;      // a long drag on the joint
            case LSD -> 16;       // a tab on the tongue
            case SHROOMS -> 32;
            case MESCALINE -> 48; // tough, bitter cactus
            case DMT -> 40;       // one deep hit, held in
            case MDMA, XANAX -> 12; // a pill, swallowed
            case HEROIN -> 36;    // finding the vein
            case LACHGAS -> 20;   // one deep breath from the balloon
            default -> 24;
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!DrugServer.enabled()) return InteractionResultHolder.fail(player.getItemInHand(hand));
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        // Advancements: before the stack shrinks, or the last dose reads as air.
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.advancements.CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
        }
        if (!level.isClientSide) {
            DrugServer.take(player, kind);
            switch (kind) {
                case COKE, KETA, METH -> DrugPose.act(player, DrugPose.SNIFF, 20);
                case WEED, DMT, LACHGAS -> DrugPose.act(player, DrugPose.SMOKE, 30);
                case HEROIN -> DrugPose.act(player, DrugPose.INJECT, 40);
                default -> {}
            }
            // A joint is smoked hit by hit: one hit off its durability, a short breath between hits.
            player.getCooldowns().addCooldown(this, kind == DrugServer.Kind.WEED ? 30 : COOLDOWN);
        }
        if (kind == DrugServer.Kind.WEED) {
            if (player.hasInfiniteMaterials()) {
                // hurtAndBreak spares creative players; a joint still burns down.
                if (!level.isClientSide) {
                    if (stack.getDamageValue() + 1 >= stack.getMaxDamage()) stack.shrink(1);
                    else stack.setDamageValue(stack.getDamageValue() + 1);
                }
            } else {
                stack.hurtAndBreak(1, entity, LivingEntity.getSlotForHand(entity.getUsedItemHand()));
            }
        } else if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }
}
