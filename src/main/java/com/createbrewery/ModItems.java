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
        .item("beer_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE, BeerDrinkItem.perSip(0.5f, 0.05f, 3), true))
        .properties(p -> p.durability(3).setNoRepair().food(ModFoods.BEER))
        .tag(BEER, BREWERY_BEER, C_BEVERAGES, C_FOODS)
        .register();

    public static final ItemEntry<BeerDrinkItem> SEALED_CAN = REGISTRATE
        .item("sealed_can", p -> new BeerDrinkItem(p, ModItems.EMPTY_CAN::get, BeerDrinkItem.perSip(0.5f, 0.05f, 3), true))
        .properties(p -> p.durability(3).setNoRepair().food(ModFoods.BEER))
        .tag(BEER, BREWERY_BEER, C_BEVERAGES, C_FOODS)
        .register();

    public static final ItemEntry<IbuprofenItem> IBUPROFEN = REGISTRATE
        .item("ibuprofen", IbuprofenItem::new)
        .lang("Ibuprofen 400")
        .properties(p -> p.stacksTo(20))
        .register();

    public static final ItemEntry<DrugItem> KOKS = REGISTRATE
        .item("koks", p -> new DrugItem(p, DrugServer.Kind.COKE))
        .lang("Cocaine")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<DrugItem> KETA = REGISTRATE
        .item("keta", p -> new DrugItem(p, DrugServer.Kind.KETA))
        .lang("Ketamine")
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
        .lang("Coca Seedling")
        .register();

    public static final ItemEntry<Item> COCA_LEAF = REGISTRATE
        .item("coca_leaf", Item::new)
        .lang("Coca Leaves")
        .register();

    public static final ItemEntry<Item> DRIED_COCA_LEAF = REGISTRATE
        .item("dried_coca_leaf", Item::new)
        .lang("Dried Coca Leaves")
        .register();

    public static final ItemEntry<Item> COCA_MEAL = REGISTRATE
        .item("coca_meal", Item::new)
        .lang("Coca Flour")
        .register();

    public static final ItemEntry<Item> COCA_PASTE = REGISTRATE
        .item("coca_paste", Item::new)
        .lang("Coca Paste")
        .register();

    public static final ItemEntry<Item> KOKS_BRICK = REGISTRATE
        .item("koks_brick", Item::new)
        .lang("Cocaine Brick")
        .register();

    public static final ItemEntry<Item> AMETHYST_GRIT = REGISTRATE
        .item("amethyst_grit", Item::new)
        .lang("Amethyst Grit")
        .register();

    /** The Keta batch half-way through the precision lab (sequenced assembly). */
    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem> KETA_BATCH = REGISTRATE
        .item("keta_batch", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .lang("Unfinished Ketamine Batch")
        .register();

    public static final ItemEntry<Item> RUINED_BATCH = REGISTRATE
        .item("ruined_batch", Item::new)
        .lang("Ruined Batch")
        .register();

    public static final ItemEntry<Item> RAW_KETA = REGISTRATE
        .item("raw_keta", Item::new)
        .lang("Raw Ketamine Crystals")
        .register();

    public static final ItemEntry<Item> KETA_CRYSTALS = REGISTRATE
        .item("keta_crystals", Item::new)
        .lang("Ketamine Crystals")
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
        .lang("Hemp Seeds")
        .register();

    public static final ItemEntry<Item> WET_BUDS = REGISTRATE
        .item("wet_buds", Item::new)
        .lang("Fresh Cannabis Buds")
        .register();

    public static final ItemEntry<Item> SEEDED_BUDS = REGISTRATE
        .item("seeded_buds", Item::new)
        .lang("Seeded Cannabis Buds")
        .register();

    public static final ItemEntry<Item> TRIMMED_BUDS = REGISTRATE
        .item("trimmed_buds", Item::new)
        .lang("Trimmed Cannabis Buds")
        .register();

    public static final ItemEntry<Item> DRIED_BUDS = REGISTRATE
        .item("dried_buds", Item::new)
        .lang("Dried Cannabis Buds")
        .register();

    public static final ItemEntry<Item> WEED_TRIM = REGISTRATE
        .item("weed_trim", Item::new)
        .lang("Cannabis Trim")
        .register();

    public static final ItemEntry<Item> HEMP_FIBER = REGISTRATE
        .item("hemp_fiber", Item::new)
        .lang("Hemp Fibers")
        .register();

    public static final ItemEntry<Item> WEED = REGISTRATE
        .item("weed", Item::new)
        .lang("Cannabis Bud")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> JOINT = REGISTRATE
        .item("joint", p -> new DrugItem(p, DrugServer.Kind.WEED))
        .lang("Joint")
        .properties(p -> p.durability(DrugServer.HITS_PER_JOINT))
        .register();

    public static final ItemEntry<DrugItem> LSD = REGISTRATE
        .item("lsd", p -> new DrugItem(p, DrugServer.Kind.LSD))
        .lang("LSD Blotter")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> MAGIC_MUSHROOM = REGISTRATE
        .item("magic_mushroom", p -> new DrugItem(p, DrugServer.Kind.SHROOMS))
        .lang("Magic Mushroom")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> PEYOTE = REGISTRATE
        .item("peyote", p -> new DrugItem(p, DrugServer.Kind.MESCALINE))
        .lang("Peyote")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> DMT = REGISTRATE
        .item("dmt", p -> new DrugItem(p, DrugServer.Kind.DMT))
        .lang("DMT Pipe")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<DrugItem> MDMA = REGISTRATE
        .item("mdma", p -> new DrugItem(p, DrugServer.Kind.MDMA))
        .lang("Ecstasy Pill")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> METH = REGISTRATE
        .item("meth", p -> new DrugItem(p, DrugServer.Kind.METH))
        .lang("Crystal Meth")
        .properties(p -> p.stacksTo(64))
        .register();

    public static final ItemEntry<DrugItem> HEROIN = REGISTRATE
        .item("heroin", p -> new DrugItem(p, DrugServer.Kind.HEROIN))
        .lang("Heroin Syringe")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<DrugItem> XANAX = REGISTRATE
        .item("xanax", p -> new DrugItem(p, DrugServer.Kind.XANAX))
        .lang("Xanax")
        .properties(p -> p.stacksTo(64))
        .register();

    // The other drugs' grows and lines (see ModRecipeProvider): made-up lab steps, nothing like a real process.
    public static final ItemEntry<ItemNameBlockItem> MUSHROOM_SPORES = REGISTRATE
        .<ItemNameBlockItem>item("mushroom_spores", p -> planted(ModBlocks.PSILOCYBE.get(), p, "mushroom_spores"))
        .lang("Mushroom Spores")
        .register();

    public static final ItemEntry<Item> FRESH_MUSHROOMS = REGISTRATE
        .item("fresh_mushrooms", Item::new)
        .lang("Fresh Magic Mushrooms")
        .register();

    public static final ItemEntry<ItemNameBlockItem> PEYOTE_PUP = REGISTRATE
        .<ItemNameBlockItem>item("peyote_pup", p -> planted(ModBlocks.PEYOTE_CACTUS.get(), p, "peyote_pup"))
        .lang("Peyote Pup")
        .register();

    public static final ItemEntry<Item> PEYOTE_BUTTON = REGISTRATE
        .item("peyote_button", Item::new)
        .lang("Fresh Peyote Button")
        .register();

    public static final ItemEntry<ItemNameBlockItem> OPIUM_POPPY_SEEDS = REGISTRATE
        .<ItemNameBlockItem>item("opium_poppy_seeds", p -> planted(ModBlocks.OPIUM_POPPY.get(), p, "opium_poppy_seeds"))
        .lang("Opium Poppy Seeds")
        .register();

    public static final ItemEntry<Item> POPPY_POD = REGISTRATE
        .item("poppy_pod", Item::new)
        .lang("Poppy Pod")
        .register();

    public static final ItemEntry<Item> OPIUM_LATEX = REGISTRATE
        .item("opium_latex", Item::new)
        .lang("Poppy Latex")
        .register();

    public static final ItemEntry<Item> RAW_OPIUM = REGISTRATE
        .item("raw_opium", Item::new)
        .lang("Raw Opium")
        .register();

    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem> HEROIN_BATCH = REGISTRATE
        .item("heroin_batch", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .lang("Unfinished Heroin Batch")
        .register();

    public static final ItemEntry<Item> RAW_HEROIN = REGISTRATE
        .item("raw_heroin", Item::new)
        .lang("Raw Heroin")
        .register();

    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem> METH_BATCH = REGISTRATE
        .item("meth_batch", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .lang("Unfinished Meth Batch")
        .register();

    public static final ItemEntry<Item> RAW_METH = REGISTRATE
        .item("raw_meth", Item::new)
        .lang("Raw Meth")
        .register();

    public static final ItemEntry<Item> METH_CRYSTALS = REGISTRATE
        .item("meth_crystals", Item::new)
        .lang("Meth Chunks")
        .register();

    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem> MDMA_BATCH = REGISTRATE
        .item("mdma_batch", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .lang("Unfinished MDMA Batch")
        .register();

    public static final ItemEntry<Item> MDMA_CRYSTALS = REGISTRATE
        .item("mdma_crystals", Item::new)
        .lang("MDMA Crystals")
        .register();

    public static final ItemEntry<Item> ERGOT = REGISTRATE
        .item("ergot", Item::new)
        .lang("Ergot")
        .register();

    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem> LSD_BATCH = REGISTRATE
        .item("lsd_batch", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .lang("Unfinished LSD Batch")
        .register();

    public static final ItemEntry<Item> LSD_SOLUTION = REGISTRATE
        .item("lsd_solution", Item::new)
        .lang("LSD Solution")
        .register();

    public static final ItemEntry<Item> BLOTTER_SHEET = REGISTRATE
        .item("blotter_sheet", Item::new)
        .lang("LSD Blotter Sheet")
        .register();

    public static final ItemEntry<Item> ROOT_BARK = REGISTRATE
        .item("root_bark", Item::new)
        .lang("Root Bark")
        .register();

    public static final ItemEntry<Item> XANAX_POWDER = REGISTRATE
        .item("xanax_powder", Item::new)
        .lang("Xanax Powder")
        .register();

    // Wine and spirits. Each bottle holds sips (durability), see BeerDrinkItem.
    public static final ItemEntry<BeerDrinkItem> WINE_BOTTLE = REGISTRATE
        .item("wine_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE, BeerDrinkItem.perSip(0.75f, 0.12f, 5), false))
        .lang("Bottle of Red Wine")
        .properties(p -> p.durability(5).setNoRepair().food(ModFoods.BEER))
        .tag(C_BEVERAGES)
        .register();

    public static final ItemEntry<BeerDrinkItem> COGNAC_BOTTLE = REGISTRATE
        .item("cognac_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE, BeerDrinkItem.perSip(0.7f, 0.4f, 16), false))
        .lang("Cognac")
        .properties(p -> p.durability(16).setNoRepair().food(ModFoods.BEER))
        .tag(C_BEVERAGES)
        .register();

    public static final ItemEntry<BeerDrinkItem> WHISKEY_BOTTLE = REGISTRATE
        .item("whiskey_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE, BeerDrinkItem.perSip(0.7f, 0.4f, 16), false))
        .lang("Whiskey")
        .properties(p -> p.durability(16).setNoRepair().food(ModFoods.BEER))
        .tag(C_BEVERAGES)
        .register();

    public static final ItemEntry<BeerDrinkItem> DOPPELKORN_BOTTLE = REGISTRATE
        .item("doppelkorn_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE, BeerDrinkItem.perSip(0.7f, 0.38f, 16), false))
        .lang("Doppelkorn")
        .properties(p -> p.durability(16).setNoRepair().food(ModFoods.BEER))
        .tag(C_BEVERAGES)
        .register();

    public static final ItemEntry<BeerDrinkItem> VODKA_BOTTLE = REGISTRATE
        .item("vodka_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE, BeerDrinkItem.perSip(0.7f, 0.4f, 16), false))
        .lang("Vodka")
        .properties(p -> p.durability(16).setNoRepair().food(ModFoods.BEER))
        .tag(C_BEVERAGES)
        .register();

    public static final ItemEntry<BeerDrinkItem> GIN_BOTTLE = REGISTRATE
        .item("gin_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE, BeerDrinkItem.perSip(0.7f, 0.4f, 16), false))
        .lang("Gin")
        .properties(p -> p.durability(16).setNoRepair().food(ModFoods.BEER))
        .tag(C_BEVERAGES)
        .register();

    public static final ItemEntry<BeerDrinkItem> RUM_BOTTLE = REGISTRATE
        .item("rum_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE, BeerDrinkItem.perSip(0.7f, 0.4f, 16), false))
        .lang("Rum")
        .properties(p -> p.durability(16).setNoRepair().food(ModFoods.BEER))
        .tag(C_BEVERAGES)
        .register();

    public static final ItemEntry<BeerDrinkItem> TEQUILA_BOTTLE = REGISTRATE
        .item("tequila_bottle", p -> new BeerDrinkItem(p, () -> Items.GLASS_BOTTLE, BeerDrinkItem.perSip(0.7f, 0.38f, 16), false))
        .lang("Tequila")
        .properties(p -> p.durability(16).setNoRepair().food(ModFoods.BEER))
        .tag(C_BEVERAGES)
        .register();

    public static final ItemEntry<ItemNameBlockItem> GRAPE_CUTTING = REGISTRATE
        .<ItemNameBlockItem>item("grape_cutting", p -> planted(ModBlocks.GRAPE_VINE.get(), p, "grape_cutting"))
        .lang("Grapevine Cutting")
        .register();

    public static final ItemEntry<Item> GRAPES = REGISTRATE
        .item("grapes", Item::new)
        .lang("Grapes")
        .properties(p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).build()))
        .register();

    public static final ItemEntry<ItemNameBlockItem> AGAVE_PUP = REGISTRATE
        .<ItemNameBlockItem>item("agave_pup", p -> planted(ModBlocks.AGAVE.get(), p, "agave_pup"))
        .lang("Agave Pup")
        .register();

    public static final ItemEntry<Item> AGAVE_HEART = REGISTRATE
        .item("agave_heart", Item::new)
        .lang("Agave Piña")
        .register();

    public static final ItemEntry<Item> ROASTED_AGAVE = REGISTRATE
        .item("roasted_agave", Item::new)
        .lang("Roasted Agave Piña")
        .register();

    public static final ItemEntry<Item> AGAVE_PULP = REGISTRATE
        .item("agave_pulp", Item::new)
        .lang("Agave Fibers")
        .register();

    public static final ItemEntry<Item> JUNIPER_BERRIES = REGISTRATE
        .item("juniper_berries", Item::new)
        .lang("Juniper Berries")
        .register();

    /** An empty balloon, for a Spout to fill with Lachgas. */
    public static final ItemEntry<Item> BALLOON = REGISTRATE.item("balloon", Item::new)
        .lang("Balloon")
        .register();

    public static final ItemEntry<DrugItem> LACHGAS_BALLOON = REGISTRATE
        .item("lachgas_balloon", p -> new DrugItem(p, DrugServer.Kind.LACHGAS))
        .lang("Nitrous Balloon")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<com.createbrewery.drugs.NaloxonItem> NALOXON = REGISTRATE
        .item("naloxon", com.createbrewery.drugs.NaloxonItem::new)
        .lang("Narcan (Naloxone)")
        .properties(p -> p.stacksTo(4))
        .register();

    public static final ItemEntry<com.createbrewery.drugs.TestKitItem> TEST_KIT = REGISTRATE
        .item("drug_test_kit", com.createbrewery.drugs.TestKitItem::new)
        .lang("Drug Test Kit")
        .properties(p -> p.durability(10))
        .register();

    public static final ItemEntry<com.createbrewery.drugs.ElectrolyteItem> ELECTROLYTE = REGISTRATE
        .item("electrolyte_drink", com.createbrewery.drugs.ElectrolyteItem::new)
        .lang("Electrolyte Drink")
        .properties(p -> p.stacksTo(16))
        .register();

    public static final ItemEntry<com.createbrewery.drugs.EdibleItem> SPACE_BROWNIE = REGISTRATE
        .item("space_brownie", com.createbrewery.drugs.EdibleItem::new)
        .lang("Space Brownie")
        .properties(p -> p.stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder()
            .nutrition(3).saturationModifier(0.3f).alwaysEdible().build()))
        .register();

    public static final ItemEntry<com.createbrewery.item.TapedSpyglassItem> TAPED_SPYGLASS = REGISTRATE
        .item("taped_spyglass", com.createbrewery.item.TapedSpyglassItem::new)
        .lang("Taped Spyglass")
        .properties(p -> p.stacksTo(1))
        .model((c, p) -> {}) // hand-written: models/item/taped_spyglass.json
        .register();

    public static final ItemEntry<com.createbrewery.item.ClubStampItem> CLUB_STAMP = REGISTRATE
        .item("club_stamp", com.createbrewery.item.ClubStampItem::new)
        .lang("Club Stamp")
        .properties(p -> p.stacksTo(1))
        .model((c, p) -> {}) // hand-written: models/item/club_stamp.json
        .register();

    public static final ItemEntry<net.minecraft.world.item.SpawnEggItem> BOUNCER_SPAWN_EGG = REGISTRATE
        .<net.minecraft.world.item.SpawnEggItem>item("bouncer_spawn_egg", p -> new net.minecraft.world.item.SpawnEggItem(
            com.createbrewery.entity.ModEntities.BOUNCER.get(), 0x1A1A1A, 0xC8A030, p))
        .lang("Bouncer Spawn Egg")
        .model((c, p) -> {}) // hand-written: models/item/bouncer_spawn_egg.json
        .register();

    /** A seed-like item that plants {@code block}, with a grow hint under its name. */
    private static ItemNameBlockItem planted(net.minecraft.world.level.block.Block block, Item.Properties p, String name) {
        return new ItemNameBlockItem(block, p) {
            @Override
            public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
                                        java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                tooltip.add(net.minecraft.network.chat.Component.translatable("item.createbrewery." + name + ".hint")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        };
    }

    public static void register() {}
}
