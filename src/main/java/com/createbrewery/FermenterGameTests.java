package com.createbrewery;

import com.createbrewery.block.entity.FermenterBlockEntity;
import com.createbrewery.recipe.FermentingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Each of these pins a specific way the Fermenter could lose a day-long batch.
 *
 * They deliberately do not use bare {@code succeedWhen}: most of the conditions worth
 * asserting ("the input is still there", "the output is empty") are already true on tick
 * zero, so a test written that way would pass without the code under test ever running.
 * Every test instead asserts the batch actually started first, and only then checks the
 * transition it cares about.
 */
@GameTestHolder(CreateBrewery.MOD_ID)
@PrefixGameTestTemplate(false)
public class FermenterGameTests {

    private static final String TEMPLATE = "platform";
    private static final BlockPos FERMENTER_POS = new BlockPos(2, 1, 2);

    /** Far more than any plausible scaled duration, and never equal to NOT_STARTED. */
    private static final long BACKDATE = 100_000_000L;

    private static final int BATCH = 500;

    // ---------------------------------------------------------------- helpers

    // getSource(), not get(): a Registrate FluidEntry resolves to the FLOWING fluid, and a
    // tank holding the flowing variant matches no recipe that names the source fluid.
    private static Fluid hoppedWortFluid() {
        return ModFluids.HOPPED_WORT.getSource();
    }

    private static Fluid beerFluid() {
        return ModFluids.BEER.getSource();
    }

    private static FluidStack hoppedWort(int amount) {
        return new FluidStack(hoppedWortFluid(), amount);
    }

    private static FluidStack beer(int amount) {
        return new FluidStack(beerFluid(), amount);
    }

    /**
     * Places a Fermenter, charges it with wort and yeast, and fails loudly if no fermenting
     * recipe is loaded at all — otherwise a datapack error reads as a block entity bug.
     */
    private static FermenterBlockEntity loadedFermenter(GameTestHelper helper) {
        if (helper.getLevel().getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.FERMENTING.<RecipeInput, FermentingRecipe>getType())
                .isEmpty()) {
            helper.fail("no createbrewery:fermenting recipes loaded - check the recipe JSON parsed");
        }

