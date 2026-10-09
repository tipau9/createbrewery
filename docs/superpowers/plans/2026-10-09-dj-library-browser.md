# DJ Booth Library Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the DJ booth's 3-row BROWSE tab with a rekordbox-style library screen (playlists, search, sortable table, BPM memory) whose records are still only the physical discs in containers next to the booth.

**Architecture:** A server-side `DjLibrary` reads the containers around the booth into a snapshot (playlists + entries) and moves discs between containers and decks. The client asks for the snapshot once a second while a new `DjLibraryScreen` is open (`DjLibrarySync` payloads) and filters/sorts it locally with `DjLibrary.view`. BPM is measured on the client, reported through `DjControl`, checked by the booth and stored on the disc as a `TrackInfo` data component.

**Tech Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), Create 6, Etched 5.1 via reflection (`EtchedCompat`), NeoForge GameTests.

**Spec:** `docs/superpowers/specs/2026-10-09-dj-library-browser-design.md`

## Global Constraints

- Physical discs (vanilla records and Etched discs) stay the only way music reaches a deck. No URLs, no virtual tracks.
- A disc is never deleted or duplicated by any load, swap or restock. Fallback order: exact slot → same container → dropped at the booth.
- Playlists: every item-handler container on the booth's N, S, E, W and down sides (in that order, other booths skipped) plus every shulker box item inside one of them.
- BPM accepted only when finite and within 60–200, only for a playing deck that is the only playing deck of the booth, only from a player who can interact with the booth.
- Key detection is out of scope: the KEY column always shows `-`.
- Work in `C:/Users/Anwender/dev/createbrewery`, branch `drugs-apotheke`. Never copy a jar into the Modrinth `mods/` folder while `javaw` runs.
- Build/test commands (from the repo root, Git Bash):
  - compile: `./gradlew compileJava -q --console=plain`
  - tests: `./gradlew runGameTestServer --console=plain 2>&1 | grep -iE "fail|passed|exception|error:|BUILD"` — success prints `All N required tests passed :)` and `BUILD SUCCESSFUL`.

## Deviations from the spec (simpler, same behaviour for the player)

- The browser is its own `Screen` (`DjLibraryScreen`) opened by the BROWSE button, not an overlay panel inside `DjMixerScreen`; Escape/BACK returns to the mixer. Same look, no hiding of ~80 mixer widgets.
- The client pulls a fresh snapshot every 20 ticks while the screen is open instead of the server tracking viewers and pushing on change. No viewer bookkeeping, no logout handling.
- On load, the deck's previous disc goes into the slot the new one came from (a swap) instead of tracking each disc's origin.
- `TrackInfo` stores BPM only. Etched lengths show `--:--` (vanilla lengths come from `JukeboxSong`).
- "Disc on a deck is marked in the table" is dropped: a disc on a deck is no longer in any container.

## Review Focus

1. Player walks away from, or someone breaks, the booth while the browser is open → the browser closes instead of showing a list that silently ignores clicks. (Task 6, manual check step.)
2. Typing `1`–`4` into the search box → the digits go into the search, nothing is loaded. (Task 6, manual check step.)
3. Pitch fader off 0 while the BPM is measured → the stored BPM is the record's own tempo, not the pitched one. (Task 4, `TrackInfo.baseBpm` test.)
4. A shulker box placed as a block next to the booth → it is a playlist like a chest. (Task 1 test.)
5. A container renamed on an anvil → its playlist shows that name. (Task 1 test.)

---

## File Structure

- Create `src/main/java/com/createbrewery/block/club/TrackInfo.java` — data component `createbrewery:track_info {bpm}`, BPM limits, `baseBpm`.
- Create `src/main/java/com/createbrewery/block/club/DjLibrary.java` — library model (Source, Playlist, Entry, Snapshot), snapshot building, take/put-back of discs incl. shulker boxes, `discSlots`, `view` (filter/sort).
- Create `src/main/java/com/createbrewery/block/club/DjLibrarySync.java` — payloads Request/Load (client→server) and Library (server→client), client cache.
- Create `src/main/java/com/createbrewery/block/club/DjLibraryScreen.java` — the browser UI.
- Create `src/main/java/com/createbrewery/block/club/TrackInfoReporter.java` — client BPM measurement → `DjControl.REPORT_BPM`.
- Modify `src/main/java/com/createbrewery/compat/EtchedCompat.java` — add `Meta` + `meta(...)`.
- Modify `src/main/java/com/createbrewery/block/club/DjBoothBlockEntity.java` — `loadFromLibrary`, `acceptTrackInfo`, restock via `DjLibrary`, remember last playlist (NBT), delete `crate()` and `loadFromCrate`.
- Modify `src/main/java/com/createbrewery/block/club/DjControl.java` — remove `LOAD_TRACK`, add `REPORT_BPM`.
- Modify `src/main/java/com/createbrewery/block/club/DjMixerScreen.java` — BROWSE opens `DjLibraryScreen`; delete old browse tab code; `DECK_COLORS` package-private.
- Modify `src/main/java/com/createbrewery/drunk/MusicPulse.java` — call `TrackInfoReporter.check`.
- Modify `src/main/java/com/createbrewery/CreateBrewery.java` — register `TrackInfo` and `DjLibrarySync`.
- Modify `src/main/java/com/createbrewery/block/club/ClubTestAccess.java` — nothing new needed (`restock` already exposed).
- Test: `src/main/java/com/createbrewery/ClubGameTests.java`.

---

### Task 0: Set aside the uncommitted restart-button work

The tree holds the finished, tested restart button (DjControl `RESTART`, `restartDeck`, `|<` button, lang, game test). Ask the user whether to commit it first. If yes:

- [ ] **Step 1: Commit it**

```bash
git add src/main/java/com/createbrewery/block/club/DjBoothBlockEntity.java src/main/java/com/createbrewery/block/club/DjControl.java src/main/java/com/createbrewery/block/club/DjMixerScreen.java src/main/java/com/createbrewery/data/PlayerTextLang.java src/main/resources/assets/createbrewery/lang/de_de.json src/generated/resources/assets/createbrewery/lang/en_us.json src/generated/resources/assets/createbrewery/lang/en_ud.json src/main/java/com/createbrewery/ClubGameTests.java
git commit -m "feat(dj): restart button plays the deck's record from the start"
```

---

### Task 1: TrackInfo component, Etched metadata and the library snapshot

**Files:**
- Create: `src/main/java/com/createbrewery/block/club/TrackInfo.java`
- Create: `src/main/java/com/createbrewery/block/club/DjLibrary.java`
- Modify: `src/main/java/com/createbrewery/compat/EtchedCompat.java` (append before the final `}`)
- Modify: `src/main/java/com/createbrewery/CreateBrewery.java:29`
- Test: `src/main/java/com/createbrewery/ClubGameTests.java`

**Interfaces:**
- Produces:
  - `record TrackInfo(float bpm)`; `TrackInfo.TRACK_INFO` (`DeferredHolder<DataComponentType<?>, DataComponentType<TrackInfo>>`); `TrackInfo.MIN_BPM = 60f`, `TrackInfo.MAX_BPM = 200f`; `static float baseBpm(double beatPeriodSeconds, float pitch)`; `static void register(IEventBus)`.
  - `EtchedCompat.Meta(Component title, String artist, int tracks)`; `static @Nullable Meta meta(ItemStack, HolderLookup.Provider)`.
  - `DjLibrary.Source(Level level, BlockPos pos, IItemHandler items)`; `DjLibrary.Playlist(Component name, int source, int shulker)` with `nested()` and `CODEC`; `DjLibrary.Entry(int playlist, int slot, Component title, String artist, float bpm, int lengthTicks)` with `CODEC`; `DjLibrary.Snapshot(List<Playlist> playlists, List<Entry> entries)`.
  - `static List<Source> DjLibrary.sources(Level, BlockPos booth)`; `static Snapshot DjLibrary.snapshot(Level, BlockPos booth)`; `static boolean DjLibrary.isShulker(ItemStack)`; `static NonNullList<ItemStack> DjLibrary.contents(ItemStack shulker)`.

- [ ] **Step 1: Write the failing test**

Add these imports to `ClubGameTests.java` (next to the existing ones):

```java
import com.createbrewery.block.club.DjLibrary;
import com.createbrewery.block.club.TrackInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import java.util.List;
```

Add the test after `restartPlaysThePausedRecordFromTheStart`:

