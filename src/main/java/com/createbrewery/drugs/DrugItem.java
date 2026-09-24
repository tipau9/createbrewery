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
        return UseAnim.TOOT_HORN; // raised to the face
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return kind == DrugServer.Kind.WEED ? 40 : 24; // a long drag on the joint
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
        if (!level.isClientSide) {
            DrugServer.take(player, kind);
            player.getCooldowns().addCooldown(this, COOLDOWN);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }
}
