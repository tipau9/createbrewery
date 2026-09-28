package com.createbrewery;

import com.createbrewery.block.CannabisPlantBlock;
import com.createbrewery.block.CocaBushBlock;
import com.createbrewery.drugs.Purity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Koks and Keta only come off their Create lines, with a known strength; the bush is a slow crop. */
@GameTestHolder(CreateBrewery.MOD_ID)
@PrefixGameTestTemplate(false)
public class DrugLineGameTests {

    @GameTest(template = "platform")
    public static void koksAndKetaLinesLoadWithoutShortcuts(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        Object[][] steps = {
            {"drying_coca_leaf", ModItems.DRIED_COCA_LEAF.get()},
            {"milling/coca_meal", ModItems.COCA_MEAL.get()},
            {"mixing/coca_paste", ModItems.COCA_PASTE.get()},
            {"compacting/koks_brick", ModItems.KOKS_BRICK.get()},
            {"crushing/koks_from_brick", ModItems.KOKS.get()},
            {"crushing/amethyst_grit", ModItems.AMETHYST_GRIT.get()},
            {"sequenced_assembly/keta_batch", ModItems.RAW_KETA.get()},
            {"mixing/keta_crystals", ModItems.KETA_CRYSTALS.get()},
            {"milling/keta_milling", ModItems.KETA.get()},
        };
        for (Object[] step : steps) {
            RecipeHolder<?> r = recipes.byKey(CreateBrewery.ID((String) step[0])).orElse(null);
            if (r == null) helper.fail("missing recipe " + step[0]);
            Item made = r.value().getResultItem(helper.getLevel().registryAccess()).getItem();
            if (made != step[1]) helper.fail(step[0] + " makes " + made + ", not " + step[1]);
        }
        // Made on the line, the drug's strength is known - not rolled like a street deal on first use.
        for (String last : new String[] {"crushing/koks_from_brick", "milling/keta_milling"}) {
            ItemStack out = recipes.byKey(CreateBrewery.ID(last)).orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
            Purity purity = out.get(Purity.PURITY.get());
            if (purity == null || purity.strength() != 1.0f) helper.fail(last + " makes " + out + " without a full-dose purity: " + purity);
        }
        for (String old : new String[] {"koks", "keta", "mixing/raw_keta", "drying_raw_keta", "pressing/koks_brick"}) {
            if (recipes.byKey(CreateBrewery.ID(old)).isPresent()) helper.fail("old shortcut " + old + " is back");
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void cuttingKoksSpreadsOneLineOverMore(GameTestHelper helper) {
        ItemStack pure = new ItemStack(ModItems.KOKS.get());
        pure.set(Purity.PURITY.get(), new Purity(1.0f, false, true));
        ItemStack out = cut(helper, List.of(pure, new ItemStack(Items.SUGAR), new ItemStack(Items.SUGAR)));
        Purity p = out.get(Purity.PURITY.get());
        if (out.getCount() != 3 || p == null || Math.abs(p.strength() - 1f / 3) > 1e-4 || p.tested()) {
            helper.fail("1 line + 2 sugar: " + out + " " + p);
        }
        // An untested street line counts as an average one, the same on client and server.
        ItemStack street = cut(helper, List.of(new ItemStack(ModItems.KOKS.get()), new ItemStack(Items.SUGAR)));
        Purity s = street.get(Purity.PURITY.get());
        if (street.getCount() != 2 || s == null || Math.abs(s.strength() - 0.3f) > 1e-4) helper.fail("street + sugar: " + street + " " + s);
        if (!cut(helper, List.of(new ItemStack(ModItems.KOKS.get()))).isEmpty()) helper.fail("Koks alone is not a cut");
        helper.succeed();
    }

    private static ItemStack cut(GameTestHelper helper, List<ItemStack> items) {
        CraftingInput input = CraftingInput.of(2, 2, List.of(
            items.get(0), items.size() > 1 ? items.get(1) : ItemStack.EMPTY,
            items.size() > 2 ? items.get(2) : ItemStack.EMPTY, ItemStack.EMPTY));
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
            .map(r -> r.value().assemble(input, helper.getLevel().registryAccess()))
            .orElse(ItemStack.EMPTY);
    }

    @GameTest(template = "platform")
    public static void cocaBushGivesLeavesOnlyWhenGrownAndKeepsItsSeedling(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        helper.setBlock(rel.below(), Blocks.GRASS_BLOCK);
        BlockPos pos = helper.absolutePos(rel);
        BlockState young = ModBlocks.COCA_BUSH.get().defaultBlockState().setValue(CocaBushBlock.AGE, 2);
        BlockState grown = young.setValue(CocaBushBlock.AGE, CocaBushBlock.MAX_AGE);

        // What a harvester or a broken bush gets: always the seedling back (the harvester replants
        // with it), leaves only when grown.
        List<ItemStack> youngDrops = Block.getDrops(young, helper.getLevel(), pos, null);
        if (count(youngDrops, ModItems.COCA_LEAF.get()) != 0 || count(youngDrops, ModItems.COCA_SEEDLING.get()) != 1) {
            helper.fail("young bush drops " + youngDrops);
        }
        List<ItemStack> grownDrops = Block.getDrops(grown, helper.getLevel(), pos, null);
        int leaves = count(grownDrops, ModItems.COCA_LEAF.get());
        if (leaves < CocaBushBlock.MIN_LEAVES || leaves > CocaBushBlock.MAX_LEAVES || count(grownDrops, ModItems.COCA_SEEDLING.get()) != 1) {
            helper.fail("grown bush drops " + grownDrops);
        }
        if (ModItems.COCA_SEEDLING.get() != ModBlocks.COCA_BUSH.get().asItem()) helper.fail("the seedling is not the bush's item");

        // Picked by hand: leaves, and cut back to the start like a harvester leaves it.
        helper.setBlock(rel, grown);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
            new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
        if (helper.getBlockState(rel).getValue(CocaBushBlock.AGE) != 0) helper.fail("picked bush not cut back");
        helper.succeedWhen(() -> {
            int picked = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .map(ItemEntity::getItem).filter(s -> s.is(ModItems.COCA_LEAF.get())).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(picked >= CocaBushBlock.MIN_LEAVES, "picked " + picked + " leaves");
        });
    }

    @GameTest(template = "platform")
    public static void cannabisGivesBudsOnlyFromUnseededFemales(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        helper.setBlock(rel.below(), Blocks.FARMLAND);
        BlockPos pos = helper.absolutePos(rel);
        BlockState plant = ModBlocks.CANNABIS_PLANT.get().defaultBlockState();
        BlockState ripe = plant.setValue(CannabisPlantBlock.AGE, CannabisPlantBlock.MAX_AGE);
        Object[][] cases = {
            {plant.setValue(CannabisPlantBlock.AGE, 1), ModItems.HEMP_SEEDS.get(), 1, 1},
            {ripe.setValue(CannabisPlantBlock.FEMALE, true), ModItems.WET_BUDS.get(), 3, 5},
            {ripe.setValue(CannabisPlantBlock.FEMALE, true).setValue(CannabisPlantBlock.SEEDED, true), ModItems.SEEDED_BUDS.get(), 3, 5},
            {ripe.setValue(CannabisPlantBlock.FEMALE, false), ModItems.HEMP_FIBER.get(), 1, 3},
        };
        for (Object[] c : cases) {
            List<ItemStack> drops = Block.getDrops((BlockState) c[0], helper.getLevel(), pos, null);
            int n = count(drops, (Item) c[1]);
            if (n < (Integer) c[2] || n > (Integer) c[3] || drops.size() != 1) helper.fail(c[0] + " drops " + drops);
        }
        // Seeded buds are fewer and weaker; the seeds come out in the grinder.
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        float clean = strength(helper, "milling/weed_from_buds"), seeded = strength(helper, "milling/weed_from_seeded_buds");
        if (!(clean == 1.0f && seeded < clean)) helper.fail("bud strengths " + clean + " / " + seeded);
        for (String step : new String[] {"deploying/trimming_buds", "drying_buds", "milling/weed_from_trim", "roll_joint", "string_from_hemp"}) {
            if (recipes.byKey(CreateBrewery.ID(step)).isEmpty()) helper.fail("missing " + step);
        }
        for (String old : new String[] {"weed", "joint"}) {
            if (recipes.byKey(CreateBrewery.ID(old)).isPresent()) helper.fail("old shortcut " + old + " is back");
        }
        helper.succeed();
    }

    private static float strength(GameTestHelper helper, String recipe) {
        ItemStack out = helper.getLevel().getRecipeManager().byKey(CreateBrewery.ID(recipe)).orElseThrow()
            .value().getResultItem(helper.getLevel().registryAccess());
        Purity p = out.get(Purity.PURITY.get());
        return p == null ? -1 : p.strength();
    }

    @GameTest(template = "platform")
    public static void aFloweringMaleSeedsTheFemalesAroundIt(GameTestHelper helper) {
        BlockState plant = ModBlocks.CANNABIS_PLANT.get().defaultBlockState();
        BlockPos male = new BlockPos(1, 2, 1), near = new BlockPos(3, 2, 1), young = new BlockPos(1, 2, 3);
        for (BlockPos p : new BlockPos[] {male, near, young}) helper.setBlock(p.below(), Blocks.FARMLAND);
        helper.setBlock(male, plant.setValue(CannabisPlantBlock.FEMALE, false).setValue(CannabisPlantBlock.AGE, 4));
        helper.setBlock(near, plant.setValue(CannabisPlantBlock.FEMALE, true).setValue(CannabisPlantBlock.AGE, 3));
        helper.setBlock(young, plant.setValue(CannabisPlantBlock.FEMALE, true).setValue(CannabisPlantBlock.AGE, 1));
        CannabisPlantBlock.pollinate(helper.getLevel(), helper.absolutePos(male));
        if (!helper.getBlockState(near).getValue(CannabisPlantBlock.SEEDED)) helper.fail("flowering female next to a male not seeded");
        if (helper.getBlockState(young).getValue(CannabisPlantBlock.SEEDED)) helper.fail("a seedling was seeded");
        // Dry soil or a dark room: it does not grow.
        if (CannabisPlantBlock.canGrowAt(helper.getLevel(), helper.absolutePos(near))
            != (helper.getLevel().getRawBrightness(helper.absolutePos(near), 0) >= CannabisPlantBlock.MIN_LIGHT
                && helper.getBlockState(near.below()).getValue(net.minecraft.world.level.block.FarmBlock.MOISTURE) > 0)) {
            helper.fail("canGrowAt disagrees with light and water");
        }
        helper.setBlock(near.below(), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 0));
        if (CannabisPlantBlock.canGrowAt(helper.getLevel(), helper.absolutePos(near))) helper.fail("grows on dry soil");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void aJointIsAsStrongAsTheWeedInIt(GameTestHelper helper) {
        ItemStack weed = new ItemStack(ModItems.WEED.get());
        weed.set(Purity.PURITY.get(), new Purity(0.6f, false, false));
        CraftingInput input = CraftingInput.of(2, 1, List.of(weed, new ItemStack(Items.PAPER)));
        ItemStack joint = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
            .map(r -> r.value().assemble(input, helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        Purity p = joint.get(Purity.PURITY.get());
        if (!joint.is(ModItems.JOINT.get()) || p == null || p.strength() != 0.6f) helper.fail("rolled " + joint + " " + p);
        helper.succeed();
    }

    private static int count(List<ItemStack> drops, Item item) {
        return drops.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