```java
    @GameTest(template = TEMPLATE)
    public static void libraryListsEveryContainerAndShulkerAroundTheBooth(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        helper.setBlock(POS.north(), Blocks.CHEST);
        helper.setBlock(POS.east(), Blocks.BARREL);
        helper.setBlock(POS.south(), Blocks.SHULKER_BOX);
        ChestBlockEntity chest = helper.getBlockEntity(POS.north());
        chest.setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
        chest.setItem(3, new ItemStack(Items.DIRT));
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CUSTOM_NAME, Component.literal("Techno"));
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(ItemStack.EMPTY, new ItemStack(Items.MUSIC_DISC_13))));
        chest.setItem(5, box);
        BarrelBlockEntity barrel = helper.getBlockEntity(POS.east());
        barrel.applyComponents(DataComponentMap.builder().set(DataComponents.CUSTOM_NAME, Component.literal("House")).build(), DataComponentPatch.EMPTY);
        barrel.setItem(2, new ItemStack(Items.MUSIC_DISC_STAL));
        ShulkerBoxBlockEntity placed = helper.getBlockEntity(POS.south());
        placed.setItem(0, new ItemStack(Items.MUSIC_DISC_MALL));

        DjLibrary.Snapshot lib = DjLibrary.snapshot(helper.getLevel(), helper.absolutePos(POS));
        // chest, placed shulker box (south comes before east), barrel, and the shulker box inside the chest
        helper.assertTrue(lib.playlists().size() == 4, "playlists: " + lib.playlists());
        DjLibrary.Playlist techno = lib.playlists().stream().filter(DjLibrary.Playlist::nested).findFirst().orElseThrow();
        helper.assertTrue(techno.name().getString().equals("Techno") && techno.shulker() == 5, "shulker playlist: " + techno);
        helper.assertTrue(lib.playlists().stream().anyMatch(p -> p.name().getString().equals("House")), "the renamed barrel lost its name: " + lib.playlists());
        helper.assertTrue(lib.entries().size() == 4, "records: " + lib.entries());
        int technoIdx = lib.playlists().indexOf(techno);
        helper.assertTrue(lib.entries().stream().anyMatch(e -> e.playlist() == technoIdx && e.slot() == 1), "the record inside the shulker box is missing");
        helper.assertTrue(lib.entries().stream().allMatch(e -> e.lengthTicks() > 0), "vanilla records know their length");
        helper.succeed();
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew compileJava -q --console=plain`
Expected: compile errors `cannot find symbol ... DjLibrary` and `TrackInfo`.

- [ ] **Step 3: Create `TrackInfo.java`**

```java
package com.createbrewery.block.club;

import com.createbrewery.CreateBrewery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What a booth has learnt about a record by playing it, kept on the record itself so the library
 * shows it wherever the record goes.
 *
 * @param bpm the record's own tempo (pitch fader at 0)
 */
public record TrackInfo(float bpm) {
    /** The tempos a booth believes; outside them the beat detector locked onto half or double time. */
    public static final float MIN_BPM = 60f, MAX_BPM = 200f;

    public static final Codec<TrackInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.FLOAT.fieldOf("bpm").forGetter(TrackInfo::bpm)
    ).apply(i, TrackInfo::new));
    public static final StreamCodec<ByteBuf, TrackInfo> STREAM_CODEC = ByteBufCodecs.FLOAT.map(TrackInfo::new, TrackInfo::bpm);

    private static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateBrewery.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TrackInfo>> TRACK_INFO =
        COMPONENTS.registerComponentType("track_info", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC));

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    /** The record's own tempo from a beat heard every {@code beatPeriodSeconds} while played at {@code pitch}. */
    public static float baseBpm(double beatPeriodSeconds, float pitch) {
        return (float) (60.0 / beatPeriodSeconds / pitch);
    }
}
```

- [ ] **Step 4: Register it** — in `CreateBrewery.java`, after the line `com.createbrewery.drugs.Purity.register(modEventBus);` add:

```java
        com.createbrewery.block.club.TrackInfo.register(modEventBus);
```

- [ ] **Step 5: Add Etched metadata** — in `EtchedCompat.java`, add imports `java.util.Optional` and `org.jetbrains.annotations.Nullable` (skip any already present), then append before the final `}`:

```java
    /** An Etched record's title, artist and number of tracks; an album's own title when it holds several. */
    public record Meta(Component title, String artist, int tracks) {}

    /** Null for anything that is not an Etched record, or when Etched's API cannot be read. */
    @Nullable
    public static Meta meta(ItemStack stack, HolderLookup.Provider registries) {
        if (!isEtchedDisc(stack)) return null;
        try {
            Class<?> pr = Class.forName("gg.moonflower.etched.api.record.PlayableRecord");
            List<?> tracks = (List<?>) pr.getMethod("getTracks", HolderLookup.Provider.class, ItemStack.class).invoke(null, registries, stack);
            if (tracks == null || tracks.isEmpty()) return null;
            Object track = tracks.size() > 1
                ? ((Optional<?>) pr.getMethod("getAlbum", ItemStack.class).invoke(null, stack)).map(o -> (Object) o).orElse(tracks.get(0))
                : tracks.get(0);
            Component title = (Component) track.getClass().getMethod("title").invoke(track);
            String artist = (String) track.getClass().getMethod("artist").invoke(track);
            return new Meta(title == null || title.getString().isBlank() ? stack.getHoverName() : title, artist == null ? "" : artist, tracks.size());
        } catch (Throwable t) {
            return null;
        }
    }
```

- [ ] **Step 6: Create `DjLibrary.java`** (snapshot part; Task 2 and 5 add to this file)

