package com.createbrewery;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

public class ModItems {
    private static final CreateRegistrate REGISTRATE = CreateBrewery.REGISTRATE;

    public static final ItemEntry<Item> BARLEY = REGISTRATE.item("barley", Item::new).register();
    public static final ItemEntry<Item> BARLEY_SEEDS = REGISTRATE.item("barley_seeds", Item::new).register();
    public static final ItemEntry<Item> GREEN_MALT = REGISTRATE.item("green_malt", Item::new).register();
    public static final ItemEntry<Item> MALT = REGISTRATE.item("malt", Item::new).register();
    public static final ItemEntry<Item> GRIST = REGISTRATE.item("grist", Item::new).register();
    public static final ItemEntry<Item> SPENT_GRAIN = REGISTRATE.item("spent_grain", Item::new).register();
    public static final ItemEntry<Item> HOP_CONES = REGISTRATE.item("hop_cones", Item::new).register();
    public static final ItemEntry<Item> YEAST = REGISTRATE.item("yeast", Item::new).register();
    public static final ItemEntry<Item> EMPTY_CAN = REGISTRATE.item("empty_can", Item::new).register();

    public static final ItemEntry<Item> BEER_BOTTLE = REGISTRATE
        .item("beer_bottle", Item::new)
        .properties(p -> p.stacksTo(16).food(ModFoods.BEER))
        .register();

    public static final ItemEntry<Item> SEALED_CAN = REGISTRATE
        .item("sealed_can", Item::new)
        .properties(p -> p.stacksTo(16).food(ModFoods.BEER))
        .register();

    public static void register() {}
}
