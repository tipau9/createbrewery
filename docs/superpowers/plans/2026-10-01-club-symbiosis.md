# Club Symbiosis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One shared `ClubState` per DJ booth that every club light and effect reads, so a drop, build-up, breakdown and the DJ's mixer moves reach the whole rig in the same tick.

**Architecture:** A Minecraft-free `ClubState` turns raw `MusicPulse` numbers plus a mixer snapshot into show events (beat, drop edge, build-up, breakdown, bass cut). A client registry `ClubStates` resolves which booth a block listens to and advances each state once per tick. `DmxProgram` and the effect block entities read that state instead of latching kicks themselves. Effects get an optional console link (same linker item as fixtures) that gates them by blackout, master and group fader.

**Tech Stack:** Java 21, NeoForge 1.21.1, Create 6, JUnit 5 (pure logic), NeoForge GameTests (`ClubGameTests`), Registrate datagen for lang.

**Spec:** `docs/superpowers/specs/2026-10-01-club-symbiosis-design.md`

## Spec amendments found while planning (the plan follows these)

- `energy` output is dropped: no consumer in the spec's reaction table (YAGNI).
- The CO2 jet has no music mode today (redstone only, no saved state). It gets the console link like the others, and **only when linked** it bursts on the drop edge. Unlinked jets stay redstone-only. The cold spark keeps its existing `AUTO` property.
- The hazer (`HazerBlock`) is not touched. The spec's "fog" means `FogMachineBlock`.
- The mixer sharpens a real build-up and does not create one: a bass kill alone never strobes.
- Group of a linked effect is changed by right-clicking it again with the same linked item (no sneak, because sneaking skips `useItemOn`).
- The fog machine's server-side fluid drain does not know about blackout; only its client particles are gated.

## Global Constraints

- Beat latch thresholds: on above 0.38, off below 0.20 (`DmxProgram`, strobe, laser today). Drop latch: on above 0.6, off below 0.2 (`ColdSparkBlockEntity` today).
- Breakdown: music playing and `sinceKick > max(2.0, 4 * period)` seconds.
- Console link reach: `FixtureBlock.MAX_LINK` (64). Booth scan reach for the console: 16 blocks (existing `nearestBooth`). Club reach for an unlinked block: 32 blocks (`MusicPulse.CLUB_REACH`).
- Unlinked and boothless blocks must behave exactly as before (existing worlds).
- `ClubState` has no Minecraft imports. `ClubStates` is client-only and is referenced only from client-gated code.
- No new packets. Server code never references `ClubStates`.
- `DmxProgramTest` must pass **unchanged** after Task 2 (it is the proof that the AUTO look is preserved).
- Every task ends with green `./gradlew test`; tasks that touch block entities also run `./gradlew runGameTestServer`. Run Gradle from `C:\Users\Anwender\dev\createbrewery`.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Never copy the jar into `mods/` while `javaw` runs.

## Review Focus

- A kick that arrives twice inside one tick or after a long stall (chunk load): exactly one `beat`, no double fire (Task 1 test).
- No music playing, mixer at rest: no beat, no breakdown, `buildUp == tension` (Task 1 test).
- Console link to a console that is unloaded or broken: the effect runs as unlinked (gate 1), never stuck dark (Task 6 test).
- Effect linked to a console in another dimension or beyond `MAX_LINK`: refused with the existing too-far message (Task 6 GameTest).
- Linker item used on an effect with no console tag: ignored, the block's normal interaction still runs (Task 6).
- Two booths in reach: each block follows its own linked booth, or the nearest playing one, and never flips between them every tick (Task 3 test of `nearestPlaying`).
- Saved world from before this change (no `Console`/`Booth` tag): loads, all defaults, nothing dark (Task 6 GameTest).

---

## File structure

| File | Responsibility |
|---|---|
| `block/club/ClubState.java` (new) | Pure logic: beat/drop/breakdown/build-up/bass cut from raw inputs and a `Mixer` snapshot |
| `block/club/ClubStates.java` (new, client) | Resolves a block's booth, feeds `MusicPulse` + booth mixer into the right `ClubState` once per tick |
| `block/club/ConsoleLinkData.java` (new) | Console pos + group of an effect, NBT, `gate()` |
| `block/club/ConsoleLinked.java` (new) | One-method interface the five effect block entities implement |
| `block/club/ConsoleLink.java` (new) | Linker helper: which blocks are linkable, apply link, the shared `useItemOn` hook |
| `block/club/DmxProgram.java` | Reads `ClubState`; static `gate(Settings, group)`; bass-cut dimming |
| `block/club/DmxConsoleBlockEntity.java` | Adopts a booth without REC, syncs `Booth` to clients, `boothPos()`, uses `ClubStates` |
| `block/club/DjBoothBlockEntity.java` | Client booth registry, `nearestPlaying`, `mixer(long)` |
| `block/club/{FixtureBlockEntity,StrobeLightBlockEntity,LaserProjectorBlockEntity,LaserProjectorRenderer,ColdSparkBlockEntity,FogMachineBlockEntity,Co2JetBlockEntity}.java` | Read `ClubState`; five of them get the link |
| `block/club/{StrobeLightBlock,LaserProjectorBlock,FogMachineBlock,Co2JetBlock}.java`, `DmxConsoleBlock.java` | Linker hook in `useItemOn` / `setPlacedBy` |
| `src/test/.../block/club/{ClubStateTest,DmxProgramGateTest}.java` (new) | Unit tests |
| `ClubGameTests.java`, `ClubTestAccess.java` | GameTests and package-private access |
| `CreateBrewery.java`, `src/main/resources/assets/createbrewery/lang/de_de.json` | New lang keys |

---

### Task 1: `ClubState` (pure logic) with tests

**Files:**
- Create: `src/main/java/com/createbrewery/block/club/ClubState.java`
- Test: `src/test/java/com/createbrewery/block/club/ClubStateTest.java`

**Interfaces:**
- Produces:
  - `final class ClubState` (package-private) with
    `void update(float dt, float kick, float drop, float tension, boolean playing, double period, Mixer mixer, boolean noFlashing)`
  - `record Mixer(float lowEq, float filter, boolean looping)` with `static final Mixer NONE`
  - public-field outputs: `boolean beat, dropEdge, breakdown, playing, noFlashing`; `int beatIndex`; `float beatPhase, env, dropLevel, buildUp, bassCut, filterClosed`; `double period`
  - constants `BEAT_ON = 0.38f`, `BEAT_OFF = 0.20f`, `DROP_ON = 0.6f`, `DROP_OFF = 0.2f`

- [ ] **Step 1: Write the failing test**