```java
package com.createbrewery.block.club;

import com.createbrewery.compat.EtchedCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The booth's record library, read off the containers around it each time: every container next to
 * the booth is a playlist, every shulker box inside one of them a playlist of its own. Only records
 * count. Nothing is stored here; the records stay where they are until one is loaded onto a deck.
 */
public final class DjLibrary {
    private DjLibrary() {}

    /** Not up: that is where the DJ stands. */
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};

    /** A container next to the booth. */
    public record Source(Level level, BlockPos pos, IItemHandler items) {}

    /**
     * A playlist: container {@code source} (index into {@link #sources}), or the shulker box at slot
     * {@code shulker} inside it (-1: the container itself).
     */
    public record Playlist(Component name, int source, int shulker) {
        static final StreamCodec<RegistryFriendlyByteBuf, Playlist> CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, Playlist::name,
            ByteBufCodecs.VAR_INT, Playlist::source,
            ByteBufCodecs.VAR_INT, Playlist::shulker,
            Playlist::new);

        public boolean nested() {
            return shulker >= 0;
        }
    }

    /** A record at {@code slot} of playlist {@code playlist} (index into the snapshot's playlists); bpm and length 0 when unknown. */
    public record Entry(int playlist, int slot, Component title, String artist, float bpm, int lengthTicks) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Entry::playlist,
            ByteBufCodecs.VAR_INT, Entry::slot,
            ComponentSerialization.STREAM_CODEC, Entry::title,
            ByteBufCodecs.STRING_UTF8, Entry::artist,
            ByteBufCodecs.FLOAT, Entry::bpm,
            ByteBufCodecs.VAR_INT, Entry::lengthTicks,
            Entry::new);
    }

    public record Snapshot(List<Playlist> playlists, List<Entry> entries) {}

    /** The containers next to the booth at {@code booth}, in {@link #SIDES} order; another booth is not a container. */
    public static List<Source> sources(Level level, BlockPos booth) {
        List<Source> out = new ArrayList<>();
        for (Direction side : SIDES) {
            BlockPos at = booth.relative(side);
            if (level.getBlockEntity(at) instanceof DjBoothBlockEntity) continue;
            IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, at, side.getOpposite());
            if (items != null) out.add(new Source(level, at, items));
        }
        return out;
    }

    public static Snapshot snapshot(Level level, BlockPos booth) {
        List<Playlist> playlists = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        List<Source> sources = sources(level, booth);
        for (int s = 0; s < sources.size(); s++) {
            Source source = sources.get(s);
            int own = playlists.size();
            playlists.add(new Playlist(name(level, source.pos()), s, -1));
            IItemHandler items = source.items();
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (DjBoothBlock.isMusicDisc(stack)) {
                    entries.add(entry(level, own, slot, stack));
                } else if (isShulker(stack)) {
                    int inner = playlists.size();
                    playlists.add(new Playlist(stack.getHoverName(), s, slot));
                    NonNullList<ItemStack> contents = contents(stack);
                    for (int i = 0; i < contents.size(); i++) {
                        if (DjBoothBlock.isMusicDisc(contents.get(i))) entries.add(entry(level, inner, i, contents.get(i)));
                    }
                }
            }
        }
        return new Snapshot(playlists, entries);
    }

    /** Its anvil name if it has one, else what it is ("Chest", "Barrel"). */
    private static Component name(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Nameable named ? named.getDisplayName() : level.getBlockState(pos).getBlock().getName();
    }

    private static Entry entry(Level level, int playlist, int slot, ItemStack disc) {
        TrackInfo info = disc.get(TrackInfo.TRACK_INFO.get());
        float bpm = info != null ? info.bpm() : 0f;
        EtchedCompat.Meta meta = EtchedCompat.meta(disc, level.registryAccess());
        if (meta != null) {
            Component title = meta.tracks() > 1 ? meta.title().copy().append(" [" + meta.tracks() + "]") : meta.title();
            // ponytail: Etched lengths are unknown until streamed; add a measured length to TrackInfo if the column matters
            return new Entry(playlist, slot, title, meta.artist(), bpm, 0);
        }
        var song = JukeboxSong.fromStack(level.registryAccess(), disc);
        Component title = song.map(h -> h.value().description()).orElse(disc.getHoverName());
        int length = song.map(h -> h.value().lengthInTicks()).orElse(0);
        return new Entry(playlist, slot, title, "", bpm, length);
    }

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    /** A shulker box item's 27 slots, as a list that may be changed and written back. */
    public static NonNullList<ItemStack> contents(ItemStack shulker) {
        NonNullList<ItemStack> list = NonNullList.withSize(ShulkerBoxBlockEntity.CONTAINER_SIZE, ItemStack.EMPTY);
        shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(list);
        return list;
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew runGameTestServer --console=plain 2>&1 | grep -iE "fail|passed|exception|error:|BUILD"`
Expected: `All 91 required tests passed :)`, `BUILD SUCCESSFUL`. If `libraryLists...` fails on the playlist count, print `lib.playlists()` from the failure message and check the side order (N, S, E, W, down).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/TrackInfo.java src/main/java/com/createbrewery/block/club/DjLibrary.java src/main/java/com/createbrewery/compat/EtchedCompat.java src/main/java/com/createbrewery/CreateBrewery.java src/main/java/com/createbrewery/ClubGameTests.java
git commit -m "feat(dj): read the booth's record library from the containers around it"
```

---

### Task 2: Loading from the library, shulker boxes, and automix following the playlist

**Files:**
- Modify: `src/main/java/com/createbrewery/block/club/DjLibrary.java` (add methods)
- Modify: `src/main/java/com/createbrewery/block/club/DjBoothBlockEntity.java:104-105` (fields), `:307-384` (crate/restock/loadFromCrate), `saveAdditional`/`loadAdditional` (`CrateSlot` lines)
- Modify: `src/main/java/com/createbrewery/block/club/DjControl.java:26,105`
- Test: `src/main/java/com/createbrewery/ClubGameTests.java`

**Interfaces:**
- Consumes: `DjLibrary.Source`, `sources`, `isShulker`, `contents` (Task 1).
- Produces:
  - `static ItemStack DjLibrary.take(List<Source> sources, int source, int shulker, int slot)` — the disc, or `ItemStack.EMPTY` if that address holds no record now.
  - `static void DjLibrary.putBack(Level level, BlockPos booth, List<Source> sources, int source, int shulker, int slot, ItemStack disc)` — never loses the disc.
  - `static List<Integer> DjLibrary.discSlots(List<Source> sources, int source, int shulker)`.
  - `public boolean DjBoothBlockEntity.loadFromLibrary(int deck, int source, int shulker, int slot, @Nullable Player player)`.
  - `DjBoothBlockEntity.restock(int deck)` keeps its signature (package-private, `boolean`).

- [ ] **Step 1: Write the failing tests** (add after `libraryListsEveryContainerAndShulkerAroundTheBooth`)

```java
    @GameTest(template = TEMPLATE)
    public static void loadingSwapsTheRecordWithItsSlotAndSkipsStaleSlots(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        helper.setBlock(POS.north(), Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(POS.north());
        chest.setItem(4, new ItemStack(Items.MUSIC_DISC_CAT));
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);
        dj.insertDisc(new ItemStack(Items.MUSIC_DISC_STAL), false, null);

        helper.assertTrue(dj.loadFromLibrary(DjBoothBlockEntity.B, 0, -1, 4, null), "nothing loaded onto the empty deck");
        helper.assertTrue(dj.getDisc(DjBoothBlockEntity.B).is(Items.MUSIC_DISC_CAT) && chest.getItem(4).isEmpty(), "cat did not move onto deck B");
        // The browser's list was made before the slot emptied: nothing happens, nothing is lost.
        helper.assertFalse(dj.loadFromLibrary(DjBoothBlockEntity.A, 0, -1, 4, null), "loaded from an empty slot");
        helper.assertTrue(dj.getDisc(DjBoothBlockEntity.A).is(Items.MUSIC_DISC_STAL), "deck A lost its record");

        chest.setItem(4, new ItemStack(Items.MUSIC_DISC_13));
        helper.assertTrue(dj.loadFromLibrary(DjBoothBlockEntity.A, 0, -1, 4, null), "13 not loaded");
        helper.assertTrue(dj.getDisc(DjBoothBlockEntity.A).is(Items.MUSIC_DISC_13), "deck A has " + dj.getDisc(DjBoothBlockEntity.A));
        helper.assertTrue(chest.getItem(4).is(Items.MUSIC_DISC_STAL), "stal did not take 13's slot: " + chest.getItem(4));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void loadingFromAShulkerBoxRewritesItsContents(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        helper.setBlock(POS.north(), Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(POS.north());
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.MUSIC_DISC_CAT))));
        chest.setItem(2, box);
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);
        dj.insertDisc(new ItemStack(Items.MUSIC_DISC_STAL), false, null);

        helper.assertTrue(dj.loadFromLibrary(DjBoothBlockEntity.A, 0, 2, 0, null), "nothing loaded from the shulker box");
        helper.assertTrue(dj.getDisc(DjBoothBlockEntity.A).is(Items.MUSIC_DISC_CAT), "deck A has " + dj.getDisc(DjBoothBlockEntity.A));
        ItemStack after = chest.getItem(2);
        helper.assertTrue(DjLibrary.isShulker(after), "the shulker box left its slot: " + after);
        helper.assertTrue(DjLibrary.contents(after).get(0).is(Items.MUSIC_DISC_STAL), "stal is not in the shulker box: " + DjLibrary.contents(after));
        int items = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (!chest.getItem(i).isEmpty()) items++;
        helper.assertTrue(items == 1, "the chest holds " + items + " stacks, not just the shulker box");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aRecordWithNowhereToGoIsDroppedNotLost(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        helper.setBlock(POS.north(), Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(POS.north());
        // A full shulker box first: the record goes into the chest instead.
        NonNullList<ItemStack> dirt = NonNullList.withSize(27, new ItemStack(Items.DIRT));
        ItemStack fullBox = new ItemStack(Items.SHULKER_BOX);
        fullBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(dirt));
        chest.setItem(0, fullBox);
        var sources = DjLibrary.sources(helper.getLevel(), helper.absolutePos(POS));
        DjLibrary.putBack(helper.getLevel(), helper.absolutePos(POS), sources, 0, 0, 3, new ItemStack(Items.MUSIC_DISC_CAT));
        boolean inChest = false;
        for (int i = 1; i < chest.getContainerSize(); i++) inChest |= chest.getItem(i).is(Items.MUSIC_DISC_CAT);
        helper.assertTrue(inChest, "cat did not go into the chest next to the full shulker box");

        // Then everything full: dropped at the booth.
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.DIRT));
        DjLibrary.putBack(helper.getLevel(), helper.absolutePos(POS), sources, 0, -1, 0, new ItemStack(Items.MUSIC_DISC_13));
        helper.assertItemEntityPresent(Items.MUSIC_DISC_13, POS, 2.0);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void automixRestocksFromThePlaylistLastLoadedFrom(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        helper.setBlock(POS.north(), Blocks.CHEST);
        helper.setBlock(POS.east(), Blocks.BARREL);
        ChestBlockEntity chest = helper.getBlockEntity(POS.north());
        chest.setItem(0, new ItemStack(Items.MUSIC_DISC_13));
        BarrelBlockEntity barrel = helper.getBlockEntity(POS.east());
        barrel.setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
        barrel.setItem(1, new ItemStack(Items.MUSIC_DISC_STAL));
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);

        // Sources: chest (north) = 0, barrel (east) = 1.
        helper.assertTrue(dj.loadFromLibrary(DjBoothBlockEntity.A, 1, -1, 0, null), "cat not loaded from the barrel");
        helper.assertTrue(com.createbrewery.block.club.ClubTestAccess.restock(dj, DjBoothBlockEntity.A), "nothing restocked");
        helper.assertTrue(dj.getDisc(DjBoothBlockEntity.A).is(Items.MUSIC_DISC_STAL), "restocked " + dj.getDisc(DjBoothBlockEntity.A) + " instead of the barrel's next record");
        helper.assertTrue(barrel.getItem(1).is(Items.MUSIC_DISC_CAT) && chest.getItem(0).is(Items.MUSIC_DISC_13), "records ended up in the wrong place");
        helper.succeed();
    }
