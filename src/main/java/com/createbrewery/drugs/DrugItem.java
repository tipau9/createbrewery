package com.createbrewery.drugs;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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

    public DrugServer.Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        if (kind == DrugServer.Kind.LACHGAS) return;
        Purity purity = stack.get(Purity.PURITY.get());
        tooltip.add(purity != null && purity.tested() ? TestKitItem.result(purity)
            : Component.literal("Ungetestet - was drin ist, weiß keiner").withStyle(ChatFormatting.GRAY));
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
            // A street batch: cut, normal or strong (any number of doses), maybe laced with fentanyl.
            Purity purity = Purity.of(stack, kind, player.getRandom());
            int doses = Pharmacology.doses(purity.strength(), player.getRandom().nextFloat());
            // A fake Xanax bar is only the fentanyl; laced heroin is both.
            if (!(purity.fentanyl() && kind == DrugServer.Kind.XANAX)) {
                for (int i = 0; i < doses; i++) DrugServer.take(player, kind);
                if (doses == 0 && kind != DrugServer.Kind.WEED) DrugServer.think(player, "Gestreckt… das merk ich kaum.", 0xA0A0A0);
            }
            if (purity.fentanyl()) Opioids.fentanyl(player);
            switch (kind) {
                case COKE, KETA, METH -> DrugPose.act(player, DrugPose.SNIFF, 100); // phone, card, straw: five seconds
                case WEED -> DrugPose.act(player, DrugPose.SMOKE, 30);
                case DMT, LACHGAS -> DrugPose.act(player, DrugPose.INHALE, 30);
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