        helper.setBlock(FERMENTER_POS, ModBlocks.FERMENTER.get());
        FermenterBlockEntity be = helper.getBlockEntity(FERMENTER_POS);
        be.getInputTank().getPrimaryHandler().fill(hoppedWort(BATCH), IFluidHandler.FluidAction.EXECUTE);
        be.getYeastSlot().setStackInSlot(0, new ItemStack(ModItems.YEAST.get(), 1));
        return be;
    }

    private static net.minecraft.resources.ResourceLocation anyFermentingRecipeId(GameTestHelper helper) {
        return helper.getLevel().getRecipeManager()
            .getAllRecipesFor(ModRecipeTypes.FERMENTING.<RecipeInput, FermentingRecipe>getType())
            .stream().findFirst()
            .orElseThrow(() -> new AssertionError("no createbrewery:fermenting recipes loaded"))
            .id();
    }

    private static void assertStarted(GameTestHelper helper, FermenterBlockEntity be) {
        if (be.getStartedAt() != FermenterBlockEntity.NOT_STARTED) return;
        StringBuilder why = new StringBuilder("the batch never started; input=")
            .append(be.getInputTank().getPrimaryHandler().getFluid())
            .append(" yeast=").append(be.getYeastSlot().getStackInSlot(0));
        for (var holder : helper.getLevel().getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.FERMENTING.<RecipeInput, FermentingRecipe>getType())) {
            FermentingRecipe r = holder.value();
            why.append(" | ").append(holder.id())
               .append(" items=").append(r.getIngredients().size())
               .append(" fluidsIn=").append(r.getFluidIngredients().size())
               .append(" fluidsOut=").append(r.getFluidResults().size())
               .append(" itemResults=").append(r.getRollableResults().size())
               .append(" duration=").append(r.getProcessingDuration());
            if (!r.getFluidIngredients().isEmpty()) {
                why.append(" fluidMatch=")
                   .append(r.getFluidIngredients().get(0).test(be.getInputTank().getPrimaryHandler().getFluid()))
                   .append(" need=").append(r.getFluidIngredients().get(0).amount());
            }
            if (!r.getIngredients().isEmpty()) {
                why.append(" itemMatch=").append(r.getIngredients().get(0).test(be.getYeastSlot().getStackInSlot(0)));
            }
        }
        helper.fail(why.toString());
    }

    /** Rewinds the clock so the batch is due; must run after the batch has actually started,
     *  because tryStart overwrites startedAt with the current game time. */
    private static void backdate(FermenterBlockEntity be) {
        be.setStartedAt(be.getStartedAt() - BACKDATE);
    }

    private static int amountOf(FermenterBlockEntity be, boolean output) {
        return (output ? be.getOutputTank() : be.getInputTank()).getPrimaryHandler().getFluid().getAmount();
    }

    // ---------------------------------------------------------------- tests

    @GameTest(template = TEMPLATE)
    public static void producesBeerOnceDurationElapses(GameTestHelper helper) {
        FermenterBlockEntity be = loadedFermenter(helper);

        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                assertStarted(helper, be);
                backdate(be);
            })
            .thenWaitUntil(() -> {
                FluidStack out = be.getOutputTank().getPrimaryHandler().getFluid();
                helper.assertTrue(out.getFluid() == beerFluid() && out.getAmount() == BATCH,
                    "expected " + BATCH + "mB of beer in the output tank, found " + out);
                helper.assertTrue(amountOf(be, false) == 0, "the wort was not consumed");
                helper.assertTrue(be.getYeastSlot().getStackInSlot(0).isEmpty(), "the yeast was not consumed");
                helper.assertTrue(be.getStartedAt() == FermenterBlockEntity.NOT_STARTED,
                    "the fermenter did not return to idle after finishing");
            })
            .thenSucceed();
    }

    /**
     * Review Focus #3, and the worst bug this mod could ship: a finished batch must wait for
     * room in the output rather than evaporating. The proof is the last step - the beer
     * appears only once the output is drained, which means it was held, not lost.
     */
    @GameTest(template = TEMPLATE)
    public static void holdsBatchWhenOutputTankIsFull(GameTestHelper helper) {
        FermenterBlockEntity be = loadedFermenter(helper);

        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                assertStarted(helper, be);

                int filled = be.getOutputTank().getPrimaryHandler()
                    .fill(beer(FermenterBlockEntity.TANK_CAPACITY), IFluidHandler.FluidAction.EXECUTE);
                helper.assertTrue(filled == FermenterBlockEntity.TANK_CAPACITY,
                    "could not pre-fill the output tank, filled " + filled);
                helper.assertTrue(
                    be.getOutputTank().getCapability().fill(beer(1), IFluidHandler.FluidAction.SIMULATE) == 0,
                    "the output tank capability must refuse insertion from outside");

                backdate(be);
            })
            // Long enough that a fermenter which drops the batch would have done so by now.
            .thenIdle(20)
            .thenExecute(() -> {
                helper.assertTrue(amountOf(be, false) == BATCH,
                    "the wort was consumed while the output was full - the batch was lost");
                helper.assertTrue(!be.getYeastSlot().getStackInSlot(0).isEmpty(),
                    "the yeast was consumed while the output was full");
                assertStarted(helper, be);
                helper.assertTrue(amountOf(be, true) == FermenterBlockEntity.TANK_CAPACITY,
                    "the output tank contents changed while it was full");

                be.getOutputTank().getPrimaryHandler()
                    .drain(FermenterBlockEntity.TANK_CAPACITY, IFluidHandler.FluidAction.EXECUTE);
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(amountOf(be, true) == BATCH,
                    "the held batch was never delivered after the output drained");
                helper.assertTrue(amountOf(be, false) == 0, "the wort was not consumed on delivery");
            })
            .thenSucceed();
    }

    /** Review Focus #2: a saved recipe id that no longer resolves (mod or datapack removed). */
    @GameTest(template = TEMPLATE)
    public static void survivesUnresolvableSavedRecipeId(GameTestHelper helper) {
        helper.setBlock(FERMENTER_POS, ModBlocks.FERMENTER.get());
        FermenterBlockEntity be = helper.getBlockEntity(FERMENTER_POS);

        CompoundTag tag = new CompoundTag();
        tag.putLong("StartedAt", helper.getLevel().getGameTime());
        tag.putString("Recipe", "createbrewery:no_such_recipe");
        // loadWithComponents is the real chunk-load path, not a test-only back door.
        be.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(be.getStartedAt() != FermenterBlockEntity.NOT_STARTED,
            "the bad state was not actually loaded, so the test would prove nothing");

        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                helper.assertTrue(be.getStartedAt() == FermenterBlockEntity.NOT_STARTED,
                    "the fermenter kept a batch pinned to a recipe that no longer exists");
                helper.assertTrue(be.getProgress() == 0f, "progress must be 0 with no resolvable recipe");
                helper.assertTrue(helper.getBlockEntity(FERMENTER_POS) != null,
                    "the block entity did not survive the unresolvable recipe id");
            })
            .thenSucceed();
    }

    /**
     * The NBT read must guard the tag. An absent StartedAt read as a plain getLong yields 0,
     * i.e. "started at world time 0", which reports a fermenter that never started as
     * instantly complete - free beer, and the core mechanic gone.
     */
    @GameTest(template = TEMPLATE)
    public static void absentStartedAtTagMeansNotStarted(GameTestHelper helper) {
        helper.setBlock(FERMENTER_POS, ModBlocks.FERMENTER.get());
        FermenterBlockEntity be = helper.getBlockEntity(FERMENTER_POS);

        CompoundTag tag = new CompoundTag();
        // Any real, resolvable id will do; looked up rather than hardcoded, because Create's
        // ProcessingRecipeBuilder prefixes generated ids with the recipe type
        // (createbrewery:fermenting/fermenting_ale, not createbrewery:fermenting_ale).
        tag.putString("Recipe", anyFermentingRecipeId(helper).toString());   // resolvable, but no StartedAt
        be.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(be.getStartedAt() == FermenterBlockEntity.NOT_STARTED,
            "an absent StartedAt tag was read as world time 0, found " + be.getStartedAt());
        helper.assertTrue(be.getProgress() == 0f,
            "a fermenter that never started reported progress " + be.getProgress());
        helper.succeed();
    }

    /** Review Focus #5: the wort was piped back out mid-batch; no beer may come from nothing. */
    @GameTest(template = TEMPLATE)
    public static void invalidatesWhenInputDrainedMidBatch(GameTestHelper helper) {
        FermenterBlockEntity be = loadedFermenter(helper);

        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                assertStarted(helper, be);
                // Backdate first: if the validity check were broken this would visibly
                // produce beer out of an empty tank rather than merely failing to reset.
                backdate(be);
                be.getInputTank().getPrimaryHandler().drain(BATCH, IFluidHandler.FluidAction.EXECUTE);
                helper.assertTrue(amountOf(be, false) == 0, "the input tank did not drain");
            })
            .thenIdle(2)
            .thenExecute(() -> {
                helper.assertTrue(be.getStartedAt() == FermenterBlockEntity.NOT_STARTED,
                    "the batch survived its input being drained");
                helper.assertTrue(amountOf(be, true) == 0, "beer was produced from an empty input tank");
                helper.assertTrue(!be.getYeastSlot().getStackInSlot(0).isEmpty(),
                    "the yeast was consumed by a batch that never completed");
            })
            .thenSucceed();
    }

    /**
     * The other half of Review Focus #5: a hopper pulls the yeast out mid-batch. If only the
     * fluid were re-checked, finish() would extract nothing from an empty slot and still
     * produce beer, letting one yeast run unlimited batches.
     */
    @GameTest(template = TEMPLATE)
    public static void invalidatesWhenYeastRemovedMidBatch(GameTestHelper helper) {
        FermenterBlockEntity be = loadedFermenter(helper);

        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                assertStarted(helper, be);
                backdate(be);
                be.getYeastSlot().extractItem(0, 1, false);
                helper.assertTrue(be.getYeastSlot().getStackInSlot(0).isEmpty(),
                    "the yeast slot did not empty");
            })
            .thenIdle(2)
            .thenExecute(() -> {
                helper.assertTrue(be.getStartedAt() == FermenterBlockEntity.NOT_STARTED,
                    "the batch survived its yeast being removed");
                helper.assertTrue(amountOf(be, true) == 0, "beer was produced with no yeast");
                helper.assertTrue(amountOf(be, false) == BATCH,
                    "the wort was consumed by a batch that never completed");
            })
            .thenSucceed();
    }

    /**
     * The registered BLOCK capabilities are what make the Fermenter automatable, and nothing
     * else tests them: the asymmetry assertion in holdsBatchWhenOutputTankIsFull hits the
     * behaviour's own handler, not the capability a hopper or pipe actually resolves.
     *
     * This goes through level.getCapability, i.e. the same lookup a hopper does.
     */
    @GameTest(template = TEMPLATE)
    public static void blockCapabilitiesAreInsertOnlyWhereItMatters(GameTestHelper helper) {
        helper.setBlock(FERMENTER_POS, ModBlocks.FERMENTER.get());
        FermenterBlockEntity be = helper.getBlockEntity(FERMENTER_POS);
        be.getYeastSlot().setStackInSlot(0, new ItemStack(ModItems.YEAST.get(), 1));

        BlockPos abs = helper.absolutePos(FERMENTER_POS);

        IItemHandler items = helper.getLevel()
            .getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.DOWN);
        helper.assertTrue(items != null, "no item handler capability is registered on the fermenter");

        helper.assertTrue(items.extractItem(0, 1, false).isEmpty(),
            "a hopper under the fermenter could pull the yeast out and cancel the batch");
        helper.assertTrue(be.getYeastSlot().getStackInSlot(0).getCount() == 1,
            "the yeast slot lost its contents to an extraction that should have been refused");

        // Insertion must still work, or a funnel could never feed it in the first place.
        helper.assertTrue(items.insertItem(0, new ItemStack(ModItems.YEAST.get(), 1), false).isEmpty(),
            "the yeast slot refused an insertion it had room for");
        helper.assertTrue(be.getYeastSlot().getStackInSlot(0).getCount() == 2,
            "the inserted yeast did not reach the real slot");

        IFluidHandler fluids = helper.getLevel()
            .getCapability(Capabilities.FluidHandler.BLOCK, abs, Direction.UP);
        helper.assertTrue(fluids != null, "no fluid handler capability is registered on the fermenter");
        helper.assertTrue(fluids.fill(hoppedWort(BATCH), IFluidHandler.FluidAction.EXECUTE) == BATCH,
            "a pipe could not fill the input tank through the block capability");
        helper.assertTrue(amountOf(be, false) == BATCH, "the piped-in wort did not reach the input tank");

        helper.succeed();
    }

    /**
     * SmartBlockEntity#destroy only fans out to behaviours, and the yeast slot is not one, so
     * without an explicit drop the yeast is voided when the block is broken. destroyBlock does
     * not drop the block itself, so a yeast item entity here can only have come from the slot.
     */
    @GameTest(template = TEMPLATE)
    public static void breakingTheFermenterDropsItsYeast(GameTestHelper helper) {
        helper.setBlock(FERMENTER_POS, ModBlocks.FERMENTER.get());
        FermenterBlockEntity be = helper.getBlockEntity(FERMENTER_POS);
        be.getYeastSlot().setStackInSlot(0, new ItemStack(ModItems.YEAST.get(), 3));

        helper.destroyBlock(FERMENTER_POS);

        helper.assertBlockNotPresent(ModBlocks.FERMENTER.get(), FERMENTER_POS);
        helper.assertItemEntityCountIs(ModItems.YEAST.get(), FERMENTER_POS, 2.0, 3);
        helper.succeed();
    }

    /**
     * Not about the Fermenter: barley seeds were once registered as a plain Item and could
     * not plant anything, and the bug survived review because verification used /setblock.
     * This goes through the real use-item-on-block path instead.
     */
    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void plantsBarleyWithTheSeedItem(GameTestHelper helper) {
        BlockPos soil = new BlockPos(1, 0, 1);
        BlockPos crop = soil.above();
        helper.setBlock(soil, Blocks.FARMLAND);
        helper.setDayTime(6000);

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BARLEY_SEEDS.get(), 3));

        BlockPos absolute = helper.absolutePos(soil);
        helper.useBlock(soil, player,
            new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false));

        helper.assertBlockPresent(ModBlocks.BARLEY_CROP.get(), crop);
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2,
            "planting did not consume a seed");
        helper.succeed();
    }

    /**
     * addToGoggleTooltip/containedFluidTooltip cannot be exercised in a GameTest at all: they
     * are client-only despite living on a plain interface with no visible @OnlyIn marker.
     * Empirically confirmed - calling addToGoggleTooltip here crashed the whole GameTestServer
     * (not just failed the test), with the real cause several frames down:
     *
     *   net.createmod.catnip.lang.LangBuilder.forGoggles(LangBuilder.java:179)
     *   -> attempted to load net.minecraft.client.Minecraft for invalid dist DEDICATED_SERVER
     *
     * So the goggle tooltip override is verified by reading the code and by the manual checks
     * in the task report; it cannot be pinned by an automated test in this project without a
     * real client, which the task explicitly forbids running (runClient hangs the agent).
     */

    /**
     * Config-sync fix: FERMENTATION_DURATION_MULTIPLIER is a COMMON config, never synced to
     * clients, so getProgress() on the client must not recompute it locally. This proves the
     * write side of the fix - that the server actually puts ScaledDuration into the same tag
     * BlockEntity#getUpdateTag sends to clients (getUpdateTag -> writeClient -> write(...,
     * clientPacket=true), confirmed by decompiling SmartBlockEntity/SyncedBlockEntity.
     *
     * What this cannot prove in a GameTest: a GameTestServer has no ClientLevel, so
     * level.isClientSide is never true here, and the read-side preference in
     * FermenterBlockEntity#scaledDuration (see FermentationProgress#resolveScaledDuration,
     * unit-tested headlessly) is untested end-to-end. That needs a real client, noted in the
     * task report as a manual check.
     */
    @GameTest(template = TEMPLATE)
    public static void syncTagCarriesServerScaledDuration(GameTestHelper helper) {
        FermenterBlockEntity be = loadedFermenter(helper);

        CompoundTag idleTag = be.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(!idleTag.contains("ScaledDuration"),
            "an idle fermenter must not put a scaled duration in the sync tag");

        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                assertStarted(helper, be);
                CompoundTag tag = be.getUpdateTag(helper.getLevel().registryAccess());
                helper.assertTrue(tag.contains("ScaledDuration"),
                    "the sync tag did not carry ScaledDuration while a batch is active");
                helper.assertTrue(tag.getInt("ScaledDuration") == 24000,
                    "expected the recipe's own 24000-tick duration at the default 1.0 multiplier, got "
                        + tag.getInt("ScaledDuration"));
            })
            .thenSucceed();
    }
}