```

Also add `import net.minecraft.core.NonNullList;` to the test imports.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew compileJava -q --console=plain`
Expected: `cannot find symbol ... loadFromLibrary`, `putBack`.

- [ ] **Step 3: Add take/putBack/discSlots to `DjLibrary.java`**

Add imports `net.minecraft.world.Containers` and `net.neoforged.neoforge.items.ItemHandlerHelper`, then add before `isShulker`:

```java
    /**
     * Takes the record at the address: slot {@code slot} of container {@code source}, or of the
     * shulker box at {@code shulker} inside it. Empty, with nothing changed, if no record is there
     * any more (the browser's list can be a second old).
     */
    public static ItemStack take(List<Source> sources, int source, int shulker, int slot) {
        if (source < 0 || source >= sources.size()) return ItemStack.EMPTY;
        Source src = sources.get(source);
        IItemHandler items = src.items();
        if (shulker < 0) {
            if (slot < 0 || slot >= items.getSlots() || !DjBoothBlock.isMusicDisc(items.getStackInSlot(slot))) return ItemStack.EMPTY;
            return items.extractItem(slot, 1, false);
        }
        if (shulker >= items.getSlots()) return ItemStack.EMPTY;
        ItemStack box = items.getStackInSlot(shulker);
        if (!isShulker(box)) return ItemStack.EMPTY;
        NonNullList<ItemStack> contents = contents(box);
        if (slot < 0 || slot >= contents.size() || !DjBoothBlock.isMusicDisc(contents.get(slot))) return ItemStack.EMPTY;
        ItemStack disc = contents.get(slot);
        contents.set(slot, ItemStack.EMPTY);
        return replaceBox(src, shulker, box, contents) ? disc : ItemStack.EMPTY;
    }

    /** Puts {@code disc} at the address if that slot is free, else anywhere in that container, else drops it at the booth. */
    public static void putBack(Level level, BlockPos booth, List<Source> sources, int source, int shulker, int slot, ItemStack disc) {
        if (source >= 0 && source < sources.size()) {
            Source src = sources.get(source);
            IItemHandler items = src.items();
            if (shulker >= 0) {
                if (shulker < items.getSlots() && isShulker(items.getStackInSlot(shulker))) {
                    ItemStack box = items.getStackInSlot(shulker);
                    NonNullList<ItemStack> contents = contents(box);
                    if (slot >= 0 && slot < contents.size() && contents.get(slot).isEmpty()) {
                        contents.set(slot, disc);
                        if (replaceBox(src, shulker, box, contents)) return;
                    }
                }
            } else if (slot >= 0 && slot < items.getSlots()) {
                disc = items.insertItem(slot, disc, false);
                if (disc.isEmpty()) return;
            }
            disc = ItemHandlerHelper.insertItem(items, disc, false);
            if (disc.isEmpty()) return;
        }
        Containers.dropItemStack(level, booth.getX() + 0.5, booth.getY() + 1.0, booth.getZ() + 0.5, disc);
    }

    /** The slots holding records in container {@code source}, or in the shulker box at {@code shulker} inside it. */
    public static List<Integer> discSlots(List<Source> sources, int source, int shulker) {
        List<Integer> out = new ArrayList<>();
        if (source < 0 || source >= sources.size()) return out;
        IItemHandler items = sources.get(source).items();
        if (shulker < 0) {
            for (int i = 0; i < items.getSlots(); i++) if (DjBoothBlock.isMusicDisc(items.getStackInSlot(i))) out.add(i);
        } else if (shulker < items.getSlots() && isShulker(items.getStackInSlot(shulker))) {
            NonNullList<ItemStack> contents = contents(items.getStackInSlot(shulker));
            for (int i = 0; i < contents.size(); i++) if (DjBoothBlock.isMusicDisc(contents.get(i))) out.add(i);
        }
        return out;
    }

    /**
     * Swaps the shulker box at {@code slot} for a copy holding {@code contents}. False, with the
     * original back in place, if the container will not take the copy.
     */
    private static boolean replaceBox(Source src, int slot, ItemStack box, NonNullList<ItemStack> contents) {
        ItemStack updated = box.copyWithCount(1);
        updated.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        ItemStack original = src.items().extractItem(slot, 1, false);
        if (original.isEmpty()) return false;
        if (src.items().insertItem(slot, updated, false).isEmpty()) return true;
        ItemStack left = src.items().insertItem(slot, original, false);
        if (!left.isEmpty()) Containers.dropItemStack(src.level(), src.pos().getX() + 0.5, src.pos().getY() + 1.0, src.pos().getZ() + 0.5, left);
        return false;
    }
```

- [ ] **Step 4: Change `DjBoothBlockEntity`**

Replace the field block at lines 104-105:

```java
    /** The record crate's slot the last record was taken from: the next one is looked for after it. */
    private int crateSlot = -1;
```

with:

```java
    /** The playlist the last record was loaded from (container, shulker box in it or -1) and its slot: automix carries on after it. */
    private int libSource = 0, libShulker = -1, crateSlot = -1;
```

Delete the whole `crate()` method, `restock(int deck)` and `loadFromCrate(...)` (the block from the `/** The record crate: ...` javadoc through the end of `loadFromCrate`, lines ~307-384) and put this in their place:

```java
    /**
     * Swaps the record on {@code deck} for the next one in the playlist last loaded from (the first
     * container until something was loaded). A record is never lost: the old one takes the new one's slot.
     * Server only.
     */
    boolean restock(int deck) {
        if (level == null) return false;
        var sources = DjLibrary.sources(level, worldPosition);
        var slots = DjLibrary.discSlots(sources, libSource, libShulker);
        if (slots.isEmpty() && (libSource != 0 || libShulker != -1)) {
            // That playlist is gone or empty: back to the first container.
            libSource = 0;
            libShulker = -1;
            crateSlot = -1;
            slots = DjLibrary.discSlots(sources, libSource, libShulker);
        }
        if (slots.isEmpty()) return false;
        int next = slots.get(0);
        for (int slot : slots) {
            if (slot > crateSlot) {
                next = slot;
                break;
            }
        }
        return swapIn(deck, sources, libSource, libShulker, next);
    }

    /** Loads the record at a library address onto {@code deck}; the deck's record takes its slot. Server only. */
    public boolean loadFromLibrary(int deck, int source, int shulker, int slot, @Nullable Player player) {
        if (level == null || level.isClientSide || deck < 0 || deck >= DECKS) return false;
        stopDeckIfLoaded(deck, source, shulker, slot);
        if (!swapIn(deck, DjLibrary.sources(level, worldPosition), source, shulker, slot)) return false;
        sync();
        if (player != null) {
            player.displayClientMessage(Component.translatable("createbrewery.dj.cued", name(deck), title(decks[deck].disc)), true);
        }
        return true;
    }

    /** A deck changing records stops first, but only once there really is a record to change to. */
    private void stopDeckIfLoaded(int deck, int source, int shulker, int slot) {
        if (!DjLibrary.discSlots(DjLibrary.sources(level, worldPosition), source, shulker).contains(slot)) return;
        stopDeck(deck);
    }

    private boolean swapIn(int deck, java.util.List<DjLibrary.Source> sources, int source, int shulker, int slot) {
        ItemStack next = DjLibrary.take(sources, source, shulker, slot);
        if (next.isEmpty()) return false;
        ItemStack old = decks[deck].disc;
        if (!old.isEmpty()) DjLibrary.putBack(level, worldPosition, sources, source, shulker, slot, old);
        decks[deck].disc = next;
        decks[deck].mainCue = 0;
        java.util.Arrays.fill(decks[deck].hotCues, -1L);
        libSource = source;
        libShulker = shulker;
        crateSlot = slot;
        return true;
    }
```

If `@Nullable` is not imported in this file yet, use `@org.jetbrains.annotations.Nullable` or the import already used for `crate()` (`grep -n "Nullable" DjBoothBlockEntity.java`).

In `saveAdditional`, after `tag.putInt("CrateSlot", crateSlot);` add:

