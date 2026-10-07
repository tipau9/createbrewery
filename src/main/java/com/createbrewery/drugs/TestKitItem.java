package com.createbrewery.drugs;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A reagent test kit (like Marquis and fentanyl strips): hold a drug in the other hand and use it.
 * Shows how strong that batch is and whether it holds fentanyl, and marks the stack as tested.
 */
public class TestKitItem extends Item {
    public TestKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack kit = player.getItemInHand(hand);
        ItemStack drug = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(drug.getItem() instanceof DrugItem item)) {
            if (!level.isClientSide) DrugServer.think(player, "createbrewery.thought.test_kit.offhand", 0xA0A0A0);
            return InteractionResultHolder.fail(kit);
        }
        if (!level.isClientSide) {
            Purity purity = Purity.of(drug, item.kind(), player.getRandom());
            drug.set(Purity.PURITY.get(), new Purity(purity.strength(), purity.fentanyl(), true));
            player.displayClientMessage(result(purity), true);
            kit.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(kit, level.isClientSide);
    }

    /** The reading, as the test kit shows it and the tooltip repeats it. */
    static Component result(Purity purity) {
        if (purity.fentanyl()) return Component.translatable("createbrewery.test_kit.fentanyl").withColor(0xFF3030);
        int percent = Math.round(purity.strength() * 100f);
        String verdict = purity.strength() < 0.6f ? "heavily_cut" : purity.strength() < 0.9f ? "cut"
            : purity.strength() <= 1.2f ? "normal" : "very_strong";
        int colour = purity.strength() < 0.9f ? 0xC8C8A0 : purity.strength() <= 1.2f ? 0x80E080 : 0xFFA030;
        return Component.translatable("createbrewery.test_kit.result", percent, Component.translatable("createbrewery.test_kit." + verdict)).withColor(colour);
    }
}