```java
package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClubStateTest {

    private static void tick(ClubState c, float kick, float drop, float tension, boolean playing, ClubState.Mixer m) {
        c.update(0.05f, kick, drop, tension, playing, 0.5, m, false);
    }

    private static void tick(ClubState c, float kick, float drop, float tension, boolean playing) {
        tick(c, kick, drop, tension, playing, ClubState.Mixer.NONE);
    }

    @Test
    void oneBeatPerKickWithHysteresis() {
        ClubState c = new ClubState();
        tick(c, 0.9f, 0, 0, true);
        assertTrue(c.beat);
        assertEquals(1, c.beatIndex);
        tick(c, 0.9f, 0, 0, true);
        assertFalse(c.beat, "a held kick fired twice");
        tick(c, 0.3f, 0, 0, true); // between the thresholds: still latched
        tick(c, 0.9f, 0, 0, true);
        assertFalse(c.beat, "re-armed above the off threshold");
        tick(c, 0.1f, 0, 0, true);
        tick(c, 0.9f, 0, 0, true);
        assertTrue(c.beat);
        assertEquals(2, c.beatIndex);
    }

    @Test
    void dropFiresOnceAndDecays() {
        ClubState c = new ClubState();
        tick(c, 0, 1f, 0, true);
        assertTrue(c.dropEdge);
        assertEquals(1f, c.dropLevel, 1e-6);
        tick(c, 0, 1f, 0, true);
        assertFalse(c.dropEdge, "a held drop fired twice");
        for (int i = 0; i < 40; i++) tick(c, 0, 0f, 0, true);
        assertTrue(c.dropLevel < 0.2f, "the drop level did not die away");
        tick(c, 0, 1f, 0, true);
        assertTrue(c.dropEdge, "not re-armed after the drop ended");
    }

    @Test
    void breakdownAfterTheSameSilenceAsDmxProgram() {
        ClubState c = new ClubState();
        tick(c, 1f, 0, 0, true);
        for (int i = 0; i < 38; i++) tick(c, 0, 0, 0, true); // 1.9 s
        assertFalse(c.breakdown);
        for (int i = 0; i < 4; i++) tick(c, 0, 0, 0, true); // 2.1 s, max(2.0, 4 * 0.5)
        assertTrue(c.breakdown);
        tick(c, 1f, 0, 0, true);
        assertFalse(c.breakdown, "the next kick ends the breakdown");
    }

    @Test
    void silenceIsNotABreakdownAndNothingFires() {
        ClubState c = new ClubState();
        for (int i = 0; i < 100; i++) {
            tick(c, 0, 0, 0, false);
            assertFalse(c.breakdown);
            assertFalse(c.beat);
            assertFalse(c.dropEdge);
        }
        assertEquals(0f, c.buildUp, 1e-6);
    }

    @Test
    void mixerSharpensABuildUpButNeverCreatesOne() {
        ClubState c = new ClubState();
        ClubState.Mixer killed = new ClubState.Mixer(0f, 0.8f, false); // bass kill + high-pass swept up
        tick(c, 0, 0, 0f, true, killed);
        assertEquals(0f, c.buildUp, 1e-6, "a bass kill alone must not strobe");
        assertEquals(1f, c.bassCut, 1e-6);
        tick(c, 0, 0, 0.5f, true, killed);
        assertTrue(c.buildUp > 0.5f, "a real build-up should be sharpened");
        assertTrue(c.buildUp <= 1f);
        tick(c, 0, 0, 0.5f, true, ClubState.Mixer.NONE);
        assertEquals(0.5f, c.buildUp, 1e-6);
    }

    @Test
    void lowPassSweepIsFilterClosedNotBassCut() {
        ClubState c = new ClubState();
        tick(c, 0, 0, 0, true, new ClubState.Mixer(0.5f, -0.6f, false));
        assertEquals(0.6f, c.filterClosed, 1e-6);
        assertEquals(0f, c.bassCut, 1e-6);
    }

    @Test
    void flashingFlagIsPassedThrough() {
        ClubState c = new ClubState();
        c.update(0.05f, 0, 0, 0, true, 0.5, ClubState.Mixer.NONE, true);
        assertTrue(c.noFlashing);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.createbrewery.block.club.ClubStateTest"`
Expected: FAIL (compile error: `ClubState` not found).

- [ ] **Step 3: Write the implementation**

```java
package com.createbrewery.block.club;

/**
 * What the club's music and its DJ are doing right now, worked out once a tick for a whole rig:
 * one place that turns raw pulse numbers and the mixer into show events, so every light and effect
 * answers the same beat, drop and breakdown. Free of Minecraft classes (unit-tested); see
 * {@link ClubStates} for who advances which instance.
 */
final class ClubState {
    static final float BEAT_ON = 0.38f, BEAT_OFF = 0.20f, DROP_ON = 0.6f, DROP_OFF = 0.2f;

    /** The loudest deck's mixer: low EQ knob 0..1 (0.5 = flat), filter -1 (low-pass closed) .. 0 .. 1 (high-pass), loop running. */
    record Mixer(float lowEq, float filter, boolean looping) {
        static final Mixer NONE = new Mixer(0.5f, 0f, false);
    }

    /** True for exactly one tick on a kick; its running count; 0..1 through the beat; the punch that dies before the next kick. */
    boolean beat;
    int beatIndex;
    float beatPhase, env;
    /** True for one tick when a drop lands; then 1 decaying over a second or two. */
    boolean dropEdge;
    float dropLevel;
    /** Music but no kick for a few beats. */
    boolean breakdown, playing, noFlashing;
    /** 0..1 build-up: the tension, sharpened by the mixer while a build-up is already running. */
    float buildUp;
    /** 0..1: low EQ killed or high-pass swept up. */
    float bassCut;
    /** 0..1: low-pass swept closed. */
    float filterClosed;
    /** Seconds per beat, at least 0.2. */
    double period = 0.5;

    private boolean kickLatched, dropLatched;
    private double sinceKick = 99;

    void update(float dt, float kick, float drop, float tension, boolean playing, double period, Mixer mixer, boolean noFlashing) {
        this.playing = playing;
        this.noFlashing = noFlashing;
        this.period = Math.max(0.2, period);

        beat = kick > BEAT_ON && !kickLatched;
        if (beat) {
            kickLatched = true;
            beatIndex++;
            sinceKick = 0;
        } else {
            if (kick < BEAT_OFF) kickLatched = false;
            sinceKick += dt;
        }
        beatPhase = (float) Math.min(1.0, sinceKick / this.period);
        env = (float) Math.exp(-sinceKick * 7);

        dropEdge = drop > DROP_ON && !dropLatched;
        if (dropEdge) dropLatched = true;
        else if (drop < DROP_OFF) dropLatched = false;
        dropLevel = Math.max(drop, dropLevel * (float) Math.exp(-dt * 1.5));

        breakdown = playing && sinceKick > Math.max(2.0, 4 * this.period);

        bassCut = Math.max(clamp((0.5f - mixer.lowEq()) * 2f), Math.max(0f, mixer.filter()));
        filterClosed = Math.max(0f, -mixer.filter());
        // The mixer only sharpens a build-up that is already running: a bass kill on its own must not strobe.
        buildUp = tension > 0.05f ? clamp(tension + 0.3f * Math.max(bassCut, filterClosed)) : tension;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.createbrewery.block.club.ClubStateTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/ClubState.java src/test/java/com/createbrewery/block/club/ClubStateTest.java
git commit -m "feat(club): ClubState, one beat/drop/breakdown source for the rig"
```

---