```java
        tag.putInt("LibSource", libSource);
        tag.putInt("LibShulker", libShulker);
```

In `loadAdditional`, after the `crateSlot = ...` line add:

```java
        libSource = Math.max(0, tag.getInt("LibSource"));
        libShulker = tag.contains("LibShulker") ? tag.getInt("LibShulker") : -1;
```

- [ ] **Step 5: Remove `LOAD_TRACK` from `DjControl.java`**

Line 26: change `PAD_TRIGGER = 26, LOAD_TRACK = 27, VINYL_MODE = 28` to `PAD_TRIGGER = 26, VINYL_MODE = 28` (27 stays unused so other numbers keep their meaning).
Line 105: delete `case LOAD_TRACK -> dj.loadFromCrate(deck, (int) control.value, player);`.

`DjMixerScreen` still calls `dj.crate()` and sends `LOAD_TRACK`; Task 6 deletes that code. To compile now, delete it here already: in `DjMixerScreen.java` delete the `browseOffset` field (line 71), the `else if (screenTab == 1) { ... renderBrowseTab(...); }` branch (lines 1060-1062), the whole `renderBrowseTab` method, and the whole `mouseClicked` and `mouseScrolled` overrides (each only handles `screenTab == 1` before calling `super`). Check nothing else references them:

Run: `grep -n "crate()\|browseOffset\|renderBrowseTab\|LOAD_TRACK\|loadFromCrate" -r src/main/java`
Expected: no output.

- [ ] **Step 6: Run the tests**

Run: `./gradlew runGameTestServer --console=plain 2>&1 | grep -iE "fail|passed|exception|error:|BUILD"`
Expected: `All 95 required tests passed :)`. The older `crateRestocks...` test (the one at the former line ~218, record order 13, cat, stal, 13) must still pass: restock now swaps the old record into the slot the new one left, which keeps that order.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/DjLibrary.java src/main/java/com/createbrewery/block/club/DjBoothBlockEntity.java src/main/java/com/createbrewery/block/club/DjControl.java src/main/java/com/createbrewery/block/club/DjMixerScreen.java src/main/java/com/createbrewery/ClubGameTests.java
git commit -m "feat(dj): load records from any container or shulker box around the booth"
```

---

### Task 3: Library payloads

**Files:**
- Create: `src/main/java/com/createbrewery/block/club/DjLibrarySync.java`
- Modify: `src/main/java/com/createbrewery/CreateBrewery.java:488`

**Interfaces:**
- Consumes: `DjLibrary.snapshot`, `Playlist.CODEC`, `Entry.CODEC`, `Snapshot` (Task 1); `DjBoothBlockEntity.loadFromLibrary` (Task 2).
- Produces (client side, used by Task 6): `static void DjLibrarySync.request(BlockPos pos)`; `static void DjLibrarySync.load(BlockPos pos, int deck, int source, int shulker, int slot)`; `static @Nullable DjLibrary.Snapshot DjLibrarySync.received(BlockPos pos)`.

No GameTest: payload handlers need a connected client. The checks they run (`canInteractWithBlock`, `loadFromLibrary`'s re-validation) are covered by Task 2 and verified in game in Task 6.

- [ ] **Step 1: Create `DjLibrarySync.java`**

```java
package com.createbrewery.block.club;

import com.createbrewery.CreateBrewery;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The booth's library between server and browser: the browser asks for it while open (about once a
 * second) and asks for records to be loaded; the server, which can see inside the containers, answers.
 */
public final class DjLibrarySync {
    private DjLibrarySync() {}

    /** Client → server: send me this booth's library. */
    public record Request(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CreateBrewery.ID("dj_library_request"));
        static final StreamCodec<ByteBuf, Request> CODEC = BlockPos.STREAM_CODEC.map(Request::new, Request::pos);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** Client → server: load the record at this library address onto {@code deck}. */
    public record Load(BlockPos pos, int deck, int source, int shulker, int slot) implements CustomPacketPayload {
        public static final Type<Load> TYPE = new Type<>(CreateBrewery.ID("dj_library_load"));
        static final StreamCodec<ByteBuf, Load> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Load::pos,
            ByteBufCodecs.VAR_INT, Load::deck,
            ByteBufCodecs.VAR_INT, Load::source,
            ByteBufCodecs.VAR_INT, Load::shulker,
            ByteBufCodecs.VAR_INT, Load::slot,
            Load::new);

        @Override
        public Type<Load> type() {
            return TYPE;
        }
    }

    /** Server → client: the library of the booth at {@code pos}. */
    public record Library(BlockPos pos, List<DjLibrary.Playlist> playlists, List<DjLibrary.Entry> entries) implements CustomPacketPayload {
        public static final Type<Library> TYPE = new Type<>(CreateBrewery.ID("dj_library"));
        static final StreamCodec<RegistryFriendlyByteBuf, Library> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Library::pos,
            DjLibrary.Playlist.CODEC.apply(ByteBufCodecs.list()), Library::playlists,
            DjLibrary.Entry.CODEC.apply(ByteBufCodecs.list()), Library::entries,
            Library::new);

        @Override
        public Type<Library> type() {
            return TYPE;
        }
    }

    /** Client: the last library received per booth. */
    private static final Map<BlockPos, DjLibrary.Snapshot> RECEIVED = new ConcurrentHashMap<>();

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Request.TYPE, Request.CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player && booth(player, request.pos()) != null) send(player, request.pos());
        });
        registrar.playToServer(Load.TYPE, Load.CODEC, (load, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DjBoothBlockEntity dj = booth(player, load.pos());
            if (dj == null) return;
            dj.loadFromLibrary(load.deck(), load.source(), load.shulker(), load.slot(), player);
            send(player, load.pos());
        });
        registrar.playToClient(Library.TYPE, Library.CODEC,
            (library, context) -> RECEIVED.put(library.pos(), new DjLibrary.Snapshot(library.playlists(), library.entries())));
    }

    /** The booth at {@code pos}, if {@code player} stands at it. */
    @Nullable
    private static DjBoothBlockEntity booth(ServerPlayer player, BlockPos pos) {
        if (!player.level().isLoaded(pos) || !player.canInteractWithBlock(pos, 1.0)) return null;
        return player.level().getBlockEntity(pos) instanceof DjBoothBlockEntity dj ? dj : null;
    }

    private static void send(ServerPlayer player, BlockPos pos) {
        DjLibrary.Snapshot snapshot = DjLibrary.snapshot(player.level(), pos);
        PacketDistributor.sendToPlayer(player, new Library(pos, snapshot.playlists(), snapshot.entries()));
    }

    /** Client side. */
    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new Request(pos));
    }

    /** Client side. */
    public static void load(BlockPos pos, int deck, int source, int shulker, int slot) {
        PacketDistributor.sendToServer(new Load(pos, deck, source, shulker, slot));
    }

    /** Client side: null until the server has answered once. */
    @Nullable
    public static DjLibrary.Snapshot received(BlockPos pos) {
        return RECEIVED.get(pos);
    }
}
```

- [ ] **Step 2: Register it** — in `CreateBrewery.java`, after `modEventBus.addListener(com.createbrewery.block.club.DjControl::register);` add:

```java
        modEventBus.addListener(com.createbrewery.block.club.DjLibrarySync::register);
```

- [ ] **Step 3: Compile and run the tests**

Run: `./gradlew compileJava -q --console=plain && ./gradlew runGameTestServer --console=plain 2>&1 | grep -iE "fail|passed|exception|error:|BUILD"`
Expected: no compile output; `All 95 required tests passed :)` (the game test server also registers payloads, so a broken codec shows up as an exception here).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/DjLibrarySync.java src/main/java/com/createbrewery/CreateBrewery.java
git commit -m "feat(dj): send the booth's library to the browser and load records from it"
```

---

### Task 4: BPM memory

**Files:**
- Modify: `src/main/java/com/createbrewery/block/club/DjBoothBlockEntity.java` (add `acceptTrackInfo` after `restartDeck`)
- Modify: `src/main/java/com/createbrewery/block/club/DjControl.java` (constant + case)
- Create: `src/main/java/com/createbrewery/block/club/TrackInfoReporter.java`
- Modify: `src/main/java/com/createbrewery/drunk/MusicPulse.java:735` (after `t.deckIndex = deck;` in `deck(Track, Minecraft)`)
- Test: `src/main/java/com/createbrewery/ClubGameTests.java`

