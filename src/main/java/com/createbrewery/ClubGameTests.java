package com.createbrewery;

import com.createbrewery.block.club.ClubTestAccess;
import com.createbrewery.block.club.DjBoothBlock;
import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.block.club.DmxConsoleBlockEntity;
import com.createbrewery.block.club.FixtureBlockEntity;
import com.createbrewery.block.club.SpeakerBlock;
import com.createbrewery.block.club.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import com.createbrewery.block.club.AutoSetup;
import com.createbrewery.block.club.AmpSettings;
import com.createbrewery.block.club.AmpRackBlockEntity;
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
        dj.setLoop(b, 128);
        helper.assertTrue(dj.getLoopBeats(b) == DjBoothBlockEntity.MAX_LOOP_BEATS, "an absurd loop length was not clamped to max 64 beats");
        dj.setLoop(b, -5);
        helper.assertTrue(dj.getLoopBeats(b) == 0, "negative loop length did not clear loop");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void autoDropTriggersRedstonePulse(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);
        helper.assertTrue(dj.isAutoDrop(), "auto-drop is not enabled by default");

        // Trigger drop
        dj.triggerDrop(null);
        helper.assertTrue(helper.getBlockState(POS).getValue(DjBoothBlock.POWERED), "drop did not power the booth");
        helper.assertTrue(helper.getLevel().getDirectSignalTo(helper.absolutePos(POS).above()) == 15, "the booth does not emit strong redstone power");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void alphaThetaXdjAzFullSystemWorks(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);

        // 1. 4-Deck architecture verification
        helper.assertTrue(DjBoothBlockEntity.DECKS == 4, "XDJ-AZ does not have 4 decks");
        helper.assertTrue(dj.deckPos(DjBoothBlockEntity.A).equals(helper.absolutePos(POS)), "Deck 1 not at booth");
        helper.assertTrue(dj.deckPos(DjBoothBlockEntity.B).equals(helper.absolutePos(POS).above()), "Deck 2 not at booth above");
        helper.assertTrue(dj.deckPos(DjBoothBlockEntity.C).equals(helper.absolutePos(POS).above(2)), "Deck 3 not at booth above(2)");
        helper.assertTrue(dj.deckPos(DjBoothBlockEntity.D).equals(helper.absolutePos(POS).above(3)), "Deck 4 not at booth above(3)");

        // 2. Sound Color FX (6 modes + Parameter)
        dj.setActiveColorFx(DjBoothBlockEntity.COLOR_NOISE);
        helper.assertTrue(dj.getActiveColorFx() == DjBoothBlockEntity.COLOR_NOISE, "Color FX did not select NOISE");
        dj.setColorFxParam(0.85f);
        helper.assertTrue(Math.abs(dj.getColorFxParam() - 0.85f) < 1e-4, "Color FX param not set");

        // 3. Beat FX Unit
        dj.setBeatFxType(DjBoothBlockEntity.BFX_FLANGER);
        helper.assertTrue(dj.getBeatFxType() == DjBoothBlockEntity.BFX_FLANGER, "Beat FX did not select FLANGER");
        dj.setBeatFxBeats(2.0f);
        helper.assertTrue(Math.abs(dj.getBeatFxBeats() - 2.0f) < 1e-4, "Beat FX beats fraction not set");
        dj.setBeatFxOn(true);
        helper.assertTrue(dj.isBeatFxOn(), "Beat FX not engaged");

        // 4. 4-Channel Mixer Faders & Crossfader Routing
        dj.setChannelFader(DjBoothBlockEntity.C, 0.75f);
        helper.assertTrue(Math.abs(dj.getChannelFader(DjBoothBlockEntity.C) - 0.75f) < 1e-4, "Ch 3 fader not set");
        dj.setCrossfaderAssign(DjBoothBlockEntity.C, 2); // THRU
        helper.assertTrue(dj.getCrossfaderAssign(DjBoothBlockEntity.C) == 2, "Crossfader assign not set to THRU");
        // Gain on THRU channel equals fader * (trim * 2) = 0.75 * 1.0 = 0.75
        helper.assertTrue(Math.abs(dj.deckGain(DjBoothBlockEntity.C, 0) - 0.75f) < 1e-4, "THRU gain does not match fader");
        dj.setTrim(DjBoothBlockEntity.C, 1.0f); // Boost gain to 2x (+6dB)
        helper.assertTrue(Math.abs(dj.deckGain(DjBoothBlockEntity.C, 0) - 1.50f) < 1e-4, "Boosted trim did not double gain");
        dj.setTrim(DjBoothBlockEntity.C, 0.5f); // Reset back to unity

        // 5. Performance Pads: Mode switching & Hot Cue / Loops
        dj.setPadMode(DjBoothBlockEntity.A, DjBoothBlockEntity.PAD_BEAT_LOOP);
        dj.handlePad(DjBoothBlockEntity.A, 3, null); // pad 3 in loops is 8 beats
        helper.assertTrue(dj.getLoopBeats(DjBoothBlockEntity.A) == 8, "Pad did not trigger 8 beat loop");

        // 6. Pitch & Jog Scrub on 4 Decks
        dj.setPitch(DjBoothBlockEntity.C, 1.04f);
        helper.assertTrue(Math.abs(dj.getPitch(DjBoothBlockEntity.C) - 1.04f) < 1e-4, "Pitch on Deck 3 did not set");
        dj.jogScrub(DjBoothBlockEntity.C, 20f, null);

        // 7. Reset: every setting of the deck back to a new booth's
        dj.setEq(DjBoothBlockEntity.C, DjBoothBlockEntity.LOW, 0f);
        dj.setFilter(DjBoothBlockEntity.C, -0.7f);
        dj.setLoop(DjBoothBlockEntity.C, 4);
        dj.setPadMode(DjBoothBlockEntity.C, DjBoothBlockEntity.PAD_HOT_CUE);
        dj.resetDeck(DjBoothBlockEntity.C);
        helper.assertTrue(Math.abs(dj.getPitch(DjBoothBlockEntity.C) - 1f) < 1e-4, "Reset left the pitch");
        helper.assertTrue(dj.getEq(DjBoothBlockEntity.C, DjBoothBlockEntity.LOW) == 0.5f, "Reset left the EQ");
        helper.assertTrue(dj.getFilter(DjBoothBlockEntity.C) == 0f, "Reset left the filter");
        helper.assertTrue(dj.getLoopBeats(DjBoothBlockEntity.C) == 0, "Reset left the loop");
        helper.assertTrue(dj.getChannelFader(DjBoothBlockEntity.C) == 1f && dj.getTrim(DjBoothBlockEntity.C) == 0.5f, "Reset left fader or trim");
        helper.assertTrue(dj.getCrossfaderAssign(DjBoothBlockEntity.C) == 0, "Reset left the crossfader side");
        helper.assertTrue(dj.getPadMode(DjBoothBlockEntity.C) == DjBoothBlockEntity.PAD_BEAT_LOOP, "Reset left the pad mode");
        helper.assertTrue(dj.getLoopBeats(DjBoothBlockEntity.A) == 8, "Reset touched another deck");

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

    private static void link(GameTestHelper helper, BlockPos at, BlockPos booth) {
        ItemStack stack = new ItemStack(helper.getBlockState(at).getBlock().asItem());
        SpeakerBlock.link(stack, booth);
        helper.assertTrue(SpeakerBlock.applyLink(helper.getLevel(), helper.absolutePos(at), stack), "not linked: " + at);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void ampRackSetsUpTheClubAndSortsNewSpeakers(GameTestHelper helper) {
        BlockPos booth = helper.absolutePos(POS);
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        BlockPos rackPos = new BlockPos(4, 1, 0), rack2Pos = new BlockPos(4, 1, 4), subPos = new BlockPos(0, 1, 0);
        BlockPos openPos = new BlockPos(2, 1, 0), wallPos = new BlockPos(2, 1, 3), roomPos = new BlockPos(2, 1, 4), latePos = new BlockPos(0, 1, 2);
        helper.setBlock(rackPos, ModBlocks.AMP_RACK.get());
        helper.setBlock(rack2Pos, ModBlocks.AMP_RACK.get());
        helper.setBlock(subPos, ModBlocks.SUBWOOFER.get());
        helper.setBlock(openPos, ModBlocks.SPEAKER.get().defaultBlockState().setValue(SpeakerBlock.FACING, Direction.SOUTH));
        helper.setBlock(wallPos, Blocks.STONE);
        helper.setBlock(roomPos, ModBlocks.SPEAKER.get().defaultBlockState().setValue(SpeakerBlock.FACING, Direction.NORTH));
        for (BlockPos at : new BlockPos[] {rackPos, rack2Pos, subPos, openPos, roomPos}) link(helper, at, booth);

        AmpRackBlockEntity rack = helper.getBlockEntity(rackPos), rack2 = helper.getBlockEntity(rack2Pos);
        helper.assertTrue(rack.drives() && !rack2.drives(), "the lowest rack must drive the booth");

        AutoSetup.Result r = rack.autoSetup();
        helper.assertTrue(r != null && r.subs() == 1 && r.tops() == 2, "Auto Setup saw " + r);
        AmpSettings s = rack.settings();
        helper.assertTrue(s.zoneOf(helper.absolutePos(openPos).asLong()) == AmpSettings.FLOOR, "open speaker in zone " + s.zoneOf(helper.absolutePos(openPos).asLong()));
        helper.assertTrue(s.zoneOf(helper.absolutePos(roomPos).asLong()) == AmpSettings.ROOM, "walled speaker in zone " + s.zoneOf(helper.absolutePos(roomPos).asLong()));
        helper.assertTrue(s.crossover == 80f && s.align, "crossover " + s.crossover + ", align " + s.align);

        rack.apply(AmpSettings.MASTER, 0, 99f, helper.absolutePos(rackPos), null);
        rack.apply(AmpSettings.ZONE_GAIN, 9, 0f, helper.absolutePos(rackPos), null);
        rack.apply(AmpSettings.ASSIGN, AmpSettings.SUBS, 0f, helper.absolutePos(openPos), null);
        helper.assertTrue(rack.settings().master == AmpSettings.MAX_MASTER, "master " + rack.settings().master);
        helper.assertTrue(rack.settings().zoneOf(helper.absolutePos(openPos).asLong()) == AmpSettings.FLOOR, "a top was put in SUBS");
        rack.apply(AmpSettings.ASSIGN, AmpSettings.DELAY, 0f, helper.absolutePos(openPos), null);
        helper.assertTrue(rack.settings().assign.get(helper.absolutePos(openPos).asLong()).manual(), "a hand-made assignment must be marked manual");

        int[] counts = rack.zoneCounts();
        helper.assertTrue(counts[AmpSettings.FLOOR] == 0 && counts[AmpSettings.DELAY] == 1 && counts[AmpSettings.ROOM] == 1 && counts[AmpSettings.MONITOR] == 1,
            "counts " + java.util.Arrays.toString(counts));

        // A speaker linked later is sorted in by the rack within two seconds; the manual one stays.
        helper.setBlock(latePos, ModBlocks.SPEAKER.get().defaultBlockState().setValue(SpeakerBlock.FACING, Direction.EAST));
        link(helper, latePos, booth);
        helper.runAfterDelay(45, () -> {
            AmpSettings.Assignment late = rack.settings().assign.get(helper.absolutePos(latePos).asLong());
            helper.assertTrue(late != null && late.zone() == AmpSettings.FLOOR && !late.manual(), "late speaker: " + late);
            helper.assertTrue(rack.settings().zoneOf(helper.absolutePos(openPos).asLong()) == AmpSettings.DELAY, "the background sort moved a manual speaker");

            // Saved and loaded, then an old rack's tags migrated.
            var registries = helper.getLevel().registryAccess();
            CompoundTag saved = rack.saveWithoutMetadata(registries);
            rack.loadWithComponents(saved, registries);
            helper.assertTrue(rack.settings().zoneOf(helper.absolutePos(roomPos).asLong()) == AmpSettings.ROOM, "zones lost on save");
            CompoundTag old = new CompoundTag();
            old.putFloat("Crossover", 150f);
            old.putFloat("SubGain", 0f);
            old.putBoolean("MuteTops", true);
            old.putInt("SubCut", 2);
            rack2.loadWithComponents(old, registries);
            AmpSettings m = rack2.settings();
            helper.assertTrue(m.crossover == 150f && m.zones[AmpSettings.SUBS].gain == AmpSettings.MIN_GAIN
                && m.zones[AmpSettings.FLOOR].mute && m.zones[AmpSettings.SUBS].hpf == 40f && m.preset == AmpSettings.CUSTOM, "migration");
            helper.succeed();
        });
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

    private static final BlockPos CONSOLE = new BlockPos(2, 1, 4);

    @GameTest(template = TEMPLATE)
    public static void effectsLinkToTheConsoleAndCycleGroups(GameTestHelper helper) {
        helper.setBlock(CONSOLE, ModBlocks.DMX_CONSOLE.get());
        helper.setBlock(POS, ModBlocks.STROBE_LIGHT.get());
        ItemStack link = new ItemStack(ModBlocks.STROBE_LIGHT.get());
        ClubTestAccess.linkFixture(link, helper.absolutePos(CONSOLE));

        helper.assertTrue(ClubTestAccess.applyEffectLink(helper.getLevel(), helper.absolutePos(POS), link), "the strobe did not link");
        helper.assertTrue(ClubTestAccess.effectGroup(helper.getLevel(), helper.absolutePos(POS)) == 0, "a new link starts at group 1");
        ClubTestAccess.applyEffectLink(helper.getLevel(), helper.absolutePos(POS), link);
        helper.assertTrue(ClubTestAccess.effectGroup(helper.getLevel(), helper.absolutePos(POS)) == 1, "linking again did not move to the next group");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theConsoleRegroupsItsOwnLightsOnly(GameTestHelper helper) {
        helper.setBlock(CONSOLE, ModBlocks.DMX_CONSOLE.get());
        helper.setBlock(POS, ModBlocks.STROBE_LIGHT.get());
        ItemStack link = new ItemStack(ModBlocks.STROBE_LIGHT.get());
        BlockPos console = helper.absolutePos(CONSOLE), light = helper.absolutePos(POS);
        ClubTestAccess.linkFixture(link, console);
        ClubTestAccess.applyEffectLink(helper.getLevel(), light, link);

        helper.assertTrue(ClubTestAccess.linkedLights(helper.getLevel(), console) == 1, "the patch does not list the linked strobe");
        helper.assertTrue(ClubTestAccess.regroup(helper.getLevel(), console, light, 5), "regrouping a linked light failed");
        helper.assertTrue(ClubTestAccess.effectGroup(helper.getLevel(), light) == 5, "the strobe is not in group 6");
        helper.assertFalse(ClubTestAccess.regroup(helper.getLevel(), console, light, 8), "accepted group 9");
        helper.assertFalse(ClubTestAccess.regroup(helper.getLevel(), console.offset(1, 0, 0), light, 2), "regrouped from a console it is not linked to");
        helper.assertTrue(ClubTestAccess.effectGroup(helper.getLevel(), light) == 5, "a refused regroup changed the group");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theHazerButtonFiresLinkedHazersOnly(GameTestHelper helper) {
        BlockPos other = POS.east(2);
        helper.setBlock(CONSOLE, ModBlocks.DMX_CONSOLE.get());
        helper.setBlock(POS, ModBlocks.HAZER.get());
        helper.setBlock(other, ModBlocks.HAZER.get());
        DmxConsoleBlockEntity dmx = helper.getBlockEntity(CONSOLE);
        java.util.List<BlockPos> both = ClubTestAccess.blastHazers(dmx, helper.getLevel());
        helper.assertTrue(both.size() == 2, "with none linked, every hazer near the console should fire");

        ItemStack link = new ItemStack(ModBlocks.HAZER.get());
        ClubTestAccess.linkFixture(link, helper.absolutePos(CONSOLE));
        helper.assertTrue(ClubTestAccess.applyEffectLink(helper.getLevel(), helper.absolutePos(POS), link), "the hazer did not take the link");
        java.util.List<BlockPos> linked = ClubTestAccess.blastHazers(dmx, helper.getLevel());
        helper.assertTrue(linked.equals(java.util.List.of(helper.absolutePos(POS))), "only the linked hazer should fire, got " + linked);
        dmx.triggerHazer(null);
        helper.assertTrue(helper.getBlockState(POS).getValue(com.createbrewery.block.club.HazerBlock.ON), "the linked hazer was not switched on");
        helper.assertFalse(helper.getBlockState(other).getValue(com.createbrewery.block.club.HazerBlock.ON), "an unlinked hazer was switched on");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void thePatchSetsAFixturesBrightnessAndReverse(GameTestHelper helper) {
        helper.setBlock(CONSOLE, ModBlocks.DMX_CONSOLE.get());
        helper.setBlock(POS, ModBlocks.MOVING_HEAD.get());
        BlockPos console = helper.absolutePos(CONSOLE), light = helper.absolutePos(POS);
        ItemStack link = new ItemStack(ModBlocks.MOVING_HEAD.get());
        ClubTestAccess.linkFixture(link, console);
        helper.assertTrue(ClubTestAccess.applyFixtureLink(helper.getLevel(), light, link),
            "the head did not take the link");
        helper.assertTrue(ClubTestAccess.adjustLight(helper.getLevel(), console, light, 1, 6), "brightness refused");
        helper.assertTrue(ClubTestAccess.adjustLight(helper.getLevel(), console, light, 2, 3), "reverse refused");
        int[] di = ClubTestAccess.fixtureDimInvert(helper.getLevel(), light);
        helper.assertTrue(di[0] == 6 && di[1] == 3, "got brightness " + di[0] + " and reverse " + di[1]);
        helper.assertFalse(ClubTestAccess.adjustLight(helper.getLevel(), console, light, 1, 11), "accepted 110 %");
        helper.assertFalse(ClubTestAccess.adjustLight(helper.getLevel(), console.offset(1, 0, 0), light, 1, 5), "adjusted from a console it is not linked to");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void effectGateFollowsBlackoutAndMaster(GameTestHelper helper) {
        helper.setBlock(CONSOLE, ModBlocks.DMX_CONSOLE.get());
        helper.setBlock(POS, ModBlocks.LASER_PROJECTOR.get());
        ItemStack link = new ItemStack(ModBlocks.LASER_PROJECTOR.get());
        ClubTestAccess.linkFixture(link, helper.absolutePos(CONSOLE));
        ClubTestAccess.applyEffectLink(helper.getLevel(), helper.absolutePos(POS), link);
        DmxConsoleBlockEntity dmx = helper.getBlockEntity(CONSOLE);

        helper.assertTrue(ClubTestAccess.effectGate(helper.getLevel(), helper.absolutePos(POS)) > 0.5f, "a linked effect is dark at default faders");
        ClubTestAccess.setBlackout(dmx, true);
        helper.assertTrue(ClubTestAccess.effectGate(helper.getLevel(), helper.absolutePos(POS)) == 0f, "blackout left the laser on");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void effectLinkRefusesAFarConsole(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.FOG_MACHINE.get());
        ItemStack link = new ItemStack(ModBlocks.FOG_MACHINE.get());
        ClubTestAccess.linkFixture(link, helper.absolutePos(POS).offset(500, 0, 0));
        helper.assertFalse(ClubTestAccess.applyEffectLink(helper.getLevel(), helper.absolutePos(POS), link), "linked to a console 500 blocks away");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void effectWithNoLinkAndAnOldSaveStaysOn(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.STROBE_LIGHT.get());
        helper.assertTrue(ClubTestAccess.effectGate(helper.getLevel(), helper.absolutePos(POS)) == 1f, "an unlinked effect is gated");
        // A block entity saved before this change has no Console tag: it must load unlinked.
        ClubTestAccess.loadEffect(helper.getLevel(), helper.absolutePos(POS), new net.minecraft.nbt.CompoundTag());
        helper.assertTrue(ClubTestAccess.effectGate(helper.getLevel(), helper.absolutePos(POS)) == 1f, "an old save came up gated");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brokenConsoleDoesNotLeaveAnEffectDark(GameTestHelper helper) {
        helper.setBlock(CONSOLE, ModBlocks.DMX_CONSOLE.get());
        helper.setBlock(POS, ModBlocks.STROBE_LIGHT.get());
        ItemStack link = new ItemStack(ModBlocks.STROBE_LIGHT.get());
        ClubTestAccess.linkFixture(link, helper.absolutePos(CONSOLE));
        ClubTestAccess.applyEffectLink(helper.getLevel(), helper.absolutePos(POS), link);
        ClubTestAccess.setBlackout(helper.getBlockEntity(CONSOLE), true);
        helper.setBlock(CONSOLE, net.minecraft.world.level.block.Blocks.AIR);
        helper.assertTrue(ClubTestAccess.effectGate(helper.getLevel(), helper.absolutePos(POS)) == 1f, "a destroyed console left the effect dark");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void effectLinkSurvivesSaveAndLoad(GameTestHelper helper) {
        helper.setBlock(CONSOLE, ModBlocks.DMX_CONSOLE.get());
        helper.setBlock(POS, ModBlocks.CO2_JET.get());
        ItemStack link = new ItemStack(ModBlocks.CO2_JET.get());
        ClubTestAccess.linkFixture(link, helper.absolutePos(CONSOLE));
        ClubTestAccess.applyEffectLink(helper.getLevel(), helper.absolutePos(POS), link);
        ClubTestAccess.applyEffectLink(helper.getLevel(), helper.absolutePos(POS), link); // group 2
        net.minecraft.world.level.block.entity.BlockEntity be = helper.getBlockEntity(POS);
        net.minecraft.nbt.CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
        ClubTestAccess.loadEffect(helper.getLevel(), helper.absolutePos(POS), new net.minecraft.nbt.CompoundTag());
        helper.assertTrue(ClubTestAccess.effectGroup(helper.getLevel(), helper.absolutePos(POS)) == 0, "load of an empty tag did not unlink");
        ClubTestAccess.loadEffect(helper.getLevel(), helper.absolutePos(POS), tag);
        helper.assertTrue(ClubTestAccess.effectGroup(helper.getLevel(), helper.absolutePos(POS)) == 1, "the group was not saved");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void lightSettingsWrapAndPersist(GameTestHelper helper) {
        helper.setBlock(CONSOLE, ModBlocks.DMX_CONSOLE.get());
        DmxConsoleBlockEntity dmx = helper.getBlockEntity(CONSOLE);
        helper.assertTrue(ClubTestAccess.lightSettingsRoundTrip(dmx), "the light settings did not survive a save and load");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anOldConsoleTagLoadsLightDefaults(GameTestHelper helper) {
        helper.assertTrue(ClubTestAccess.oldTagDefaults(), "an old save did not come up with today's look");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fixtureFanCyclesWrapsAndSurvivesSaveAndLoad(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.PAR_CAN.get());
        helper.assertTrue(ClubTestAccess.fixtureFan(helper.getLevel(), helper.absolutePos(POS)) == 0, "a new fixture has a fan");
        ClubTestAccess.setFixtureFan(helper.getLevel(), helper.absolutePos(POS), 9);
        helper.assertTrue(ClubTestAccess.fixtureFan(helper.getLevel(), helper.absolutePos(POS)) == 1, "the fan did not wrap");
        net.minecraft.world.level.block.entity.BlockEntity be = helper.getBlockEntity(POS);
        net.minecraft.nbt.CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
        ClubTestAccess.loadEffect(helper.getLevel(), helper.absolutePos(POS), new net.minecraft.nbt.CompoundTag());
        helper.assertTrue(ClubTestAccess.fixtureFan(helper.getLevel(), helper.absolutePos(POS)) == 0, "an old save came up fanned");
        ClubTestAccess.loadEffect(helper.getLevel(), helper.absolutePos(POS), tag);
        helper.assertTrue(ClubTestAccess.fixtureFan(helper.getLevel(), helper.absolutePos(POS)) == 1, "the fan was not saved");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void consoleFindsItsBoothWithoutRec(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        BlockPos consolePos = POS.offset(3, 0, 0);
        helper.setBlock(consolePos, ModBlocks.DMX_CONSOLE.get());
        DmxConsoleBlockEntity dmx = helper.getBlockEntity(consolePos);
        helper.assertTrue(ClubTestAccess.consoleBooth(dmx) == null, "a fresh console follows a booth already");
        ClubTestAccess.tickConsole(dmx);
        helper.assertTrue(helper.absolutePos(POS).equals(ClubTestAccess.consoleBooth(dmx)), "the console did not adopt the booth beside it");

        // And the client gets it with the rest of the console's state.
        net.minecraft.nbt.CompoundTag tag = dmx.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(tag.contains("Booth"), "the booth is not synced to clients");
        helper.succeed();
    }
}
