package com.createbrewery;

import com.createbrewery.block.club.DjBoothBlock;
import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.block.club.DmxConsoleBlockEntity;
import com.createbrewery.block.club.FixtureBlockEntity;
import com.createbrewery.block.club.SpeakerBlock;
import com.createbrewery.block.club.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The club blocks' ways of losing a player's items or handing out something that is not beer. */
@GameTestHolder(CreateBrewery.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClubGameTests {

    private static final String TEMPLATE = "platform";
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    @GameTest(template = TEMPLATE)
    public static void brokenBoothDropsItsRecord(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);
        helper.assertTrue(dj.insertDisc(new ItemStack(Items.MUSIC_DISC_CAT), false, null), "the booth took no record");

        // With drops, as a player breaking it (helper.destroyBlock drops nothing).
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.assertItemEntityPresent(Items.MUSIC_DISC_CAT, POS, 2.0);
        // The club blocks had no loot tables at all: broken, they were simply gone.
        helper.assertItemEntityPresent(ModBlocks.DJ_BOOTH.get().asItem(), POS, 2.0);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bothDecksPlayAndMixOnTheCrossfader(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);
        long now = helper.getLevel().getGameTime();

        dj.insertDisc(new ItemStack(Items.MUSIC_DISC_CAT), false, null);
        helper.assertTrue(dj.isPlaying(DjBoothBlockEntity.A), "the first record did not start");
        helper.assertTrue(Math.abs(dj.deckGain(DjBoothBlockEntity.A, now) - 1f) < 1e-4f, "a lone deck is not at full volume");

        // The second record is only cued while the first plays, until its deck is started.
        dj.insertDisc(new ItemStack(Items.MUSIC_DISC_13), true, null);
        helper.assertFalse(dj.isPlaying(DjBoothBlockEntity.B), "the second record started instead of being cued");
        dj.toggleDeck(DjBoothBlockEntity.B, null);
        helper.assertTrue(dj.isPlaying(DjBoothBlockEntity.A) && dj.isPlaying(DjBoothBlockEntity.B), "both decks should play at once");

        // Equal power: halfway both are at 1/sqrt(2), so the mix does not dip.
        dj.setCrossfader(0.5f);
        float a = dj.deckGain(DjBoothBlockEntity.A, now), b = dj.deckGain(DjBoothBlockEntity.B, now);
        helper.assertTrue(Math.abs(a - b) < 1e-4f && Math.abs(a * a + b * b - 1f) < 1e-3f, "crossfader is not equal power: " + a + ", " + b);

        dj.setPitch(DjBoothBlockEntity.A, 3f);
        helper.assertTrue(dj.getPitch(DjBoothBlockEntity.A) == 1f + DjBoothBlockEntity.PITCH_RANGE, "pitch is not held to the fader's range");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mixerChannelsHoldToTheirRanges(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);
        int b = DjBoothBlockEntity.B;

        dj.setEq(b, DjBoothBlockEntity.LOW, -3f);
        helper.assertTrue(dj.getEq(b, DjBoothBlockEntity.LOW) == 0f, "a kill went below 0");
        helper.assertTrue(dj.getEq(DjBoothBlockEntity.A, DjBoothBlockEntity.LOW) == 0.5f, "deck A's EQ moved with deck B's");
        dj.setFilter(b, 0.02f);
        helper.assertTrue(dj.getFilter(b) == 0f, "the filter has no centre detent");
        dj.setFx(b, com.createbrewery.drunk.DeckFx.EFFECTS);
        helper.assertTrue(dj.getFx(b) == com.createbrewery.drunk.DeckFx.NONE, "the effect selector did not wrap round");

        int serial = dj.getLoopSerial(b);
        dj.setLoop(b, 4);
        helper.assertTrue(dj.getLoopBeats(b) == 4 && dj.getLoopSerial(b) != serial, "the loop did not engage");
        dj.setLoop(b, 4);
        helper.assertTrue(dj.getLoopBeats(b) == 0, "pressing the loop again did not let go");
        dj.setLoop(b, 3);
        helper.assertTrue(dj.getLoopBeats(b) == 0, "a loop length the mixer does not have was taken");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void speakersPlayOnlyTheBoothTheyAreLinkedTo(GameTestHelper helper) {
        BlockPos booth = helper.absolutePos(POS);
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        BlockPos speakerPos = new BlockPos(0, 1, 0);
        helper.setBlock(speakerPos, ModBlocks.SPEAKER.get());

        ItemStack speakers = new ItemStack(ModBlocks.SPEAKER.get(), 8);
        SpeakerBlock.link(speakers, booth);
        helper.assertTrue(SpeakerBlock.linkOf(speakers).orElse(null) != null, "the speaker items did not remember the booth");
        helper.assertTrue(SpeakerBlock.applyLink(helper.getLevel(), helper.absolutePos(speakerPos), speakers), "the placed speaker was not linked");
        SpeakerBlockEntity speaker = helper.getBlockEntity(speakerPos);
        helper.assertTrue(booth.equals(speaker.getBooth()), "linked to " + speaker.getBooth() + ", not the booth at " + booth);

        // A link naming no booth, or one out of cable reach, changes nothing.
        ItemStack nowhere = new ItemStack(ModBlocks.SPEAKER.get());
        SpeakerBlock.link(nowhere, booth.offset(SpeakerBlock.MAX_LINK + 10, 0, 0));
        helper.assertFalse(SpeakerBlock.applyLink(helper.getLevel(), helper.absolutePos(speakerPos), nowhere), "linked to a booth that is not there");
        helper.assertTrue(booth.equals(speaker.getBooth()), "a failed link undid the good one");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void recordCrateRotatesAndLosesNothing(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        BlockPos chestPos = POS.north();
        helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.MUSIC_DISC_13));
        chest.setItem(1, new ItemStack(Items.MUSIC_DISC_CAT));
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);
        dj.insertDisc(new ItemStack(Items.MUSIC_DISC_STAL), false, null);

        // Played in crate order, each record going back as the next comes out: 13, cat, then stal again.
        net.minecraft.world.item.Item[] order = {Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_13};
        for (net.minecraft.world.item.Item want : order) {
            helper.assertTrue(com.createbrewery.block.club.ClubTestAccess.restock(dj, DjBoothBlockEntity.A), "no record taken from the crate");
            helper.assertTrue(dj.getDisc(DjBoothBlockEntity.A).is(want), "deck A has " + dj.getDisc(DjBoothBlockEntity.A) + ", not " + want);
        }
        int discs = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (!chest.getItem(i).isEmpty()) discs++;
        helper.assertTrue(discs == 2, "the crate holds " + discs + " records, not 2");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void microphoneFindsItsBoothsSpeakersOnTheServer(GameTestHelper helper) {
        BlockPos booth = helper.absolutePos(POS);
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        BlockPos micPos = new BlockPos(0, 1, 0), speakerPos = new BlockPos(4, 1, 0), subPos = new BlockPos(4, 1, 4);
        helper.setBlock(micPos, ModBlocks.MICROPHONE.get());
        helper.setBlock(speakerPos, ModBlocks.SPEAKER.get());
        helper.setBlock(subPos, ModBlocks.SUBWOOFER.get());
        for (BlockPos at : new BlockPos[] {micPos, speakerPos, subPos}) {
            ItemStack stack = new ItemStack(helper.getBlockState(at).getBlock().asItem());
            SpeakerBlock.link(stack, booth);
            helper.assertTrue(SpeakerBlock.applyLink(helper.getLevel(), helper.absolutePos(at), stack), "not linked: " + at);
        }
        var out = com.createbrewery.block.club.ClubTestAccess.micSpeakers(helper.getBlockEntity(micPos));
        helper.assertTrue(out.size() == 1, "the voice goes to " + out.size() + " places, not the one speaker");
        helper.assertTrue(BlockPos.containing(out.get(0)).equals(helper.absolutePos(speakerPos)), "the voice goes to " + out.get(0));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void subsAndAmpRackLinkAndTheRackHoldsItsRange(GameTestHelper helper) {
        BlockPos booth = helper.absolutePos(POS);
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        BlockPos subPos = new BlockPos(0, 1, 0), rackPos = new BlockPos(2, 1, 0);
        helper.setBlock(subPos, ModBlocks.SUBWOOFER.get());
        helper.setBlock(rackPos, ModBlocks.AMP_RACK.get());
        for (BlockPos at : new BlockPos[] {subPos, rackPos}) {
            ItemStack stack = new ItemStack(helper.getBlockState(at).getBlock().asItem());
            SpeakerBlock.link(stack, booth);
            helper.assertTrue(SpeakerBlock.applyLink(helper.getLevel(), helper.absolutePos(at), stack), "not linked: " + at);
            SpeakerBlockEntity linked = helper.getBlockEntity(at);
            helper.assertTrue(booth.equals(linked.getBooth()), at + " linked to " + linked.getBooth());
        }

        com.createbrewery.block.club.AmpRackBlockEntity rack = helper.getBlockEntity(rackPos);
        rack.setCrossover(10f);
        rack.setSubGain(3f);
        rack.setTopGain(-1f);
        helper.assertTrue(rack.getCrossover() == 60f && rack.getSubGain() == 1f && rack.getTopGain() == 0f,
            "out of range: " + rack.getCrossover() + " / " + rack.getSubGain() + " / " + rack.getTopGain());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fixturesLinkToTheConsoleAndScenesComeBack(GameTestHelper helper) {
        BlockPos console = helper.absolutePos(POS);
        helper.setBlock(POS, ModBlocks.DMX_CONSOLE.get());
        BlockPos headPos = new BlockPos(0, 1, 0);
        helper.setBlock(headPos, ModBlocks.MOVING_HEAD.get());

        ItemStack heads = new ItemStack(ModBlocks.MOVING_HEAD.get(), 4);
        com.createbrewery.block.club.ClubTestAccess.linkFixture(heads, console);
        helper.assertTrue(com.createbrewery.block.club.ClubTestAccess.applyFixtureLink(helper.getLevel(), helper.absolutePos(headPos), heads), "the placed head was not linked");
        FixtureBlockEntity head = helper.getBlockEntity(headPos);
        helper.assertTrue(console.equals(head.getConsole()), "linked to " + head.getConsole());

        DmxConsoleBlockEntity dmx = helper.getBlockEntity(POS);
        helper.assertTrue(com.createbrewery.block.club.ClubTestAccess.sceneRoundTrip(dmx), "a stored scene did not come back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void boothTakesOnlyPlayableRecords(GameTestHelper helper) {
        helper.assertTrue(DjBoothBlock.isMusicDisc(new ItemStack(Items.MUSIC_DISC_CAT)), "a music disc was refused");
        helper.assertFalse(DjBoothBlock.isMusicDisc(new ItemStack(Items.DISC_FRAGMENT_5)), "a disc fragment was taken");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void tapTakesOnlyBeer(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.BEER_TAP.get());
        IFluidHandler tank = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(POS), Direction.UP);
        helper.assertTrue(tank != null, "the tap has no fluid handler");

        helper.assertTrue(tank.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "the tap took lava");
        helper.assertTrue(tank.fill(new FluidStack((net.minecraft.world.level.material.Fluid) ModFluids.BEER.getSource(), 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "the tap refused beer");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void kegStoresBeerAndTapDrawsDirectlyFromIt(GameTestHelper helper) {
        BlockPos kegPos = new BlockPos(2, 1, 2);
        BlockPos tapPos = new BlockPos(2, 2, 2);
        helper.setBlock(kegPos, ModBlocks.BEER_KEG.get());
        helper.setBlock(tapPos, ModBlocks.BEER_TAP.get());

        com.createbrewery.block.KegBlockEntity keg = helper.getBlockEntity(kegPos);
        helper.assertTrue(keg != null, "keg block entity missing");
        var kegTank = keg.getTank().getPrimaryHandler();
        kegTank.fill(new FluidStack((net.minecraft.world.level.material.Fluid) ModFluids.BEER.getSource(), 2000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(kegTank.getFluidAmount() == 2000, "keg did not accept beer");

        com.createbrewery.block.BeerTapBlockEntity tap = helper.getBlockEntity(tapPos);
        helper.assertTrue(tap != null, "tap block entity missing");
        helper.assertTrue(tap.findConnectedKeg() == keg, "tap did not detect connected keg below");

        // Tap dispenses 250 mB of beer drawing directly from the keg!
        helper.assertTrue(tap.dispenseBeer(250), "tap could not dispense from connected keg");
        helper.assertTrue(kegTank.getFluidAmount() == 1750, "keg did not drain: " + kegTank.getFluidAmount());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void placedBeerMugFillsFromBottleAndDrinksOffCounter(GameTestHelper helper) {
        BlockPos mugPos = new BlockPos(2, 1, 2);
        helper.setBlock(mugPos, ModBlocks.BEER_MUG.get());

        var state = helper.getBlockState(mugPos);
        helper.assertTrue(state.getValue(com.createbrewery.block.DrinkGlassBlock.CONTENT) == com.createbrewery.block.DrinkContent.EMPTY,
            "placed mug is not empty");

        // Fill mug with beer
        helper.setBlock(mugPos, state.setValue(com.createbrewery.block.DrinkGlassBlock.CONTENT, com.createbrewery.block.DrinkContent.BEER));
        helper.assertTrue(helper.getBlockState(mugPos).getValue(com.createbrewery.block.DrinkGlassBlock.CONTENT) == com.createbrewery.block.DrinkContent.BEER,
            "mug did not hold beer");

        // Mock player drinks it
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        float before = com.createbrewery.drunk.DrunkServer.state(player).total();
        com.createbrewery.drunk.DrunkServer.drink(player, com.createbrewery.block.DrinkContent.BEER.getPerMille());
        float after = com.createbrewery.drunk.DrunkServer.state(player).total();
        helper.assertTrue(after > before, "drinking gave no alcohol");
        helper.succeed();
    }
}