**Interfaces:**
- Consumes: `TrackInfo` (Task 1).
- Produces: `public boolean DjBoothBlockEntity.acceptTrackInfo(int deck, float bpm)`; `DjControl.REPORT_BPM = 44`; `public static void TrackInfoReporter.check(DjBoothBlockEntity dj, int deck, MusicPulse.Track t)`.

- [ ] **Step 1: Write the failing test**

```java
    @GameTest(template = TEMPLATE)
    public static void aRecordRemembersItsTempoOnlyWhenPlayedAlone(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        DjBoothBlockEntity dj = helper.getBlockEntity(POS);
        dj.insertDisc(new ItemStack(Items.MUSIC_DISC_CAT), false, null);

        helper.assertFalse(dj.acceptTrackInfo(DjBoothBlockEntity.A, 250f), "took a tempo above 200 BPM");
        helper.assertFalse(dj.acceptTrackInfo(DjBoothBlockEntity.A, Float.NaN), "took NaN");
        helper.assertTrue(dj.acceptTrackInfo(DjBoothBlockEntity.A, 128f), "refused the lone deck's tempo");
        TrackInfo info = dj.getDisc(DjBoothBlockEntity.A).get(TrackInfo.TRACK_INFO.get());
        helper.assertTrue(info != null && info.bpm() == 128f, "record holds " + info);

        dj.insertDisc(new ItemStack(Items.MUSIC_DISC_13), true, null);
        helper.assertFalse(dj.acceptTrackInfo(DjBoothBlockEntity.B, 120f), "took the tempo of a deck that is not playing");
        dj.toggleDeck(DjBoothBlockEntity.B, null);
        helper.assertFalse(dj.acceptTrackInfo(DjBoothBlockEntity.B, 120f), "took a tempo while two decks play into the detector");
        helper.assertFalse(dj.getDisc(DjBoothBlockEntity.B).has(TrackInfo.TRACK_INFO.get()), "13 got a tempo anyway");

        // Heard at +8 % pitch, a 0.5 s beat is a 120 BPM record, not 128.
        helper.assertTrue(Math.abs(TrackInfo.baseBpm(0.5 / 1.08, 1.08f) - 120f) < 0.01f, "pitch not taken out: " + TrackInfo.baseBpm(0.5 / 1.08, 1.08f));
        helper.succeed();
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew compileJava -q --console=plain`
Expected: `cannot find symbol ... acceptTrackInfo`.

- [ ] **Step 3: Add `acceptTrackInfo` to `DjBoothBlockEntity`** (after `restartDeck`)

```java
    /**
     * A tempo measured by a client for the record on {@code deck}, kept on the record. Only believed
     * for the booth's one playing deck: with two playing, the beat detector hears both.
     */
    public boolean acceptTrackInfo(int deck, float bpm) {
        if (deck < 0 || deck >= DECKS || !Float.isFinite(bpm) || bpm < TrackInfo.MIN_BPM || bpm > TrackInfo.MAX_BPM) return false;
        Deck d = decks[deck];
        if (!d.playing || d.disc.isEmpty()) return false;
        for (int i = 0; i < DECKS; i++) if (i != deck && decks[i].playing) return false;
        d.disc.set(TrackInfo.TRACK_INFO.get(), new TrackInfo(bpm));
        sync();
        return true;
    }
```

- [ ] **Step 4: Add the action to `DjControl.java`**

After `public static final byte RESTART = 43;` add:

```java
    /** A record's tempo measured by the client, at pitch 0 ({@link DjBoothBlockEntity#acceptTrackInfo}). */
    public static final byte REPORT_BPM = 44;
```

After `case RESTART -> dj.restartDeck(deck, player);` add:

```java
            case REPORT_BPM -> dj.acceptTrackInfo(deck, control.value);
```

(The handler's existing `canInteractWithBlock(control.pos, 1.0)` check already runs before the switch.)

- [ ] **Step 5: Create `TrackInfoReporter.java`**

```java
package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Client: tells the booth a record's tempo once it has played alone long enough to be sure of it.
 * The beat detector hears everything at once, so only a booth's lone playing deck is measured.
 */
public final class TrackInfoReporter {
    private TrackInfoReporter() {}

    /** Seconds a record plays before its tempo counts; beats the tempo must hold within {@link #STEADY}. */
    private static final double MIN_SECONDS = 30.0;
    private static final int STEADY_BEATS = 8;
    private static final double STEADY = 0.01;

    private static BlockPos boothAt;
    private static int deckAt = -1;
    private static double heldPeriod;
    private static int heldSince = -1;

    /** Once a frame per deck track (MusicPulse). */
    public static void check(DjBoothBlockEntity dj, int deck, MusicPulse.Track t) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack disc = dj.getDisc(deck);
        if (mc.player == null || disc.isEmpty() || disc.has(TrackInfo.TRACK_INFO.get()) || !dj.isPlaying(deck)
            || !mc.player.canInteractWithBlock(dj.getBlockPos(), 1.0)) return;
        for (int i = 0; i < DjBoothBlockEntity.DECKS; i++) if (i != deck && dj.isPlaying(i)) return;
        if (!dj.getBlockPos().equals(boothAt) || deck != deckAt) {
            boothAt = dj.getBlockPos();
            deckAt = deck;
            heldSince = -1;
        }
        if (t.rate <= 0 || t.deckFrame / (double) t.rate < MIN_SECONDS) return;
        double period = t.getBeatPeriod();
        int beats = t.getBeats();
        if (heldSince < 0 || Math.abs(period - heldPeriod) > heldPeriod * STEADY) {
            heldPeriod = period;
            heldSince = beats;
            return;
        }
        if (beats - heldSince < STEADY_BEATS) return;
        heldSince = -1;
        DjControl.send(dj.getBlockPos(), DjControl.REPORT_BPM, deck, TrackInfo.baseBpm(period, dj.getPitch(deck)));
    }
}
```

- [ ] **Step 6: Hook it into `MusicPulse.deck`** — after the line `t.deckIndex = deck;` (inside `private static void deck(Track t, Minecraft mc)`, ~line 735) add:

```java
        com.createbrewery.block.club.TrackInfoReporter.check(dj, deck, t);
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew runGameTestServer --console=plain 2>&1 | grep -iE "fail|passed|exception|error:|BUILD"`
Expected: `All 96 required tests passed :)`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/DjBoothBlockEntity.java src/main/java/com/createbrewery/block/club/DjControl.java src/main/java/com/createbrewery/block/club/TrackInfoReporter.java src/main/java/com/createbrewery/drunk/MusicPulse.java src/main/java/com/createbrewery/ClubGameTests.java
git commit -m "feat(dj): records remember their tempo once played alone at the booth"
```

---

### Task 5: Search and sort

**Files:**
- Modify: `src/main/java/com/createbrewery/block/club/DjLibrary.java`
- Test: `src/main/java/com/createbrewery/ClubGameTests.java`

**Interfaces:**
- Consumes: `Snapshot`, `Entry` (Task 1).
- Produces: `enum DjLibrary.Sort { INDEX, TITLE, ARTIST, BPM, LENGTH }`; `static List<Entry> DjLibrary.view(Snapshot snapshot, int playlist, String query, Sort sort, boolean descending)` — `playlist` -1 means all.

- [ ] **Step 1: Write the failing test**

```java
    @GameTest(template = TEMPLATE)
    public static void libraryViewSearchesAndSorts(GameTestHelper helper) {
        var playlists = List.of(
            new DjLibrary.Playlist(Component.literal("Crate"), 0, -1),
            new DjLibrary.Playlist(Component.literal("Trance"), 0, 4));
        var entries = List.of(
            new DjLibrary.Entry(0, 0, Component.literal("Strobe"), "deadmau5", 128f, 0),
            new DjLibrary.Entry(0, 1, Component.literal("Around the World"), "Daft Punk", 121f, 0),
            new DjLibrary.Entry(1, 0, Component.literal("Sandstorm"), "Darude", 136f, 0));
        DjLibrary.Snapshot lib = new DjLibrary.Snapshot(playlists, entries);

        helper.assertTrue(DjLibrary.view(lib, -1, "", DjLibrary.Sort.INDEX, false).size() == 3, "ALL TRACKS is not everything");
        helper.assertTrue(DjLibrary.view(lib, 0, "", DjLibrary.Sort.INDEX, false).size() == 2, "a playlist shows another's records");
        var daft = DjLibrary.view(lib, -1, "daft", DjLibrary.Sort.INDEX, false);
        helper.assertTrue(daft.size() == 1 && daft.get(0).slot() == 1 && daft.get(0).playlist() == 0, "artist search: " + daft);
        var storm = DjLibrary.view(lib, -1, "  STORM ", DjLibrary.Sort.INDEX, false);
        helper.assertTrue(storm.size() == 1 && storm.get(0).playlist() == 1, "title search: " + storm);
        helper.assertTrue(DjLibrary.view(lib, -1, "", DjLibrary.Sort.BPM, false).get(0).bpm() == 121f, "BPM ascending");
        helper.assertTrue(DjLibrary.view(lib, -1, "", DjLibrary.Sort.BPM, true).get(0).bpm() == 136f, "BPM descending");
        helper.assertTrue(DjLibrary.view(lib, -1, "", DjLibrary.Sort.TITLE, false).get(0).title().getString().equals("Around the World"), "title sort");
        helper.succeed();
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew compileJava -q --console=plain`
Expected: `cannot find symbol ... Sort` / `view`.

- [ ] **Step 3: Implement** — add imports `java.util.Collections`, `java.util.Comparator`, `java.util.Locale` to `DjLibrary.java`, then add after `Snapshot`:

```java
    /** The browser's sort columns; INDEX is the order the records sit in their containers. */
    public enum Sort { INDEX, TITLE, ARTIST, BPM, LENGTH }

    /** What the browser lists: playlist {@code playlist} (-1: all), matching {@code query} in title or artist, sorted. */
    public static List<Entry> view(Snapshot snapshot, int playlist, String query, Sort sort, boolean descending) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : snapshot.entries()) {
            if (playlist >= 0 && e.playlist() != playlist) continue;
            if (!q.isEmpty() && !lower(e.title().getString()).contains(q) && !lower(e.artist()).contains(q)) continue;
            out.add(e);
        }
        Comparator<Entry> order = switch (sort) {
            case INDEX -> null;
            case TITLE -> Comparator.comparing((Entry e) -> lower(e.title().getString()));
            case ARTIST -> Comparator.comparing((Entry e) -> lower(e.artist()));
            case BPM -> Comparator.comparingDouble(Entry::bpm);
            case LENGTH -> Comparator.comparingInt(Entry::lengthTicks);
        };
        if (order != null) out.sort(order);
        if (descending) Collections.reverse(out);
        return out;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew runGameTestServer --console=plain 2>&1 | grep -iE "fail|passed|exception|error:|BUILD"`
Expected: `All 97 required tests passed :)`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/DjLibrary.java src/main/java/com/createbrewery/ClubGameTests.java
git commit -m "feat(dj): search and sort the booth's library"
```