### Task 2: `DmxProgram` reads `ClubState`

**Files:**
- Modify: `src/main/java/com/createbrewery/block/club/DmxProgram.java:42-127`
- Test: existing `src/test/java/com/createbrewery/block/club/DmxProgramTest.java` (unchanged)

**Interfaces:**
- Consumes: `ClubState` fields from Task 1.
- Produces:
  - `void update(Settings s, long tick, float dt, ClubState c)` (new, the real implementation)
  - the existing raw `update(Settings, long, float, float kick, float drop, float tension, boolean playing, double period, boolean noFlashing)` stays as an adapter over a private `ClubState own`, so `DmxProgramTest` is untouched.

- [ ] **Step 1: Run the existing test as the baseline**

Run: `./gradlew test --tests "com.createbrewery.block.club.DmxProgramTest"`
Expected: PASS (baseline).

- [ ] **Step 2: Replace the state and the update method**

In `DmxProgram.java`, remove the fields `kickLatched`, `sinceKick`, `drop`, `env` and `beatPhase` as sources of truth. Keep `beatPhase` as a public-ish field that is copied from the state (it is read by `pixel`). Replace lines 42-127 with:

```java
    final float[] level = new float[GROUPS];
    final int[] color = new int[GROUPS];
    /** Chase steps so far: moves on every {@code rate} beats, or every second with no music. */
    int step;
    /** 0..1 through the current beat, and the moving heads' clock (radians). */
    float beatPhase, movePhase;
    private boolean playing;
    private double sinceStep, time;
    private int beats;
    private final ClubState own = new ClubState();

    /**
     * Raw-number entry (a fixture linked to no booth, and the tests): the numbers go through a
     * private {@link ClubState}. The rig itself uses {@link #update(Settings, long, float, ClubState)}.
     */
    void update(Settings s, long tick, float dt, float kick, float drop, float tension, boolean playing, double period, boolean noFlashing) {
        own.update(dt, kick, drop, tension, playing, period, ClubState.Mixer.NONE, noFlashing);
        update(s, tick, dt, own);
    }

    /**
     * Once a tick (client): what the club is doing, as {@link ClubState} works it out. {@code tick}
     * is the game time, so every rig strobes in phase. With {@code c.noFlashing}: no strobing, and
     * every level glides instead of jumping.
     */
    void update(Settings s, long tick, float dt, ClubState c) {
        time += dt;
        sinceStep += dt;
        playing = c.playing;
        boolean noFlashing = c.noFlashing;
        float tension = c.buildUp;
        if (c.beat && ++beats % RATES[Math.floorMod(s.rate, RATES.length)] == 0) nextStep();
        if (!playing && sinceStep >= 1.0) nextStep();
        beatPhase = c.beatPhase;
        // One turn of a pattern every four beats; slow and steady with no music.
        movePhase += (float) (playing ? dt * Math.PI / (2 * c.period) : dt * 0.5);
        float env = c.env;
        boolean breakdown = c.breakdown;
        // Build-ups strobe faster and faster: a flash every 5 ticks (4 a second) up to every other tick (10).
        // Counted in ticks, like the strobe: a sine at those rates, sampled once a tick, aliases to nothing.
        boolean shutterOpen = noFlashing || tension < 0.15f || tick % Math.round(5 - 3 * tension) == 0;

        List<Scene> stored = new ArrayList<>();
        for (Scene sc : s.scenes) if (sc != null) stored.add(sc);

        for (int g = 0; g < GROUPS; g++) {
            float fader = s.faders[g], lv;
            int col = PALETTE[Math.floorMod(s.colors[g], PALETTE.length)];
            switch (s.program) {
                case CHASE -> {
                    if (stored.isEmpty()) {
                        // No scenes: a running light through the groups.
                        lv = Math.floorMod(step, GROUPS) == g ? fader : 0f;
                    } else {
                        Scene sc = stored.get(Math.floorMod(step, stored.size()));
                        lv = sc.levels()[g];
                        col = PALETTE[Math.floorMod(sc.colors()[g], PALETTE.length)];
                    }
                }
                case AUTO -> {
                    if (!playing) {
                        lv = fader * (0.15f + 0.1f * (float) Math.sin(time * 1.2 + g * 0.8));
                    } else if (breakdown) {
                        lv = fader * 0.08f;
                    } else {
                        // Odd and even groups trade the beat: a punch that dies before the next kick.
                        boolean on = (g + step) % 2 == 0;
                        lv = fader * (on ? 0.15f + 0.85f * env : 0.1f);
                        if (!shutterOpen) lv = 0f;
                        else if (tension >= 0.15f) lv = Math.max(lv, fader * tension);
                    }
                    if (c.dropLevel > 0.02f) {
                        lv = Math.max(lv, fader * c.dropLevel);
                        col = mix(col, 0xFFFFFF, c.dropLevel);
                    }
                }
                default -> lv = fader;
            }
            if ((s.flash >> g & 1) != 0) lv = 1f;
            float target = s.blackout ? 0f : clamp(lv * s.master);
            // Photosensitivity: at most a tenth of full range a tick, half a second from dark to full.
            level[g] = noFlashing ? level[g] + Math.max(-GLIDE, Math.min(GLIDE, target - level[g])) : target;
            color[g] = col;
        }
    }
```

Note for the implementer: the old code's `beat` used `sinceKick` before `period = max(0.2, ...)`; `ClubState` stores the clamped period, so `c.period` is already clamped.

- [ ] **Step 3: Run the unchanged test**

