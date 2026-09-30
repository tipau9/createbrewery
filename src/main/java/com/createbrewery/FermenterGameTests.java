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

    /** The alcohol-poisoning damage type is registered and actually hurts. */
    @GameTest(template = TEMPLATE)
    public static void alcoholPoisoningDealsDamage(GameTestHelper helper) {
        net.minecraft.world.entity.animal.Pig pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        float before = pig.getHealth();
        pig.hurt(com.createbrewery.drunk.DrunkServer.poisonSource(helper.getLevel()), 2f);
        helper.assertTrue(pig.getHealth() < before, "poisoning did not hurt");
        helper.succeed();
    }

    /** The party and vomiting effects tick for their whole duration without crashing. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void partyAndVomitEffectsRunThrough(GameTestHelper helper) {
        net.minecraft.world.entity.animal.Pig pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        pig.setHealth(5f);
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.VOMITING,
            com.createbrewery.effect.VomitingEffect.DURATION));
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.GOOD_MOOD, 200));
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.CHEERS, 201));
        // Waits for it rather than a fixed delay: the pig may start ticking a little late.
        helper.succeedWhen(() -> {
            helper.assertTrue(!pig.hasEffect(com.createbrewery.effect.ModEffects.VOMITING), "vomiting did not end");
            helper.assertTrue(pig.getHealth() > 5f, "cheers did not heal");
        });
    }

    /** Every custom particle type and sound is registered and can be sent to clients. */
    @GameTest(template = TEMPLATE)
    public static void customParticlesAndSoundsAreRegistered(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        net.minecraft.core.BlockPos pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        for (var p : java.util.List.of(com.createbrewery.particle.ModParticles.BEER_FOAM, com.createbrewery.particle.ModParticles.CHEERS_SPARK,
                com.createbrewery.particle.ModParticles.CONFETTI, com.createbrewery.particle.ModParticles.PARTY_NOTE,
                com.createbrewery.particle.ModParticles.VOMIT_CHUNK, com.createbrewery.particle.ModParticles.VOMIT_SPLASH,
                com.createbrewery.particle.ModParticles.VOMIT_PUDDLE, com.createbrewery.particle.ModParticles.POWDER, com.createbrewery.particle.ModParticles.NOSEBLEED,
                com.createbrewery.particle.ModParticles.SMOKE)) {
            helper.assertTrue(p.isBound(), "particle not registered: " + p.getId());
            level.sendParticles(p.get(), pos.getX(), pos.getY(), pos.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
        }
        for (var s : java.util.List.of(com.createbrewery.sound.ModSounds.GLASS_CLINK, com.createbrewery.sound.ModSounds.HICCUP,
                com.createbrewery.sound.ModSounds.HEARTBEAT, com.createbrewery.sound.ModSounds.BEER_OPEN,
                com.createbrewery.sound.ModSounds.EAR_RINGING, com.createbrewery.sound.ModSounds.SNIFF,
                com.createbrewery.sound.ModSounds.COUGH, com.createbrewery.sound.ModSounds.GIGGLE)) {
            helper.assertTrue(s.isBound(), "sound not registered: " + s.getId());
            level.playSound(null, pos, s.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        }
        helper.succeed();
    }

    /** Once the painkiller kicks in it clears the hangover; before that it does not. */
    @GameTest(template = TEMPLATE)
    public static void painkillerClearsHangoverAfterOnset(GameTestHelper helper) {
        var early = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        var late = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 3, 2, 3);
        for (var pig : java.util.List.of(early, late)) {
            pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.HANGOVER, 2000));
        }
        early.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.PAINKILLER,
            com.createbrewery.effect.PainkillerEffect.DURATION));
        late.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.PAINKILLER,
            com.createbrewery.effect.PainkillerEffect.DURATION - com.createbrewery.effect.PainkillerEffect.ONSET));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(early.hasEffect(com.createbrewery.effect.ModEffects.HANGOVER), "worked before its onset");
            helper.assertTrue(!late.hasEffect(com.createbrewery.effect.ModEffects.HANGOVER), "did not clear the hangover");
            helper.succeed();
        });
    }

    /** Ibu plus alcohol: the stomach-bleeding damage type is registered and hurts at once. */
    @GameTest(template = TEMPLATE)
    public static void ibuWithAlcoholHurts(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        float before = player.getHealth();
        com.createbrewery.drunk.DrunkServer.irritateStomach(player);
        helper.assertTrue(player.getHealth() < before, "Ibu and alcohol did not hurt");
        helper.succeed();
    }

    /** Every high ends in its comedown, and the heart-attack damage type hurts. */
    @GameTest(template = TEMPLATE)
    public static void drugHighsEndInComedown(GameTestHelper helper) {
        var coke = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        var keta = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 3, 2, 3);
        coke.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.COKE_HIGH, 5));
        keta.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.KETA_HIGH, 5));
        float before = coke.getHealth();
        coke.hurt(com.createbrewery.drugs.DrugServer.heartAttack(helper.getLevel()), 2f);
        helper.assertTrue(coke.getHealth() < before, "heart attack did not hurt");
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(coke.hasEffect(com.createbrewery.effect.ModEffects.COKE_CRASH), "no crash after Koks");
            helper.assertTrue(keta.hasEffect(com.createbrewery.effect.ModEffects.DAZED), "not dazed after Keta");
            helper.succeed();
        });
    }

    /** Koks and Keta together raise the CK-Mix, and that makes the comedown longer. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ckMixMakesTheComedownWorse(GameTestHelper helper) {
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        pig.setInvulnerable(true); // the heart strain must not end the test early
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.COKE_HIGH, 45));
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.KETA_HIGH, 200));
        helper.runAfterDelay(25, () -> helper.assertTrue(pig.hasEffect(com.createbrewery.effect.ModEffects.CK_MIX), "no CK-Mix"));
        helper.runAfterDelay(60, () -> {
            var crash = pig.getEffect(com.createbrewery.effect.ModEffects.COKE_CRASH);
            helper.assertTrue(crash != null && crash.getDuration() > 1200, "comedown not longer after CK");
            helper.succeed();
        });
    }

    /** Weed pulls the K-Loch a dose closer; a third joint makes the circulation give up. */
    @GameTest(template = TEMPLATE)
    public static void weedMixesWithKetaAndGreensOut(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var weed = com.createbrewery.drugs.DrugServer.Kind.WEED;
        com.createbrewery.drugs.DrugServer.take(player, weed);
        com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.KETA);
        com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.KETA);
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.K_HOLE), "weed did not pull the K-Loch closer");
        // Two and a half joints (25 hits), hit by hit: the circulation gives up for sure.
        for (int i = 1; i < 26; i++) com.createbrewery.drugs.DrugServer.take(player, weed);
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.GREENING_OUT), "two and a half joints did not green out");
        helper.succeed();
    }

    /** Four lines on alcohol overload the heart: it races first, and when it gives out you collapse. */
    @GameTest(template = TEMPLATE)
    public static void overloadedHeartRacesThenGivesOut(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        float before = player.getHealth();
        // Past the come-up, fourth line.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.COKE_HIGH,
            com.createbrewery.drugs.DrugServer.COKE_TICKS - 400, 3));
        com.createbrewery.drunk.DrunkServer.state(player).blood = 1.5f;
        for (int i = 0; i < 20; i++) com.createbrewery.drugs.DrugServer.heartTick(player);
        var racing = player.getEffect(com.createbrewery.effect.ModEffects.TACHYCARDIA);
        helper.assertTrue(racing != null && racing.getAmplifier() == 1, "the heart did not race");
        com.createbrewery.drugs.DrugServer.heartAttack(player);
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.HEART_ATTACK), "no collapse");
        helper.assertTrue(player.getHealth() < before, "the heart attack did not hurt");
        helper.succeed();
    }

    /** Throwing up while out cold chokes you; awake, it does not. */
    @GameTest(template = TEMPLATE)
    public static void vomitingOutColdChokes(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player out = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        net.minecraft.world.entity.player.Player awake = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        com.createbrewery.drunk.DrunkServer.blackout(out);
        com.createbrewery.drunk.DrunkServer.vomit(out);
        com.createbrewery.drunk.DrunkServer.vomit(awake);
        helper.assertTrue(out.hasEffect(com.createbrewery.effect.ModEffects.ASPIRATION), "out cold, but no choking");
        helper.assertTrue(!awake.hasEffect(com.createbrewery.effect.ModEffects.ASPIRATION), "choked while awake");
        helper.succeed();
    }

    /** Keta dulls the pain, not the injury: the hit lands in full. */
    @GameTest(template = TEMPLATE)
    public static void ketaDoesNotSoftenDamage(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.K_HOLE, 200));
        float before = player.getHealth();
        player.hurt(player.damageSources().generic(), 4f);
        helper.assertTrue(Math.abs(before - 4f - player.getHealth()) < 0.01f, "Keta softened the hit");
        helper.succeed();
    }

    /** A line comes up over seconds instead of hitting at once. */
    @GameTest(template = TEMPLATE)
    public static void aLineComesUpSlowly(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.COKE);
        helper.assertTrue(com.createbrewery.drugs.DrugEffect.strength(player, com.createbrewery.effect.ModEffects.COKE_HIGH) < 0.05f,
            "Koks hit at full strength at once");
        helper.succeed();
    }

    /** More Keta in the K-Loch deepens it; it does not start the come-up again. */
    @GameTest(template = TEMPLATE)
    public static void moreKetaDeepensTheKHole(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var keta = com.createbrewery.drugs.DrugServer.Kind.KETA;
        for (int i = 0; i < 3; i++) com.createbrewery.drugs.DrugServer.take(player, keta);
        // Deep in the hole already, past its come-up.
        player.removeEffect(com.createbrewery.effect.ModEffects.K_HOLE);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.K_HOLE,
            com.createbrewery.drugs.DrugServer.K_HOLE_TICKS - 200));
        com.createbrewery.drugs.DrugServer.take(player, keta);
        helper.assertTrue(com.createbrewery.drugs.DrugEffect.strength(player, com.createbrewery.effect.ModEffects.K_HOLE) > 0.9f,
            "another dose pulled the K-Loch back to its come-up");
        helper.succeed();
    }

    /** Choking with no air left hurts (the aspiration damage type is registered). */
    @GameTest(template = TEMPLATE)
    public static void chokingHurts(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.ASPIRATION,
            com.createbrewery.drugs.DrugServer.ASPIRATION_TICKS));
        player.setAirSupply(0);
        float before = player.getHealth();
        com.createbrewery.drugs.DrugServer.aspirationTick(player, 0);
        helper.assertTrue(player.getHealth() < before, "choking did not hurt");
        helper.succeed();
    }

    /** A joint is smoked hit by hit: one durability per hit, gone after the last. */
    @GameTest(template = TEMPLATE)
    public static void jointWearsDownHitByHit(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        net.minecraft.world.item.ItemStack joint = new net.minecraft.world.item.ItemStack(com.createbrewery.ModItems.JOINT.get());
        joint.set(com.createbrewery.drugs.Purity.PURITY.get(), new com.createbrewery.drugs.Purity(1f, false, false)); // an average joint
        com.createbrewery.drugs.DrugItem.setLit(joint, true); // an unlit one is not smoked
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, joint);
        joint.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(joint.getDamageValue() == 1, "a hit did not wear the joint");
        for (int i = 1; i < com.createbrewery.drugs.DrugServer.HITS_PER_JOINT; i++) joint.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(joint.isEmpty(), "the joint did not burn down");
        float joints = com.createbrewery.drugs.DrugServer.joints(player);
        helper.assertTrue(Math.abs(joints - 1f) < 0.01f, "one joint should be one joint's worth, was " + joints);
        helper.succeed();
    }

    /** Creative players smoke joints down too, so the durability bar shows. */
    @GameTest(template = TEMPLATE)
    public static void jointWearsDownInCreative(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        net.minecraft.world.item.ItemStack joint = new net.minecraft.world.item.ItemStack(com.createbrewery.ModItems.JOINT.get());
        com.createbrewery.drugs.DrugItem.setLit(joint, true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, joint);
        joint.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(joint.getDamageValue() == 1 && joint.isBarVisible(), "a creative hit did not wear the joint");
        for (int i = 1; i < com.createbrewery.drugs.DrugServer.HITS_PER_JOINT; i++) joint.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(joint.isEmpty(), "the joint did not burn down in creative");
        helper.succeed();
    }

    private static net.minecraft.world.entity.player.Player stonedPlayer(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        for (int i = 0; i < 3; i++) com.createbrewery.drugs.DrugServer.hit(player);
        // Past the come-up, at the peak.
        int hits = player.getEffect(com.createbrewery.effect.ModEffects.WEED_HIGH).getAmplifier();
        player.removeEffect(com.createbrewery.effect.ModEffects.WEED_HIGH);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.WEED_HIGH,
            com.createbrewery.drugs.DrugServer.WEED_TICKS - 300, hits));
        return player;
    }

    private static void finishUsing(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish(player, stack, 0, stack));
    }

    /** Smoking dries the mouth; any drink takes it away. */
    @GameTest(template = TEMPLATE)
    public static void cottonmouthUntilYouDrink(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = stonedPlayer(helper);
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.COTTONMOUTH), "smoking left no dry mouth");
        finishUsing(player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.COTTONMOUTH), "bread quenched the thirst");
        finishUsing(player, net.minecraft.world.item.alchemy.PotionContents.createItemStack(
            net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER));
        helper.assertTrue(!player.hasEffect(com.createbrewery.effect.ModEffects.COTTONMOUTH), "water did not help the dry mouth");
        helper.succeed();
    }

    /** The munchies: something sweet while high fills you up and makes you happy (luck, speed, extra hearts). */
    @GameTest(template = TEMPLATE)
    public static void sweetsWhileHighAreBliss(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = stonedPlayer(helper);
        player.getFoodData().setFoodLevel(4);
        finishUsing(player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKIE));
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.SNACK_BLISS), "a cookie while high was no bliss");
        helper.assertTrue(player.getAbsorptionAmount() >= 4f, "no extra hearts from the bliss, had " + player.getAbsorptionAmount());
        helper.assertTrue(player.getFoodData().getFoodLevel() >= 8, "sweets did not fill up, food " + player.getFoodData().getFoodLevel());
        net.minecraft.world.entity.player.Player sober = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        finishUsing(sober, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKIE));
        helper.assertTrue(!sober.hasEffect(com.createbrewery.effect.ModEffects.SNACK_BLISS), "bliss without being high");
        helper.succeed();
    }

    /** Cake while high is bliss, but the bliss adds no food of its own: the slice must still be taken. */
    @GameTest(template = TEMPLATE)
    public static void cakeWhileHighIsBlissNotFreeFood(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = stonedPlayer(helper);
        net.minecraft.core.BlockPos cake = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(cake, net.minecraft.world.level.block.Blocks.CAKE.defaultBlockState());
        player.getFoodData().setFoodLevel(18);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
            player, net.minecraft.world.InteractionHand.MAIN_HAND, cake,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(cake), net.minecraft.core.Direction.UP, cake, false)));
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.SNACK_BLISS), "cake while high was no bliss");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 18, "the bliss fed on its own, food " + player.getFoodData().getFoodLevel());
        helper.succeed();
    }

    /** A trip ends in a day of tolerance for every psychedelic, and a tolerant dose starts at half and only fades. */
    @GameTest(template = TEMPLATE)
    public static void psychedelicsShareTolerance(GameTestHelper helper) {
        // Mock players do not tick their effects; a pig does.
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.LSD_TRIP, 5));
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.PSY_TOLERANCE, 1000));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(pig.hasEffect(com.createbrewery.effect.ModEffects.PSY_TOLERANCE), "no tolerance after LSD");
            com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.SHROOMS);
            float felt = com.createbrewery.drugs.DrugEffect.felt(player, com.createbrewery.effect.ModEffects.SHROOM_TRIP);
            helper.assertTrue(felt > 0f && felt <= 0.5f, "tolerant mushrooms hit at " + felt);
            helper.succeed();
        });
    }

    /** DMT breaks through within seconds and pins the body down; a second hit only prolongs it. */
    @GameTest(template = TEMPLATE)
    public static void dmtBreaksThroughAtOnce(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var dmt = com.createbrewery.drugs.DrugServer.Kind.DMT;
        com.createbrewery.drugs.DrugServer.take(player, dmt);
        var effect = player.getEffect(com.createbrewery.effect.ModEffects.BREAKTHROUGH);
        helper.assertTrue(effect != null && effect.getAmplifier() == 0, "no breakthrough");
        com.createbrewery.drugs.DrugServer.take(player, dmt);
        helper.assertTrue(player.getEffect(com.createbrewery.effect.ModEffects.BREAKTHROUGH).getAmplifier() == 1,
            "a second hit restarted the come-up");
        helper.succeed();
    }

    /** MDMA and meth both end in a comedown, and meth blocks sleep until it does. */
    @GameTest(template = TEMPLATE)
    public static void stimulantsComeDown(GameTestHelper helper) {
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.ROLLING, 5));
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.TWEAK, 5));
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.METH);
        com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.METH);
        helper.assertTrue(player.getEffect(com.createbrewery.effect.ModEffects.TWEAK).getAmplifier() == 1, "meth does not stack");
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(pig.hasEffect(com.createbrewery.effect.ModEffects.COMEDOWN), "no Tiefpunkt after MDMA");
            helper.assertTrue(pig.hasEffect(com.createbrewery.effect.ModEffects.METH_CRASH), "no crash after meth");
            helper.succeed();
        });
    }

    /** A trip ends in the afterglow. */
    @GameTest(template = TEMPLATE)
    public static void tripLeavesAfterglow(GameTestHelper helper) {
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.LSD_TRIP, 5));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(pig.hasEffect(com.createbrewery.effect.ModEffects.AFTERGLOW), "no afterglow after the trip");
            helper.succeed();
        });
    }

    /** Two shots of heroin are survivable, with Xanax on top they stop the breath; Xanax ends a bad trip. */
    @GameTest(template = TEMPLATE)
    public static void heroinWithXanaxStopsTheBreath(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.BAD_TRIP, 1000));
        // Two shots, the second a top-up at full strength: still breathing.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.NOD, 4700, 1));
        com.createbrewery.drugs.Opioids.body(player);
        helper.assertFalse(player.hasEffect(com.createbrewery.effect.ModEffects.RESPIRATORY_DEPRESSION), "two shots stopped the breath");
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.CALM, 3000, 0));
        com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.XANAX);
        helper.assertFalse(player.hasEffect(com.createbrewery.effect.ModEffects.BAD_TRIP), "Xanax did not end the bad trip");
        com.createbrewery.drugs.Opioids.body(player);
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.RESPIRATORY_DEPRESSION), "heroin and Xanax did not stop the breath");
        helper.succeed();
    }

    /** Fentanyl in a street bar stops the breath of someone not used to opioids; Naloxon still works. */
    @GameTest(template = TEMPLATE)
    public static void fentanylStopsTheBreath(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        com.createbrewery.drugs.Opioids.fentanyl(player);
        helper.assertTrue(player.getEffect(com.createbrewery.effect.ModEffects.NOD).getAmplifier() == 2, "fentanyl was not three shots' worth");
        // Past the three seconds it takes to reach the brain.
        var nod = player.getEffect(com.createbrewery.effect.ModEffects.NOD);
        player.removeEffect(com.createbrewery.effect.ModEffects.NOD);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.NOD, nod.getDuration() - 100, 2));
        com.createbrewery.drugs.Opioids.body(player);
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.RESPIRATORY_DEPRESSION), "fentanyl did not stop the breath");
        com.createbrewery.drugs.Opioids.naloxone(player);
        helper.assertFalse(player.hasEffect(com.createbrewery.effect.ModEffects.RESPIRATORY_DEPRESSION), "naloxon did not work on fentanyl");
        helper.succeed();
    }

    /** A pill after a meal comes up later; on an empty stomach, sooner. */
    @GameTest(template = TEMPLATE)
    public static void foodSlowsAPill(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player full = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        full.getFoodData().setFoodLevel(20);
        full.getFoodData().setSaturation(15f);
        com.createbrewery.drugs.DrugServer.take(full, com.createbrewery.drugs.DrugServer.Kind.MDMA);
        net.minecraft.world.entity.player.Player hungry = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        hungry.getFoodData().setFoodLevel(4);
        hungry.getFoodData().setSaturation(0f);
        com.createbrewery.drugs.DrugServer.take(hungry, com.createbrewery.drugs.DrugServer.Kind.MDMA);
        int ticks = com.createbrewery.drugs.Stimulants.MDMA_TICKS;
        helper.assertTrue(full.getEffect(com.createbrewery.effect.ModEffects.ROLLING).getDuration() > ticks, "a meal did not slow the pill");
        helper.assertTrue(hungry.getEffect(com.createbrewery.effect.ModEffects.ROLLING).getDuration() < ticks, "an empty stomach did not speed it");
        helper.assertTrue(com.createbrewery.drugs.DrugEffect.strength(hungry, com.createbrewery.effect.ModEffects.ROLLING) > 0f, "hungry, it had not started");
        helper.succeed();
    }

    /** Used to Koks, the same line does less; the heart pays in full. */
    @GameTest(template = TEMPLATE)
    public static void aHabitThinsTheHigh(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.COKE_HIGH,
            com.createbrewery.drugs.DrugServer.COKE_TICKS - 300, 0));
        float fresh = com.createbrewery.drugs.DrugEffect.felt(player, com.createbrewery.effect.ModEffects.COKE_HIGH);
        com.createbrewery.drunk.DrunkServer.state(player).cokeHabit = 0.9f;
        com.createbrewery.drugs.DrugServer.habits(player);
        float used = com.createbrewery.drugs.DrugEffect.felt(player, com.createbrewery.effect.ModEffects.COKE_HIGH);
        helper.assertTrue(used < fresh * 0.5f, "a habit did not thin the high: " + fresh + " -> " + used);
        helper.succeed();
    }

    /** A Koks binge ends in craving; the next line stills it. */
    @GameTest(template = TEMPLATE)
    public static void aBingeEndsInCraving(GameTestHelper helper) {
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.COKE_HIGH, 5, 1));
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.CRAVING, 2400));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(pig.hasEffect(com.createbrewery.effect.ModEffects.CRAVING), "no craving after a binge");
            com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.COKE);
            helper.assertFalse(player.hasEffect(com.createbrewery.effect.ModEffects.CRAVING), "a line did not still the craving");
            helper.succeed();
        });
    }

    /** The serotonin is still spent after a roll: the next pill only does half. */
    @GameTest(template = TEMPLATE)
    public static void mdmaWearsThinUntilTheLow(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.COMEDOWN_PENDING, 36000));
        com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.MDMA);
        helper.assertTrue(com.createbrewery.drugs.DrugEffect.strength(player, com.createbrewery.effect.ModEffects.ROLLING) <= 0.5f,
            "a spent pill rolled at full strength");
        helper.succeed();
    }

    /** The test command's phase clock: the peak of two LSD tabs sits between come-up and fade. */
    @GameTest(template = TEMPLATE)
    public static void testCommandSetsThePhase(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.LSD);
        com.createbrewery.drugs.DrugServer.take(player, com.createbrewery.drugs.DrugServer.Kind.LSD);
        // Two tabs at once still come up slowly.
        helper.assertTrue(com.createbrewery.drugs.DrugEffect.strength(player, com.createbrewery.effect.ModEffects.LSD_TRIP) < 0.1f,
            "the second tab skipped the come-up");
        com.createbrewery.drugs.TestCommand.setPhase(player, com.createbrewery.effect.ModEffects.LSD_TRIP, "peak");
        var trip = player.getEffect(com.createbrewery.effect.ModEffects.LSD_TRIP);
        helper.assertTrue(trip != null && trip.getAmplifier() == 1, "two tabs are not amplifier 1");
        helper.assertTrue(com.createbrewery.drugs.DrugEffect.strength(player, com.createbrewery.effect.ModEffects.LSD_TRIP) > 0.99f,
            "the peak is not at full strength");
        helper.succeed();
    }

    /** Naloxon brings the breath back and blocks the heroin; on a habit it is the withdrawal at once. */
    @GameTest(template = TEMPLATE)
    public static void naloxonBringsTheBreathBack(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.createbrewery.effect.ModEffects.NOD, 4700, 2));
        com.createbrewery.drunk.DrunkServer.state(player).dependence = 0.6f;
        com.createbrewery.drugs.Opioids.body(player);
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.RESPIRATORY_DEPRESSION), "three shots did not stop the breath");
        com.createbrewery.drugs.Opioids.naloxone(player);
        helper.assertFalse(player.hasEffect(com.createbrewery.effect.ModEffects.RESPIRATORY_DEPRESSION), "naloxon did not bring the breath back");
        com.createbrewery.drugs.Opioids.body(player);
        helper.assertFalse(player.hasEffect(com.createbrewery.effect.ModEffects.RESPIRATORY_DEPRESSION), "the heroin stopped the breath through the naloxon");
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.WITHDRAWAL), "naloxon on a habit brought no withdrawal");
        helper.succeed();
    }

    /** Named mixes show while both are active; Lachgas on a trip breaks through for a moment. */
    @GameTest(template = TEMPLATE)
    public static void mixesAreSpotted(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var fx = new Object() {
            void add(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(e, 2000, 1));
            }
        };
        fx.add(com.createbrewery.effect.ModEffects.LSD_TRIP);
        fx.add(com.createbrewery.effect.ModEffects.ROLLING);
        fx.add(com.createbrewery.effect.ModEffects.WAH);
        fx.add(com.createbrewery.effect.ModEffects.NOD);
        fx.add(com.createbrewery.effect.ModEffects.TWEAK);
        com.createbrewery.drugs.Mixes.tick(player);
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.CANDYFLIP), "no Candyflip");
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.SPEEDBALL), "no Speedball");
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.NITROUS_PEAK), "no Gipfelsturm");
        helper.assertTrue(player.hasEffect(com.createbrewery.effect.ModEffects.BREAKTHROUGH), "Lachgas on a trip did not break through");
        helper.assertFalse(player.hasEffect(com.createbrewery.effect.ModEffects.STONED_TRIP), "Nachgelegt without weed");
        helper.succeed();
    }
}