---

### Task 6: The browser screen

**Files:**
- Create: `src/main/java/com/createbrewery/block/club/DjLibraryScreen.java`
- Modify: `src/main/java/com/createbrewery/block/club/DjMixerScreen.java:41` (`DECK_COLORS` visibility), `:162` (BROWSE button)

**Interfaces:**
- Consumes: `DjLibrarySync.request/load/received` (Task 3), `DjLibrary.view`, `Sort`, `Snapshot`, `Entry`, `Playlist` (Tasks 1, 5), `DjMixerScreen.open(BlockPos)`, `DjMixerScreen.DECK_COLORS`.
- Produces: `public static void DjLibraryScreen.open(BlockPos pos)`.

UI is verified in game (Step 4); the logic it shows is tested in Tasks 1, 2, 5.

- [ ] **Step 1: Make the deck colours shared** — in `DjMixerScreen.java` line 41 change `private static final int[] DECK_COLORS = {` to `static final int[] DECK_COLORS = {`.

- [ ] **Step 2: Point BROWSE at the new screen** — in `DjMixerScreen.init()`, change

```java
        tabBrowseButton = addRenderableWidget(Button.builder(Component.literal("BROWSE"), b -> screenTab = 1)
```

to

```java
        tabBrowseButton = addRenderableWidget(Button.builder(Component.literal("BROWSE"), b -> DjLibraryScreen.open(pos))
```

- [ ] **Step 3: Create `DjLibraryScreen.java`**

