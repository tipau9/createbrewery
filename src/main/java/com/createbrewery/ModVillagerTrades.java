package com.createbrewery;

import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;

/**
 * Task-8 Step 2 (trade half): a level-2 (Apprentice) Farmer trade selling hop cones for
 * emeralds. {@code VillagerTradesEvent} is a plain (non-mod-bus) NeoForge event — confirmed via
 * javap on neoforge-21.1.228-universal.jar, it does not implement {@code IModBusEvent} — so it
 * is registered on {@code NeoForge.EVENT_BUS}, not the mod event bus. {@code BasicItemListing}
 * (net.neoforged.neoforge.common) is NeoForge's own ready-made "N emeralds for an item"
 * {@code VillagerTrades.ItemListing}, used here instead of a hand-rolled one.
 */
public class ModVillagerTrades {
    private static final int EMERALD_COST = 1;
    private static final int HOP_CONES_FOR_SALE = 3;
    private static final int MAX_TRADES = 12;
    private static final int XP = 2;
    private static final int FARMER_TRADE_LEVEL = 2;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModVillagerTrades::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(ModVillagerTrades::onWandererTrades);
    }

    /** The wandering trader deals on the side: now and then he has something from the Apotheke. */
    private static void onWandererTrades(net.neoforged.neoforge.event.village.WandererTradesEvent event) {
        List<VillagerTrades.ItemListing> rare = event.getRareTrades();
        rare.add(new BasicItemListing(6, new ItemStack(ModItems.LSD.get(), 3), 4, 1));
        rare.add(new BasicItemListing(4, new ItemStack(ModItems.MAGIC_MUSHROOM.get(), 4), 4, 1));
        rare.add(new BasicItemListing(5, new ItemStack(ModItems.MDMA.get(), 2), 4, 1));
        rare.add(new BasicItemListing(10, new ItemStack(ModItems.DMT.get()), 2, 1));
        rare.add(new BasicItemListing(8, new ItemStack(ModItems.HEROIN.get()), 2, 1));
    }

    private static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.FARMER) {
            return;
        }
        List<VillagerTrades.ItemListing> trades = event.getTrades().get(FARMER_TRADE_LEVEL);
        trades.add(new BasicItemListing(
            EMERALD_COST,
            new ItemStack(ModItems.HOP_CONES.get(), HOP_CONES_FOR_SALE),
            MAX_TRADES,
            XP));
    }
}
