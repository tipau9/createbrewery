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

    // Koks and Keta come off Create lines (see ModRecipeProvider): made-up steps, nothing like a real process.
    public static final ItemEntry<net.minecraft.world.item.ItemNameBlockItem> COCA_SEEDLING = REGISTRATE
        .<net.minecraft.world.item.ItemNameBlockItem>item("coca_seedling", p -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.COCA_BUSH.get(), p) {
            @Override
            public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
                                        java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                tooltip.add(net.minecraft.network.chat.Component.translatable("item.createbrewery.coca_seedling.hint")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        })
        .lang("Kokasetzling")
        .register();

    public static final ItemEntry<Item> COCA_LEAF = REGISTRATE
        .item("coca_leaf", Item::new)
        .lang("Kokablätter")
        .register();

    public static final ItemEntry<Item> DRIED_COCA_LEAF = REGISTRATE
        .item("dried_coca_leaf", Item::new)
        .lang("Getrocknete Kokablätter")
        .register();

    public static final ItemEntry<Item> COCA_MEAL = REGISTRATE
        .item("coca_meal", Item::new)
        .lang("Kokamehl")
        .register();

    public static final ItemEntry<Item> COCA_PASTE = REGISTRATE
        .item("coca_paste", Item::new)
        .lang("Kokapaste")
        .register();

    public static final ItemEntry<Item> KOKS_BRICK = REGISTRATE
        .item("koks_brick", Item::new)
        .lang("Koks-Ziegel")
        .register();

    public static final ItemEntry<Item> AMETHYST_GRIT = REGISTRATE
        .item("amethyst_grit", Item::new)
        .lang("Amethystgrieß")
        .register();

    /** The Keta batch half-way through the precision lab (sequenced assembly). */
    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem> KETA_BATCH = REGISTRATE
        .item("keta_batch", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .lang("Unfertige Keta-Charge")
        .register();

    public static final ItemEntry<Item> RUINED_BATCH = REGISTRATE
        .item("ruined_batch", Item::new)
        .lang("Verdorbene Charge")
        .register();

    public static final ItemEntry<Item> RAW_KETA = REGISTRATE
        .item("raw_keta", Item::new)
        .lang("Keta-Rohkristalle")
        .register();

    public static final ItemEntry<Item> KETA_CRYSTALS = REGISTRATE
        .item("keta_crystals", Item::new)
        .lang("Keta-Kristalle")
        .register();

    // The grow (see CannabisPlantBlock): seeds, the harvest and the steps to a smokable bud.
    public static final ItemEntry<net.minecraft.world.item.ItemNameBlockItem> HEMP_SEEDS = REGISTRATE
        .<net.minecraft.world.item.ItemNameBlockItem>item("hemp_seeds", p -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.CANNABIS_PLANT.get(), p) {
            @Override
            public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
                                        java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                tooltip.add(net.minecraft.network.chat.Component.translatable("item.createbrewery.hemp_seeds.hint")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        })
        .lang("Hanfsamen")
        .register();

    public static final ItemEntry<Item> WET_BUDS = REGISTRATE
        .item("wet_buds", Item::new)
        .lang("Frische Blüten")
        .register();

    public static final ItemEntry<Item> SEEDED_BUDS = REGISTRATE
        .item("seeded_buds", Item::new)
        .lang("Samige Blüten")
        .register();

    public static final ItemEntry<Item> TRIMMED_BUDS = REGISTRATE
        .item("trimmed_buds", Item::new)
        .lang("Getrimmte Blüten")
        .register();

    public static final ItemEntry<Item> DRIED_BUDS = REGISTRATE
        .item("dried_buds", Item::new)
        .lang("Getrocknete Blüten")
        .register();

    public static final ItemEntry<Item> WEED_TRIM = REGISTRATE
        .item("weed_trim", Item::new)
        .lang("Verschnitt")
        .register();

    public static final ItemEntry<Item> HEMP_FIBER = REGISTRATE
        .item("hemp_fiber", Item::new)
        .lang("Hanffasern")
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
        .lang("Narcan (Naloxon)")
        .properties(p -> p.stacksTo(4))
        .register();

    public static final ItemEntry<com.createbrewery.drugs.TestKitItem> TEST_KIT = REGISTRATE
        .item("drug_test_kit", com.createbrewery.drugs.TestKitItem::new)
        .lang("Drogentest-Kit")
        .properties(p -> p.durability(10))
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

    public static final ItemEntry<com.createbrewery.item.TapedSpyglassItem> TAPED_SPYGLASS = REGISTRATE
        .item("taped_spyglass", com.createbrewery.item.TapedSpyglassItem::new)
        .lang("Abgeklebtes Fernglas")
        .properties(p -> p.stacksTo(1))
        .model((c, p) -> {}) // hand-written: models/item/taped_spyglass.json
        .register();

    public static final ItemEntry<com.createbrewery.item.ClubStampItem> CLUB_STAMP = REGISTRATE
        .item("club_stamp", com.createbrewery.item.ClubStampItem::new)
        .lang("Club-Stempel")
        .properties(p -> p.stacksTo(1))
        .model((c, p) -> {}) // hand-written: models/item/club_stamp.json
        .register();

    public static final ItemEntry<net.minecraft.world.item.SpawnEggItem> BOUNCER_SPAWN_EGG = REGISTRATE
        .<net.minecraft.world.item.SpawnEggItem>item("bouncer_spawn_egg", p -> new net.minecraft.world.item.SpawnEggItem(
            com.createbrewery.entity.ModEntities.BOUNCER.get(), 0x1A1A1A, 0xC8A030, p))
        .lang("Türsteher Spawn-Ei")
        .model((c, p) -> {}) // hand-written: models/item/bouncer_spawn_egg.json
        .register();

    public static void register() {}
}
