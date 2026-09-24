package com.createbrewery;

import com.createbrewery.drugs.DrugItem;
import com.createbrewery.drugs.DrugServer;
import com.createbrewery.item.BeerDrinkItem;
import com.createbrewery.item.IbuprofenItem;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;

import static com.createbrewery.ModTags.*;

public class ModItems {
    private static final CreateRegistrate REGISTRATE = CreateBrewery.REGISTRATE;

    public static final ItemEntry<Item> BARLEY = REGISTRATE.item("barley", Item::new)
        .tag(C_CROPS_BARLEY)
        .register();

    // Places ModBlocks.BARLEY_CROP, same pattern vanilla uses for wheat_seeds. The block
    // reference is resolved lazily inside this factory lambda (invoked only when the Item
    // registry event actually constructs the item, which NeoForge fires after the Block
    // registry event), so this does not create a static-initialization cycle with ModBlocks
    // even though ModBlocks.BARLEY_CROP itself needs ModItems.BARLEY_SEEDS (also resolved
    // lazily, from BarleyCropBlock#getBaseSeedId, only at runtime long after both are
    // registered).
    public static final ItemEntry<ItemNameBlockItem> BARLEY_SEEDS = REGISTRATE
        .item("barley_seeds", p -> new ItemNameBlockItem(ModBlocks.BARLEY_CROP.get(), p))
        .tag(C_SEEDS_BARLEY)
        .register();

    public static final ItemEntry<Item> GREEN_MALT = REGISTRATE.item("green_malt", Item::new).register();
    public static final ItemEntry<Item> MALT = REGISTRATE.item("malt", Item::new).register();
    public static final ItemEntry<Item> GRIST = REGISTRATE.item("grist", Item::new).register();
    public static final ItemEntry<Item> SPENT_GRAIN = REGISTRATE.item("spent_grain", Item::new).register();

    // Places ModBlocks.HOPS_CROP; hop cones are both the seed and the harvested item.
    public static final ItemEntry<ItemNameBlockItem> HOP_CONES = REGISTRATE
        .item("hop_cones", p -> new ItemNameBlockItem(ModBlocks.HOPS_CROP.get(), p))
        .tag(C_CROPS_HOPS)
        .register();

    public static final ItemEntry<Item> YEAST = REGISTRATE.item("yeast", Item::new).register();
    public static final ItemEntry<Item> EMPTY_CAN = REGISTRATE.item("empty_can", Item::new).register();

    public static final ItemEntry<BeerDrinkItem> BEER_BOTTLE = REGISTRATE
        .item("beer_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE))
        .properties(p -> p.stacksTo(16).food(ModFoods.BEER))
        .tag(BEER, BREWERY_BEER, C_BEVERAGES, C_FOODS)
        .register();

    public static final ItemEntry<BeerDrinkItem> SEALED_CAN = REGISTRATE
        .item("sealed_can", p -> new BeerDrinkItem(p, ModItems.EMPTY_CAN::get))
        .properties(p -> p.stacksTo(16).food(ModFoods.BEER))
        .tag(BEER, BREWERY_BEER, C_BEVERAGES, C_FOODS)
        .register();

    public static final ItemEntry<IbuprofenItem> IBUPROFEN = REGISTRATE
        .item("ibuprofen", IbuprofenItem::new)
        .lang("Ibu 400")
        .properties(p -> p.stacksTo(20))
        .register();

    public static final ItemEntry<DrugItem> KOKS = REGISTRATE
        .item("koks", p -> new DrugItem(p, DrugServer.Kind.COKE))
        .lang("Koks")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<DrugItem> KETA = REGISTRATE
        .item("keta", p -> new DrugItem(p, DrugServer.Kind.KETA))
        .lang("Keta")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<DrugItem> JOINT = REGISTRATE
        .item("joint", p -> new DrugItem(p, DrugServer.Kind.WEED))
        .lang("Joint")
        .properties(p -> p.durability(DrugServer.HITS_PER_JOINT))
        .register();

    public static void register() {}
}
