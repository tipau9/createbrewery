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

    public static final ItemEntry<Item> WEED = REGISTRATE
        .item("weed", Item::new)
        .lang("Cannabis-Blüte")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> JOINT = REGISTRATE
        .item("joint", p -> new DrugItem(p, DrugServer.Kind.WEED))
        .lang("Joint")
        .properties(p -> p.durability(DrugServer.HITS_PER_JOINT))
        .register();

    public static final ItemEntry<DrugItem> LSD = REGISTRATE
        .item("lsd", p -> new DrugItem(p, DrugServer.Kind.LSD))
        .lang("LSD-Pappe")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> MAGIC_MUSHROOM = REGISTRATE
        .item("magic_mushroom", p -> new DrugItem(p, DrugServer.Kind.SHROOMS))
        .lang("Zauberpilz")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> PEYOTE = REGISTRATE
        .item("peyote", p -> new DrugItem(p, DrugServer.Kind.MESCALINE))
        .lang("Peyote")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> DMT = REGISTRATE
        .item("dmt", p -> new DrugItem(p, DrugServer.Kind.DMT))
        .lang("DMT-Pfeife")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<DrugItem> MDMA = REGISTRATE
        .item("mdma", p -> new DrugItem(p, DrugServer.Kind.MDMA))
        .lang("Ecstasy-Pille")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> METH = REGISTRATE
        .item("meth", p -> new DrugItem(p, DrugServer.Kind.METH))
        .lang("Crystal")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> HEROIN = REGISTRATE
        .item("heroin", p -> new DrugItem(p, DrugServer.Kind.HEROIN))
        .lang("Heroin-Spritze")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<DrugItem> XANAX = REGISTRATE
        .item("xanax", p -> new DrugItem(p, DrugServer.Kind.XANAX))
        .lang("Xanax")
        .properties(p -> p.stacksTo(64))
        .register();

    /** An empty balloon, for a Spout to fill with Lachgas. */
    public static final ItemEntry<Item> BALLOON = REGISTRATE.item("balloon", Item::new)
        .lang("Luftballon")
        .register();

    public static final ItemEntry<DrugItem> LACHGAS_BALLOON = REGISTRATE
        .item("lachgas_balloon", p -> new DrugItem(p, DrugServer.Kind.LACHGAS))
        .lang("Lachgas-Ballon")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<com.createbrewery.drugs.NaloxonItem> NALOXON = REGISTRATE
        .item("naloxon", com.createbrewery.drugs.NaloxonItem::new)
        .lang("Naloxon-Nasenspray")
        .properties(p -> p.stacksTo(4))
        .register();

    public static final ItemEntry<com.createbrewery.drugs.ElectrolyteItem> ELECTROLYTE = REGISTRATE
        .item("electrolyte_drink", com.createbrewery.drugs.ElectrolyteItem::new)
        .lang("Elektrolyt-Drink")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<com.createbrewery.drugs.EdibleItem> SPACE_BROWNIE = REGISTRATE
        .item("space_brownie", com.createbrewery.drugs.EdibleItem::new)
        .lang("Space-Brownie")
        .properties(p -> p.stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder()
            .nutrition(3).saturationModifier(0.3f).alwaysEdible().build()))
        .register();

    public static void register() {}
}