```java
package com.createbrewery.block.club;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The booth's BROWSE screen, rekordbox style: playlists (the containers around the booth and the
 * shulker boxes in them) on the left, their records in a searchable, sortable table. The records
 * stay physical: loading one moves it from its container onto the deck.
 */
public class DjLibraryScreen extends Screen {
    private static final int W = 540, H = 276;
    private static final int SIDEBAR_W = 110, PLAYLIST_H = 12, ROW_H = 14, HEADER_Y = 24, FIRST_ROW_Y = 38;
    private static final int ROWS = (H - FIRST_ROW_Y - 6) / ROW_H;
    /** Column x from the table's left edge: #, title, artist, BPM, key, time; then the four load buttons. */
    private static final int[] COL_X = {4, 26, 180, 284, 320, 346};
    private static final String[] HEADERS = {"#", "TITLE", "ARTIST", "BPM", "KEY", "TIME"};
    private static final DjLibrary.Sort[] HEADER_SORT = {DjLibrary.Sort.INDEX, DjLibrary.Sort.TITLE, DjLibrary.Sort.ARTIST, DjLibrary.Sort.BPM, null, DjLibrary.Sort.LENGTH};
    private static final int COL_LOAD = 370, LOAD_W = 10, LOAD_STEP = 11;

    private final BlockPos pos;
    private int left, top, tableX;
    private EditBox search;
    /** -1: ALL TRACKS. */
    private int playlist = -1;
    private DjLibrary.Sort sort = DjLibrary.Sort.INDEX;
    private boolean descending;
    private int scroll, selected, ticks;
    /** What the table shows, as last drawn; clicks and keys act on it. */
    private List<DjLibrary.Entry> rows = List.of();

    private DjLibraryScreen(BlockPos pos) {
        super(Component.literal("BROWSE"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DjLibraryScreen(pos));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        tableX = left + 6 + SIDEBAR_W + 4;
        String query = search != null ? search.getValue() : "";
        search = addRenderableWidget(new EditBox(font, left + 120, top + 5, 200, 14, Component.literal("Search")));
        search.setHint(Component.literal("Search title / artist").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(q -> {
            scroll = 0;
            selected = 0;
        });
        addRenderableWidget(Button.builder(Component.literal("BACK"), b -> onClose()).bounds(left + W - 50, top + 5, 44, 14).build());
        DjLibrarySync.request(pos);
    }

    @Override
    public void tick() {
        // Gone or out of reach: the server would ignore every click, so close like a chest does.
        if (minecraft == null || minecraft.level == null || minecraft.player == null
            || !(minecraft.level.getBlockEntity(pos) instanceof DjBoothBlockEntity)
            || !minecraft.player.canInteractWithBlock(pos, 1.0)) {
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }
        if (++ticks % 20 == 0) DjLibrarySync.request(pos);
    }

    @Override
    public void onClose() {
        DjMixerScreen.open(pos);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + H, 0xFF111317);
        g.renderOutline(left, top, W, H, 0xFF353944);
        g.fill(left + 6, top + HEADER_Y, left + 6 + SIDEBAR_W, top + H - 6, 0xFF0A0E16);
        g.fill(tableX, top + HEADER_Y, left + W - 6, top + H - 6, 0xFF080C14);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, "BROWSE", left + 8, top + 8, 0xFFFFCC00);
        int cx = tableX + (left + W - 6 - tableX) / 2;

        DjLibrary.Snapshot lib = DjLibrarySync.received(pos);
        if (lib == null) {
            g.drawCenteredString(font, "LOADING...", cx, top + 80, 0xFF888888);
            return;
        }
        if (lib.playlists().isEmpty()) {
            g.drawCenteredString(font, "NO RECORD CRATE AT BOOTH", cx, top + 80, 0xFFFF4444);
            g.drawCenteredString(font, "Put a chest or barrel next to the booth", cx, top + 94, 0xFF888888);
            return;
        }
        if (playlist >= lib.playlists().size()) playlist = -1;
        rows = DjLibrary.view(lib, playlist, search.getValue(), sort, descending);
        selected = Mth.clamp(selected, 0, Math.max(0, rows.size() - 1));
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - ROWS));

        renderSidebar(g, lib, mouseX, mouseY);
        for (int h = 0; h < HEADERS.length; h++) {
            boolean active = HEADER_SORT[h] == sort && sort != null;
            String label = HEADERS[h] + (active && h > 0 ? (descending ? " v" : " ^") : "");
            g.drawString(font, label, tableX + COL_X[h], top + HEADER_Y + 2, active ? 0xFFFFCC00 : 0xFF88AAFF);
        }
        g.drawString(font, "LOAD", tableX + COL_LOAD + 8, top + HEADER_Y + 2, 0xFF88AAFF);

        if (rows.isEmpty()) {
            g.drawCenteredString(font, search.getValue().isBlank() ? "PLAYLIST EMPTY" : "NO MATCHES", cx, top + 80, 0xFF888888);
            return;
        }
        for (int r = 0; r < ROWS; r++) {
            int idx = scroll + r;
            if (idx >= rows.size()) break;
            DjLibrary.Entry e = rows.get(idx);
            int ry = top + FIRST_ROW_Y + r * ROW_H;
            g.fill(tableX + 1, ry, left + W - 7, ry + ROW_H - 1, idx == selected ? 0xFF2A3A5A : (r % 2 == 0 ? 0xFF0E1420 : 0xFF141C2C));
            int text = 0xFFE0E0E0;
            g.drawString(font, String.valueOf(idx + 1), tableX + COL_X[0], ry + 3, 0xFF888888);
            g.drawString(font, font.plainSubstrByWidth(e.title().getString(), COL_X[2] - COL_X[1] - 4), tableX + COL_X[1], ry + 3, text);
            g.drawString(font, font.plainSubstrByWidth(e.artist(), COL_X[3] - COL_X[2] - 4), tableX + COL_X[2], ry + 3, 0xFFAAAAAA);
            g.drawString(font, e.bpm() > 0 ? String.format("%.1f", e.bpm()) : "-", tableX + COL_X[3], ry + 3, text);
            g.drawString(font, "-", tableX + COL_X[4], ry + 3, 0xFF666666);
            g.drawString(font, e.lengthTicks() > 0 ? time(e.lengthTicks()) : "--:--", tableX + COL_X[5], ry + 3, text);
            for (int d = 0; d < DjBoothBlockEntity.DECKS; d++) {
                int bx = tableX + COL_LOAD + d * LOAD_STEP;
                boolean hover = mouseX >= bx && mouseX < bx + LOAD_W && mouseY >= ry + 1 && mouseY < ry + 12;
                g.fill(bx, ry + 1, bx + LOAD_W, ry + 12, hover ? 0xFF354560 : 0xFF1E283A);
                g.renderOutline(bx, ry + 1, LOAD_W, 11, DjMixerScreen.DECK_COLORS[d]);
                g.drawCenteredString(font, String.valueOf(d + 1), bx + 5, ry + 3, DjMixerScreen.DECK_COLORS[d]);
            }
        }
    }

    private void renderSidebar(GuiGraphics g, DjLibrary.Snapshot lib, int mouseX, int mouseY) {
        int x = left + 6;
        // ponytail: playlists past the sidebar's height are not drawn; add sidebar scrolling if a booth ever has that many
        for (int i = -1; i < lib.playlists().size(); i++) {
            int y = top + HEADER_Y + (i + 1) * PLAYLIST_H;
            if (y + PLAYLIST_H > top + H - 6) break;
            boolean nested = i >= 0 && lib.playlists().get(i).nested();
            String name = i < 0 ? "ALL TRACKS" : lib.playlists().get(i).name().getString();
            if (i == playlist) g.fill(x, y, x + SIDEBAR_W, y + PLAYLIST_H, 0xFF2A3A5A);
            int indent = nested ? 12 : 4;
            g.drawString(font, font.plainSubstrByWidth(name, SIDEBAR_W - indent - 2), x + indent, y + 2, i == playlist ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
    }

    private static String time(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        DjLibrary.Snapshot lib = DjLibrarySync.received(pos);
        if (lib == null) return false;
        setFocused(null);
        search.setFocused(false);

        if (mouseX >= left + 6 && mouseX < left + 6 + SIDEBAR_W && mouseY >= top + HEADER_Y) {
            int i = (int) ((mouseY - top - HEADER_Y) / PLAYLIST_H) - 1;
            if (i >= -1 && i < lib.playlists().size()) {
                playlist = i;
                scroll = 0;
                selected = 0;
                return true;
            }
        }
        if (mouseX >= tableX && mouseY >= top + HEADER_Y && mouseY < top + FIRST_ROW_Y - 2) {
            for (int h = HEADERS.length - 1; h >= 0; h--) {
                if (mouseX >= tableX + COL_X[h]) {
                    if (HEADER_SORT[h] == null) return true;
                    descending = HEADER_SORT[h] == sort && !descending;
                    sort = HEADER_SORT[h];
                    return true;
                }
            }
        }
        if (mouseX >= tableX && mouseY >= top + FIRST_ROW_Y) {
            int r = (int) ((mouseY - top - FIRST_ROW_Y) / ROW_H);
            int idx = scroll + r;
            if (r < ROWS && idx < rows.size()) {
                int ry = top + FIRST_ROW_Y + r * ROW_H;
                for (int d = 0; d < DjBoothBlockEntity.DECKS; d++) {
                    int bx = tableX + COL_LOAD + d * LOAD_STEP;
                    if (mouseX >= bx && mouseX < bx + LOAD_W && mouseY >= ry + 1 && mouseY < ry + 12) {
                        load(lib, rows.get(idx), d);
                        return true;
                    }
                }
                selected = idx;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 3, 0, Math.max(0, rows.size() - ROWS));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_TAB) {
                search.setFocused(false);
                setFocused(null);
                return true;
            }
            // Typing goes into the search, digits included: 1-4 load only outside it.
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            selected = Mth.clamp(selected + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), 0, Math.max(0, rows.size() - 1));
            if (selected < scroll) scroll = selected;
            if (selected >= scroll + ROWS) scroll = selected - ROWS + 1;
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_4 && selected < rows.size()) {
            DjLibrary.Snapshot lib = DjLibrarySync.received(pos);
            if (lib != null) load(lib, rows.get(selected), keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void load(DjLibrary.Snapshot lib, DjLibrary.Entry entry, int deck) {
        if (entry.playlist() < 0 || entry.playlist() >= lib.playlists().size()) return;
        DjLibrary.Playlist p = lib.playlists().get(entry.playlist());
        DjLibrarySync.load(pos, deck, p.source(), p.shulker(), entry.slot());
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.2f);
        }
    }
}
```

- [ ] **Step 4: Build, run tests, check in game**

Run: `./gradlew build --console=plain -q 2>&1 | grep -iE "error|fail" ; ./gradlew runGameTestServer --console=plain 2>&1 | grep -iE "fail|passed|exception|BUILD"`
Expected: no build errors; `All 97 required tests passed :)`.

Then in game (`./gradlew runClient`, or with the user's OK copy `build/libs/createbrewery-*.jar` over the profile's `mods/createbrewery.jar` while Minecraft is closed). Place a booth, a chest north of it with 3 records and a renamed shulker box holding 2 records, a barrel east with 1 record. Check each:

1. BROWSE opens the library; sidebar shows ALL TRACKS, the chest, the shulker (indented, its name), the barrel; ALL TRACKS lists 6 records.
2. Clicking TITLE sorts A–Z, again Z–A; BPM column `-` for unplayed records; TIME shows `m:ss` for vanilla records.
3. Typing `cat` filters; typing `1` into the search does **not** load anything (Review Focus 2); Enter leaves the search, then Up/Down moves the highlight and `1` loads it onto deck 1.
4. Load buttons 1–4 move the record onto that deck; the deck's old record appears in the vacated slot within a second.
5. Load a record from inside the shulker box; open the chest: the box holds the deck's previous record.
6. Play one record alone, standing at the booth, for ~40 s; its BPM appears in the list.
7. Walk ~8 blocks away with BROWSE open → the screen closes (Review Focus 1). Open it again and break the booth from another player / with a command → it closes.
8. Escape and BACK return to the mixer.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/DjLibraryScreen.java src/main/java/com/createbrewery/block/club/DjMixerScreen.java
git commit -m "feat(dj): rekordbox-style library browser for the booth"
```

---

## Self-review notes

- Spec coverage: library model (T1), sync (T3), loading incl. shulkers + stale + never lost (T2), automix follows playlist (T2), track memory (T4), browser UI with search/sort/keys/empty states (T5, T6), all six spec tests (T1, T2 ×4, T4, T5). Deviations listed at the top.
- Names used across tasks: `DjLibrary.sources/snapshot/take/putBack/discSlots/view/isShulker/contents`, `Source/Playlist/Entry/Snapshot/Sort`, `TrackInfo.TRACK_INFO/MIN_BPM/MAX_BPM/baseBpm`, `DjBoothBlockEntity.loadFromLibrary/acceptTrackInfo/restock`, `DjLibrarySync.request/load/received`, `DjControl.REPORT_BPM`, `DjMixerScreen.DECK_COLORS/open`.
- Test counts assume the 90 tests passing today (89 + restart); each task's expected count adds its new tests.