Run: `./gradlew test --tests "com.createbrewery.block.club.DmxProgramTest" --tests "com.createbrewery.block.club.ClubStateTest"`
Expected: PASS, with `DmxProgramTest.java` unmodified (`git diff --stat src/test` shows nothing for it).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/DmxProgram.java
git commit -m "refactor(club): DmxProgram reads ClubState instead of latching kicks itself"
```

---

### Task 3: `ClubStates` registry, booth registry and mixer snapshot

**Files:**
- Create: `src/main/java/com/createbrewery/block/club/ClubStates.java`
- Modify: `src/main/java/com/createbrewery/block/club/DjBoothBlockEntity.java` (`onLoad`, `setRemoved`, new methods near line 361)
- Test: `src/test/java/com/createbrewery/block/club/ClubStateTest.java` (add the pure part, see step 1)

**Interfaces:**
- Consumes: `ClubState` (Task 1); `DjBoothBlockEntity.getEq(int,int)`, `getFilter(int)`, `getLoopBeats(int)`, `isPlaying(int)`, `deckGain(int,long)`, constants `A`, `B`, `LOW`; `MusicPulse.kickNear/dropNear/tensionNear/playingNear/periodNear(BlockPos)`.
- Produces:
  - `ClubState ClubStates.at(Level level, BlockPos pos, @Nullable BlockPos linkedBooth)` (client, advances at most once per game tick per booth or per loose position)
  - `DjBoothBlockEntity.mixer(long gameTime): ClubState.Mixer`
  - `static @Nullable DjBoothBlockEntity DjBoothBlockEntity.nearestPlaying(Level level, BlockPos pos, double reach)`
  - `static ClubState.Mixer ClubState.loudestMixer(boolean[] playing, float[] gain, float[] lowEq, float[] filter, boolean[] looping)` (the pure deck choice, testable)

- [ ] **Step 1: Write the failing test for the deck choice**

Add to `ClubStateTest`:

```java
    @Test
    void mixerFollowsTheLoudestPlayingDeck() {
        boolean[] playing = {true, true};
        float[] gain = {0.2f, 0.9f}, low = {0.5f, 0f}, filter = {0f, 0.7f};
        boolean[] loop = {false, true};
        ClubState.Mixer m = ClubState.loudestMixer(playing, gain, low, filter, loop);
        assertEquals(0f, m.lowEq());
        assertEquals(0.7f, m.filter());
        assertTrue(m.looping());
        playing[1] = false; // the loud deck stopped: the other one is what is heard
        assertEquals(0.5f, ClubState.loudestMixer(playing, gain, low, filter, loop).lowEq());
        playing[0] = false;
        assertEquals(ClubState.Mixer.NONE, ClubState.loudestMixer(playing, gain, low, filter, loop));
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.createbrewery.block.club.ClubStateTest"`
Expected: FAIL (`loudestMixer` not defined).

- [ ] **Step 3: Add `loudestMixer` to `ClubState`**

```java
    /** The mixer of the deck the crowd hears: the loudest of the playing ones, {@link Mixer#NONE} when none plays. */
    static Mixer loudestMixer(boolean[] playing, float[] gain, float[] lowEq, float[] filter, boolean[] looping) {
        int best = -1;
        for (int d = 0; d < playing.length; d++) {
            if (playing[d] && (best < 0 || gain[d] > gain[best])) best = d;
        }
        return best < 0 ? Mixer.NONE : new Mixer(lowEq[best], filter[best], looping[best]);
    }
```

Run the test again: PASS.

- [ ] **Step 4: Booth registry and `mixer()` in `DjBoothBlockEntity`**

Add a client-only registry next to the other statics, register in `onLoad` (the existing `onLoad` at ~line 439 already has a server branch; add the client branch), unregister in `setRemoved`:

```java
    /** Client: every loaded booth, for the lights and effects that follow whichever booth plays near them. */
    private static final java.util.Set<BlockPos> BOOTHS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) BOOTHS.add(worldPosition.immutable());
        // ...the existing server-side reset stays as it is
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) BOOTHS.remove(worldPosition);
    }

    /** What the mixer is doing to the music the crowd hears (client). */
    public ClubState.Mixer mixer(long gameTime) {
        boolean[] playing = {isPlaying(A), isPlaying(B)};
        float[] gain = {deckGain(A, gameTime), deckGain(B, gameTime)};
        float[] low = {getEq(A, LOW), getEq(B, LOW)};
        float[] filter = {getFilter(A), getFilter(B)};
        boolean[] loop = {getLoopBeats(A) > 0, getLoopBeats(B) > 0};
        return ClubState.loudestMixer(playing, gain, low, filter, loop);
    }

    /** Client: the closest loaded booth within {@code reach} blocks of {@code pos} that is playing; ties go to the lower position so it never flips. */
    @org.jetbrains.annotations.Nullable
    public static DjBoothBlockEntity nearestPlaying(Level level, BlockPos pos, double reach) {
        DjBoothBlockEntity best = null;
        double bestDist = reach * reach;
        for (BlockPos p : BOOTHS) {
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof DjBoothBlockEntity dj) || !(dj.isPlaying(A) || dj.isPlaying(B))) continue;
            double d = p.distSqr(pos);
            if (d < bestDist || (d == bestDist && best != null && p.compareTo(best.getBlockPos()) < 0)) {
                best = dj;
                bestDist = d;
            }
        }
        return best;
    }
```

If `DjBoothBlockEntity` already overrides `setRemoved`, merge into it instead of adding a second one.

- [ ] **Step 2: Write `ClubStates`**

```java
package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Client: which {@link ClubState} a light or effect listens to, advanced once per game tick no
 * matter how many blocks ask. A block follows its console's booth; with none, the nearest booth
 * that plays within the club's reach; with no booth at all it gets a state of its own fed from the
 * music heard at its position, which is what it did before there was a shared state.
 */
final class ClubStates {
    private static final double REACH = 32.0;
    private static final int KEEP = 200;

    private static final class Entry {
        final ClubState state = new ClubState();
        long tick = Long.MIN_VALUE;
    }

    private static final Map<BlockPos, Entry> BOOTH = new HashMap<>(), LOOSE = new HashMap<>();
    private static WeakReference<Level> seen = new WeakReference<>(null);

    private ClubStates() {}

    static ClubState at(Level level, BlockPos pos, @Nullable BlockPos linkedBooth) {
        if (seen.get() != level) {
            BOOTH.clear();
            LOOSE.clear();
            seen = new WeakReference<>(level);
        }
        long now = level.getGameTime();
        DjBoothBlockEntity dj = null;
        if (linkedBooth != null && level.isLoaded(linkedBooth) && level.getBlockEntity(linkedBooth) instanceof DjBoothBlockEntity linked) dj = linked;
        if (dj == null) dj = DjBoothBlockEntity.nearestPlaying(level, pos, REACH);

        BlockPos at = dj != null ? dj.getBlockPos() : pos;
        Map<BlockPos, Entry> map = dj != null ? BOOTH : LOOSE;
        Entry e = map.computeIfAbsent(at.immutable(), k -> new Entry());
        if (e.tick != now) {
            e.tick = now;
            e.state.update(0.05f, MusicPulse.kickNear(at), MusicPulse.dropNear(at), MusicPulse.tensionNear(at),
                MusicPulse.playingNear(at), MusicPulse.periodNear(at),
                dj != null ? dj.mixer(now) : ClubState.Mixer.NONE,
                Minecraft.getInstance().options.hideLightningFlash().get());
            if (map.size() > 64) map.values().removeIf(old -> old.tick < now - KEEP);
        }
        return e.state;
    }
}
```

- [ ] **Step 3: Compile and run the tests**

Run: `./gradlew test`
Expected: PASS (nothing uses `ClubStates` yet).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/ClubStates.java src/main/java/com/createbrewery/block/club/ClubState.java src/main/java/com/createbrewery/block/club/DjBoothBlockEntity.java src/test/java/com/createbrewery/block/club/ClubStateTest.java
git commit -m "feat(club): ClubStates resolves a block's booth and advances its state once a tick"
```

---

### Task 4: Console, fixtures and effects read `ClubStates` (no link yet)

**Files:**
- Modify: `DmxConsoleBlockEntity.java:222-239`, `FixtureBlockEntity.java:100-108`, `StrobeLightBlockEntity.java:20,40-50`, `LaserProjectorBlockEntity.java:23,33-50`, `LaserProjectorRenderer.java:41-43`, `ColdSparkBlockEntity.java:36-43`, `FogMachineBlockEntity.java:76`
- All in `src/main/java/com/createbrewery/block/club/`

**Interfaces:**
- Consumes: `ClubStates.at(Level, BlockPos, @Nullable BlockPos)`, `ClubState` fields.
- Produces: `LaserProjectorBlockEntity.getDrop()` and `getKick()` (floats the renderer reads; the renderer no longer calls `MusicPulse`).

This task is behaviour-preserving for blocks without a booth in reach, and the client-only code cannot be unit-tested, so the check is compile + both test suites + the manual step at the end.

- [ ] **Step 1: `DmxConsoleBlockEntity.output()`**

Replace the body of `output()` (lines 232-239) and drop the `Minecraft` and `MusicPulse` imports if unused afterwards:

```java
    DmxProgram output() {
        if (level == null || level.getGameTime() == ranAt) return program;
        ranAt = level.getGameTime();
        program.update(settings, ranAt, 0.05f, ClubStates.at(level, worldPosition, booth));
        return program;
    }
```

- [ ] **Step 2: `FixtureBlockEntity.clientTick()` standalone branch**

Replace the `program.update(settings, ..., Minecraft.getInstance().options.hideLightningFlash().get());` call (lines 105-107) with:

```java
            program.update(settings, level.getGameTime(), 0.05f, ClubStates.at(level, worldPosition, null));
```

and remove the unused `Minecraft` and `MusicPulse` imports.

- [ ] **Step 3: Strobe BEAT mode**

In `StrobeLightBlockEntity`, delete the `kickLatched` field and replace the `case BEAT ->` block (lines 41-49) with:

```java
            case BEAT -> {
                if (level != null && ClubStates.at(level, worldPosition, null).beat) flashIntensity = 1f;
            }
```

- [ ] **Step 4: Laser BE and renderer**

In `LaserProjectorBlockEntity.tick()` delete `kickLatched` and replace lines 35-46 with:

```java
        ClubState club = ClubStates.at(level, worldPosition, null);
        // The old raw pulse was a smooth 0..1 that peaks on the kick; the state's punch envelope has the same shape.
        kick = club.env;
        drop = club.dropLevel;
        prevPhase = phase;
        phase += 0.04f + kick * 0.12f + drop * 0.08f;

        boolean hit = club.beat;
        beat = hit ? 1f : beat * 0.8f;

        // Chase: new spots on every beat, or every second while nothing plays.
        if (hit || (!club.playing && ticks % 20 == 0)) {
```

Add fields `private float kick, drop;` and getters:

```java
    /** 1 on a beat, dying away within a few ticks (the renderer's brightness). */
    public float getKick() { return kick; }
    /** The shared drop level, 1 decaying. */
    public float getDrop() { return drop; }
```

In `LaserProjectorRenderer` (lines 41-43), replace `MusicPulse.dropNear(pos)` and `MusicPulse.kickNear(pos)` with `be.getDrop()` and `be.getKick()` (the renderer already has the block entity; read it before editing and use its variable name).

- [ ] **Step 5: Cold spark and fog**

`ColdSparkBlockEntity.clientTick`: replace the latch block (lines 35-43) with:

```java
        if (state.getValue(ColdSparkBlock.AUTO) && ClubStates.at(level, pos, null).dropEdge) burst = BURST;
```

and delete the `dropLatched` field.

`FogMachineBlockEntity` line 76: replace the `MusicPulse.dropNear(worldPosition) > 0.3f` test with `ClubStates.at(level, worldPosition, null).dropLevel > 0.3f`.

- [ ] **Step 6: Verify**

Run: `./gradlew test runGameTestServer`
Expected: BUILD SUCCESSFUL, 72+ game tests pass.
Also run: `rg -n "MusicPulse" src/main/java/com/createbrewery/block/club` — expected: only `ClubStates.java`, `DjMixerScreen.java` and `AmpRackScreen.java` (GUI code) remain.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/createbrewery/block/club
git commit -m "refactor(club): console, fixtures, strobe, laser, cold spark and fog read ClubState"
```

---

### Task 5: Console adopts a booth without REC, and syncs it

**Files:**
- Modify: `DmxConsoleBlockEntity.java` (`serverTick` ~line 202, `writeShown` ~line 272, `loadAdditional` ~line 279, new `boothPos()`)
- Modify: `ClubTestAccess.java`
- Test: `ClubGameTests.java`

**Interfaces:**
- Consumes: existing `nearestBooth()`, `booth()`, `SCAN_COOLDOWN`, `scannedAt`.
- Produces: `@Nullable BlockPos DmxConsoleBlockEntity.boothPos()` (the saved booth position, loaded or not); `ClubTestAccess.consoleBooth(DmxConsoleBlockEntity)`, `ClubTestAccess.tickConsole(DmxConsoleBlockEntity)`.

- [ ] **Step 1: Write the failing GameTest**

Add to `ClubGameTests` (imports for `DmxConsoleBlockEntity` and `ClubTestAccess` exist; add `com.createbrewery.block.club.ClubTestAccess` if missing):

```java
    @GameTest(template = TEMPLATE)
    public static void consoleFindsItsBoothWithoutRec(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.DJ_BOOTH.get());
        BlockPos consolePos = POS.offset(3, 0, 0);
        helper.setBlock(consolePos, ModBlocks.DMX_CONSOLE.get());
        DmxConsoleBlockEntity dmx = helper.getBlockEntity(consolePos);
        helper.assertTrue(ClubTestAccess.consoleBooth(dmx) == null, "a fresh console follows a booth already");
        ClubTestAccess.tickConsole(dmx);
        helper.assertTrue(helper.absolutePos(POS).equals(ClubTestAccess.consoleBooth(dmx)), "the console did not adopt the booth beside it");

        // And it survives a save and load, the way a client gets it.
        net.minecraft.nbt.CompoundTag tag = dmx.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(tag.contains("Booth"), "the booth is not synced to clients");
        helper.succeed();
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew runGameTestServer`
Expected: compile error (`ClubTestAccess.consoleBooth` missing).

- [ ] **Step 3: Implement**

In `ClubTestAccess`:

```java
    public static BlockPos consoleBooth(DmxConsoleBlockEntity dmx) {
        return dmx.boothPos();
    }

    public static void tickConsole(DmxConsoleBlockEntity dmx) {
        dmx.serverTick();
    }
```

In `DmxConsoleBlockEntity`:

```java
    /** The booth this console follows, whether or not it is loaded; null if none was found yet. */
    @org.jetbrains.annotations.Nullable
    BlockPos boothPos() {
        return booth;
    }

    /** Without REC pressed: a console with no live booth looks for the nearest one now and then. */
    private void adoptBooth() {
        if (booth() != null || level.getGameTime() - scannedAt < SCAN_COOLDOWN * 5) return;
        scannedAt = level.getGameTime();
        BlockPos found = nearestBooth();
        if (found != null && !found.equals(booth)) {
            booth = found;
            sync();
        }
    }
```

In `serverTick()` call `adoptBooth();` as the first line after the null check. In `writeShown(tag)` add `if (booth != null) tag.put("Booth", net.minecraft.nbt.NbtUtils.writeBlockPos(booth));`. `loadAdditional` already reads `Booth`. Remove the duplicate `Booth` write in `saveAdditional` if `writeShown` now covers it (it calls `writeShown`).

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test runGameTestServer`
Expected: PASS including `consoleFindsItsBoothWithoutRec`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/block/club src/main/java/com/createbrewery/ClubGameTests.java
git commit -m "feat(club): console adopts the nearest booth without REC and syncs it"
```

---

### Task 6: Effects under the console (link, group, gate)

**Files:**
- Create: `ConsoleLinkData.java`, `ConsoleLinked.java`, `ConsoleLink.java` (all in `block/club/`)
- Modify: `DmxProgram.java` (static `gate`), `StrobeLightBlockEntity`, `LaserProjectorBlockEntity` + `LaserProjectorRenderer`, `FogMachineBlockEntity`, `Co2JetBlockEntity`, `ColdSparkBlockEntity`, blocks `StrobeLightBlock`, `LaserProjectorBlock`, `FogMachineBlock`, `Co2JetBlock` (covers `ColdSparkBlock`), `DmxConsoleBlock`, `FixtureBlock` (make `linkOf` reachable, already package-private), `ClubTestAccess`, `CreateBrewery.java`, `lang/de_de.json`
- Test: `src/test/java/com/createbrewery/block/club/DmxProgramGateTest.java` (new), `ClubGameTests.java`

**Interfaces:**
- Consumes: `FixtureBlock.linkOf(ItemStack): Optional<BlockPos>`, `FixtureBlock.MAX_LINK`, `ClubStates.at`, `DmxConsoleBlockEntity.settings`, `boothPos()`.
- Produces:
  - `static float DmxProgram.gate(Settings s, int group)`: 0 under blackout, else `master * fader`, or `master` while that group's flash is held
  - `ConsoleLinkData`: fields `@Nullable BlockPos console; int group;` methods `void save(CompoundTag)`, `void load(CompoundTag)`, `@Nullable DmxConsoleBlockEntity console(Level)`, `float gate(Level)` (1 when unlinked or the console is not loaded), `@Nullable BlockPos booth(Level)`
  - `interface ConsoleLinked { ConsoleLinkData consoleLink(); }`
  - `ConsoleLink.linkable(Block): boolean`, `ConsoleLink.applyLink(Level, BlockPos, ItemStack): boolean`, `ConsoleLink.use(Level, BlockPos, Player, ItemStack): ItemInteractionResult`, `ConsoleLink.holdsLink(ItemStack): boolean`, `ConsoleLink.onPlaced(Level, BlockPos, @Nullable LivingEntity, ItemStack)`

- [ ] **Step 1: Write the failing unit test for the gate**

```java
package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmxProgramGateTest {
    @Test
    void blackoutMasterAndFaderGateAnEffect() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.faders[2] = 0.5f;
        s.master = 0.8f;
        assertEquals(0.4f, DmxProgram.gate(s, 2), 1e-6);
        s.blackout = true;
        assertEquals(0f, DmxProgram.gate(s, 2));
    }

    @Test
    void aHeldFlashButtonLetsTheGroupThroughAtMaster() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.faders[1] = 0f;
        s.master = 0.6f;
        s.flash = 1 << 1;
        assertEquals(0.6f, DmxProgram.gate(s, 1), 1e-6);
        s.blackout = true;
        assertEquals(0f, DmxProgram.gate(s, 1), "blackout beats a flash");
    }

    @Test
    void outOfRangeGroupIsWrappedNotThrown() {
        assertEquals(DmxProgram.gate(new DmxProgram.Settings(), 0), DmxProgram.gate(new DmxProgram.Settings(), DmxProgram.GROUPS), 1e-6);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.createbrewery.block.club.DmxProgramGateTest"`
Expected: FAIL (`gate` not defined).

- [ ] **Step 3: Implement `DmxProgram.gate`**

```java
    /** How much of its brightness an effect in {@code group} may show: the console's blackout, master and the group's fader (or its held flash). */
    static float gate(Settings s, int group) {
        if (s.blackout) return 0f;
        int g = Math.floorMod(group, GROUPS);
        return clamp(s.master * ((s.flash >> g & 1) != 0 ? 1f : s.faders[g]));
    }
```

Run the test: PASS.

- [ ] **Step 4: `ConsoleLinkData`, `ConsoleLinked`, `ConsoleLink`**

```java
package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** The console an effect is linked to and its DMX group; what every linkable effect block entity holds. */
final class ConsoleLinkData {
    @Nullable
    BlockPos console;
    int group;

    void save(CompoundTag tag) {
        if (console == null) return;
        tag.put("Console", NbtUtils.writeBlockPos(console));
        tag.putInt("Group", group);
    }

    void load(CompoundTag tag) {
        console = NbtUtils.readBlockPos(tag, "Console").orElse(null);
        group = Math.floorMod(tag.getInt("Group"), DmxProgram.GROUPS);
    }

    @Nullable
    DmxConsoleBlockEntity console(Level level) {
        return console != null && level.isLoaded(console) && level.getBlockEntity(console) instanceof DmxConsoleBlockEntity dmx ? dmx : null;
    }

    /** 1 when unlinked or the console is not there (never stuck dark), else the console's gate for this group. */
    float gate(Level level) {
        DmxConsoleBlockEntity dmx = console(level);
        return dmx == null ? 1f : DmxProgram.gate(dmx.settings, group);
    }

    /** The booth of the linked console, null if unlinked. */
    @Nullable
    BlockPos booth(Level level) {
        DmxConsoleBlockEntity dmx = console(level);
        return dmx == null ? null : dmx.boothPos();
    }
}
```

```java
package com.createbrewery.block.club;

/** An effect that can be put under a DMX console. */
interface ConsoleLinked {
    ConsoleLinkData consoleLink();
}
```

```java
package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Linking effects to a console with the same item that links fixtures (tag {@code LinkedConsole}). */
final class ConsoleLink {
    private ConsoleLink() {}

    /** Blocks that take the console's link item: fixtures and the five effects (the cold spark is a CO2 jet). */
    static boolean linkable(Block block) {
        return block instanceof FixtureBlock || block instanceof StrobeLightBlock || block instanceof LaserProjectorBlock
            || block instanceof FogMachineBlock || block instanceof Co2JetBlock;
    }

    static boolean holdsLink(ItemStack stack) {
        return FixtureBlock.linkOf(stack).isPresent();
    }

    /**
     * Server: links the effect at {@code pos} to the console {@code stack} names. Used again on an
     * effect already linked to that console, it moves the effect on to the next group.
     */
    static boolean applyLink(Level level, BlockPos pos, ItemStack stack) {
        Optional<BlockPos> console = FixtureBlock.linkOf(stack);
        if (console.isEmpty() || !(level.getBlockEntity(pos) instanceof ConsoleLinked linked)) return false;
        if (!console.get().closerThan(pos, FixtureBlock.MAX_LINK) || !level.isLoaded(console.get())
            || !(level.getBlockEntity(console.get()) instanceof DmxConsoleBlockEntity)) return false;
        ConsoleLinkData data = linked.consoleLink();
        if (console.get().equals(data.console)) {
            data.group = Math.floorMod(data.group + 1, DmxProgram.GROUPS);
        } else {
            data.console = console.get().immutable();
            data.group = 0;
        }
        level.getBlockEntity(pos).setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        return true;
    }

    /** The shared {@code useItemOn} hook: call it first, and return its result when {@link #holdsLink} is true. */
    static ItemInteractionResult use(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (!level.isClientSide) {
            boolean ok = applyLink(level, pos, stack);
            int group = level.getBlockEntity(pos) instanceof ConsoleLinked l ? l.consoleLink().group + 1 : 1;
            player.displayClientMessage(ok ? Component.translatable("createbrewery.dmx.effect_linked", group)
                : Component.translatable("createbrewery.dmx.too_far", FixtureBlock.MAX_LINK), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** {@code setPlacedBy} hook: an effect placed from a linked stack comes up linked (group 1). */
    static void onPlaced(Level level, BlockPos pos, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !holdsLink(stack)) return;
        if (!applyLink(level, pos, stack) && placer instanceof Player player) {
            player.displayClientMessage(Component.translatable("createbrewery.dmx.too_far", FixtureBlock.MAX_LINK), true);
        }
    }
}
```

`InteractionResult` import is unused; remove it. If `FixtureBlock.linkOf` is `private`/not package-visible when compiling, widen it to package-private (it is declared `static Optional<BlockPos> linkOf` today, so no change should be needed).

- [ ] **Step 5: Console accepts any linkable item**

In `DmxConsoleBlock.useItemOn` (line 76), change `item.getBlock() instanceof FixtureBlock` to `ConsoleLink.linkable(item.getBlock())`. `FixtureBlock.link(stack, pos)` stays the way a stack is tagged.

- [ ] **Step 6: Give the five block entities the link, with persistence and sync**

For each of `StrobeLightBlockEntity`, `Co2JetBlockEntity`, `ColdSparkBlockEntity` (plain `BlockEntity`, no sync today) add `implements ConsoleLinked` and:

```java
    private final ConsoleLinkData link = new ConsoleLinkData();

    @Override
    public ConsoleLinkData consoleLink() {
        return link;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        link.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        link.load(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        link.save(tag);
        return tag;
    }
```

(with the imports `net.minecraft.core.HolderLookup`, `net.minecraft.nbt.CompoundTag`, `net.minecraft.network.protocol.Packet`, `net.minecraft.network.protocol.game.ClientGamePacketListener`, `net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket`).

`LaserProjectorBlockEntity` already has `saveAdditional`, `loadAdditional`, `getUpdatePacket` and `getUpdateTag` (lines ~100-135): add `implements ConsoleLinked`, the `link` field and `consoleLink()`, and call `link.save(tag)` / `link.load(tag)` inside the existing save/load/updateTag methods.

`FogMachineBlockEntity` is a Create `SmartBlockEntity` that syncs through `sendData()`. Add `implements ConsoleLinked` (it already implements `IHaveGoggleInformation`: append it), the `link` field and `consoleLink()`, and override:

```java
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        link.save(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        link.load(tag);
    }
```

Check the exact `write`/`read` signatures against Create 6's `SmartBlockEntity` in the decompiled sources before compiling; use `sendData()` after a link change. `ConsoleLink.applyLink` calls `sendBlockUpdated`, which is enough for plain block entities; for the fog machine also call `sendData()` there by adding, in `applyLink`, `if (be instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity s) s.sendData();`.

- [ ] **Step 7: Linker hook in the blocks**

In each of `StrobeLightBlock.useItemOn` (line 105), `LaserProjectorBlock.useItemOn` (97) and `FogMachineBlock.useItemOn` (82), add as the first line:

```java
        if (ConsoleLink.holdsLink(stack)) return ConsoleLink.use(level, pos, player, stack);
```

`Co2JetBlock` and `ColdSparkBlock` have no `useItemOn`: add to `Co2JetBlock`:

```java
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (ConsoleLink.holdsLink(stack)) return ConsoleLink.use(level, pos, player, stack);
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
```

(`ColdSparkBlock` inherits it. Add the missing imports.) Add to each of the four effect blocks (Strobe, Laser, Fog, Co2Jet) a `setPlacedBy` override:

```java
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ConsoleLink.onPlaced(level, pos, placer, stack);
    }
```

If a block already overrides `setPlacedBy`, add the single `ConsoleLink.onPlaced(...)` line to it instead.

- [ ] **Step 8: Effects obey the gate and the console's booth**

Pass the console's booth and apply the gate (all client side):

- `StrobeLightBlockEntity.tick`: replace `ClubStates.at(level, worldPosition, null)` (from Task 4) with `ClubStates.at(level, worldPosition, link.booth(level))`, compute `float gate = link.gate(level);` once, and offer `flashIntensity * gate` to `StrobeFlash.offer(...)` and `flashIntensity * gate * (0.4f + 0.3f * power)` to `StrobeRoomLight.update(...)`.
- `LaserProjectorBlockEntity.tick`: same booth argument; store `gate = link.gate(level)` and add `public float getGate() { return gate; }`. In `LaserProjectorRenderer`, where beam brightness/alpha is computed from `kickBoost`, return early when `be.getGate() < 0.05f` and multiply the beam alpha by `be.getGate()`.
- `FogMachineBlockEntity` client branch: `float gate = link.gate(level); if (gate < 0.05f) return/skip particle spawning; int puffs = Math.round((2 + (drop > 0.3f ? 2 : 0)) * gate);` with the booth argument as above.
- `ColdSparkBlockEntity.clientTick`: booth argument as above; `boolean on = state.getValue(Co2JetBlock.POWERED) || burst > 0; if (link.gate(level) < 0.05f) on = false;`.
- `Co2JetBlockEntity.tick` (client branch): add `private int burst;` and, only when linked,

```java
        boolean linked = link.console != null;
        if (linked && ClubStates.at(level, pos, link.booth(level)).dropEdge) burst = 20;
        if (burst > 0) burst--;
        boolean on = (state.getValue(Co2JetBlock.POWERED) || burst > 0) && link.gate(level) >= 0.05f;
```

  replacing `boolean on = state.getValue(Co2JetBlock.POWERED);` for the client part. Keep the server `chill` check on `POWERED` as it is (burst is client-only).
- `FixtureBlockEntity` and `DmxConsoleBlockEntity` already pass the console's booth (Task 4 / Task 5). Change `FixtureBlockEntity`'s standalone `ClubStates.at(level, worldPosition, null)` to stay `null`.

- [ ] **Step 9: Lang**

In `CreateBrewery.java` next to `REGISTRATE.addRawLang("createbrewery.dmx.relinked", ...)` (line ~222):

```java
        REGISTRATE.addRawLang("createbrewery.dmx.effect_linked", "Linked to the console, group %s");
```

In `src/main/resources/assets/createbrewery/lang/de_de.json` after `createbrewery.dmx.relinked`:

```json
  "createbrewery.dmx.effect_linked": "Mit dem Pult verbunden, Gruppe %s",
```

Run `./gradlew runData` to regenerate `src/generated/resources/assets/createbrewery/lang/en_us.json` and `en_ud.json`, and commit them.

- [ ] **Step 10: GameTests**

Add to `ClubGameTests` (imports `ClubTestAccess`, `DmxConsoleBlockEntity`, `net.minecraft.world.item.ItemStack`):

```java
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
    public static void effectGateFollowsBlackoutAndMaster(GameTestHelper helper) {
        helper.setBlock(CONSOLE, ModBlocks.DMX_CONSOLE.get());
        helper.setBlock(POS, ModBlocks.LASER_PROJECTOR.get());
        ItemStack link = new ItemStack(ModBlocks.LASER_PROJECTOR.get());
        ClubTestAccess.linkFixture(link, helper.absolutePos(CONSOLE));
        ClubTestAccess.applyEffectLink(helper.getLevel(), helper.absolutePos(POS), link);
        DmxConsoleBlockEntity dmx = helper.getBlockEntity(CONSOLE);

        helper.assertTrue(ClubTestAccess.effectGate(helper.getLevel(), helper.absolutePos(POS)) > 0.5f, "a linked effect is dark at default faders");
        dmx.setBlackout(true);
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
        net.minecraft.nbt.CompoundTag old = new net.minecraft.nbt.CompoundTag();
        ClubTestAccess.loadEffect(helper.getLevel(), helper.absolutePos(POS), old);
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
        ((DmxConsoleBlockEntity) helper.getBlockEntity(CONSOLE)).setBlackout(true);
        helper.setBlock(CONSOLE, net.minecraft.world.level.block.Blocks.AIR);
        helper.assertTrue(ClubTestAccess.effectGate(helper.getLevel(), helper.absolutePos(POS)) == 1f, "a destroyed console left the effect dark");
        helper.succeed();
    }
```

Add to `ClubTestAccess`:

```java
    public static boolean applyEffectLink(Level level, BlockPos pos, ItemStack stack) {
        return ConsoleLink.applyLink(level, pos, stack);
    }

    public static int effectGroup(Level level, BlockPos pos) {
        return ((ConsoleLinked) level.getBlockEntity(pos)).consoleLink().group;
    }

    public static float effectGate(Level level, BlockPos pos) {
        return ((ConsoleLinked) level.getBlockEntity(pos)).consoleLink().gate(level);
    }

    public static void loadEffect(Level level, BlockPos pos, net.minecraft.nbt.CompoundTag tag) {
        level.getBlockEntity(pos).loadAdditional(tag, level.registryAccess());
    }
```

(`loadAdditional` is `protected` on `BlockEntity`; `ClubTestAccess` is in the same package as none of the vanilla class, so call it through the public `BlockEntity.loadCustomOnly(tag, registries)` instead.)

- [ ] **Step 11: Verify and commit**

Run: `./gradlew test runGameTestServer`
Expected: PASS, including the five new GameTests.

```bash
git add -A src docs
git commit -m "feat(club): strobe, laser, fog, CO2 and cold spark can be linked to a DMX console"
```

---

### Task 7: Mixer coupling to the fixtures, then a full check

**Files:**
- Modify: `DmxProgram.java` (AUTO branch)
- Test: `DmxProgramTest.java` is not edited; add `DmxProgramMixerTest.java` (new)

**Interfaces:**
- Consumes: `ClubState.bassCut`, `ClubState.filterClosed`, `DmxProgram.update(Settings, long, float, ClubState)`.
- Produces: in AUTO, the group level is scaled by `1 - 0.6 * max(bassCut, filterClosed)` while playing and not in a drop.

- [ ] **Step 1: Write the failing test**

```java
package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmxProgramMixerTest {
    private static float punch(ClubState.Mixer mixer) {
        DmxProgram p = new DmxProgram();
        DmxProgram.Settings s = new DmxProgram.Settings();
        ClubState c = new ClubState();
        c.update(0.05f, 1f, 0, 0, true, 0.5, mixer, false);
        p.update(s, 0, 0.05f, c);
        return p.level[0] + p.level[1];
    }

    @Test
    void aBassKillDarkensTheRoom() {
        assertTrue(punch(new ClubState.Mixer(0f, 0f, false)) < punch(ClubState.Mixer.NONE) * 0.6f);
    }

    @Test
    void aClosedFilterDarkensTheRoom() {
        assertTrue(punch(new ClubState.Mixer(0.5f, -1f, false)) < punch(ClubState.Mixer.NONE) * 0.6f);
    }

    @Test
    void theDropBeatsTheMixerDimming() {
        DmxProgram p = new DmxProgram();
        DmxProgram.Settings s = new DmxProgram.Settings();
        ClubState c = new ClubState();
        c.update(0.05f, 1f, 1f, 0, true, 0.5, new ClubState.Mixer(0f, 0f, false), false);
        p.update(s, 0, 0.05f, c);
        assertEquals(s.faders[0], p.level[0], 1e-4, "the drop must hit at full level even with the bass killed");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.createbrewery.block.club.DmxProgramMixerTest"`
Expected: FAIL (`aBassKillDarkensTheRoom`: no dimming yet).

- [ ] **Step 3: Implement**

In the `AUTO` branch of `update(Settings, long, float, ClubState)`, just before the `if (c.dropLevel > 0.02f)` block, add:

```java
                    // The DJ takes the lows or closes the filter: the room darkens with it (a drop below still hits full).
                    lv *= 1f - 0.6f * Math.max(c.bassCut, c.filterClosed);
```

- [ ] **Step 4: Run the whole suite**

Run: `./gradlew test runGameTestServer jar`
Expected: BUILD SUCCESSFUL. `DmxProgramTest` still passes unmodified (`git diff HEAD~7 --stat -- src/test/java/com/createbrewery/block/club/DmxProgramTest.java` prints nothing).

- [ ] **Step 5: Final scans**

Run: `rg -n "kickLatched|0\\.38f" src/main/java/com/createbrewery/block/club` — expected: only `ClubState.java` and `DmxProgram.java` comment lines.
Run: `rg -n "Minecraft.getInstance" src/main/java/com/createbrewery/block/club/DmxConsoleBlockEntity.java src/main/java/com/createbrewery/block/club/FixtureBlockEntity.java` — expected: no matches.

- [ ] **Step 6: Manual check in game (cannot be automated)**

With `javaw` closed: copy `build/libs/createbrewery-0.1.2.jar` into the profile's `mods/`, start the game, build a booth + speaker + console + fixtures + strobe + laser + fog machine + CO2 jet + cold spark, link the effects with the console's link item, then:
1. Play a track and wait for a drop: lights, strobe, laser, fog blast and the CO2 burst answer on the same beat.
2. Pull the low EQ to zero and sweep the filter: the fixtures darken, and come back on release.
3. Press blackout on the console: the linked effects go dark, unlinked ones keep running.
4. Turn on "no flashing" in the options: nothing strobes.
5. Quit and reload the world: links and groups are kept.
Then check `logs/latest.log` for `createbrewery` errors.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/DmxProgram.java src/test/java/com/createbrewery/block/club/DmxProgramMixerTest.java
git commit -m "feat(club): bass kill and filter sweeps darken the fixtures"
```
