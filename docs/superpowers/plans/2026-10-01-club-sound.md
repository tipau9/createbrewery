# Club Sound Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Club speakers stop dropping out, the club gets a room reverb that follows the room around the listener, and the PA gets a level plan (stack loudness, soft clipping, bass that carries).

**Architecture:** Five small pure classes carry the logic and are unit tested without Minecraft or OpenAL: `SyncPolicy` (when an emitter restarts, servos or keeps), `EmitterBudget` (which speakers may run), `RoomAcoustics` (ray distances to reverb parameters), `PaLevel` (stack gain) and the soft knee in `DeckFx.Limiter`. `Emitter` and `MusicPulse` use them; a new `ReverbBus` owns the one EFX slot and `RoomProbe` feeds it from rays around the listener.

**Tech Stack:** Java 21, NeoForge 1.21.1, LWJGL 3.3.3 OpenAL (`AL10`, `AL11`, `EXTEfx`, `ALC10`), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-01-club-sound-design.md`

## Rulings made while planning (the plan follows these)

- **`read` and `kicks` are already `volatile`** in `MusicPulse.Track`, so the spec's race step is smaller than written. `cursor()` is the only place that removes chunks (the sound thread only appends at line 389 after a `clear()` on a brand-new track), so its `size()` / `get()` pairs cannot go stale. No change; the spec's step 6 is dropped.
- **No volume cap in `RoomAcoustics`.** The spec caps the room volume at 20000 m3, but with the surface left uncapped that makes decay fall for big halls, which breaks the spec's "monotonic in d". The decay clamp (0.2 .. 3.5 s) is the only cap.
- **Open sky is squared.** `lateGain` and `reflectionsGain` scale with `(1 - open)^2`, so a listener outdoors (about three quarters of the rays miss) hears next to no reverb, not a quarter of it.
- **The reverb slot is only deleted when no emitter exists.** OpenAL Soft refuses to delete an auxiliary slot that sources still send to. Turning `clubReverb` off while a song plays therefore sets the wet amount to 0 and keeps the slot.
- **`MusicPulse.update()` has two callers** (`DrunkClient.java:615` per frame, `RollClient.java:101`). Both stay; the ray budget resets at the top of every call, so a double call can cast up to 8 rays in a frame. Not worth a guard; recorded in case the resync lines survive Stage 1.
- **The resync cause cannot be measured headless.** Task 7 logs the reason per restart and the policy bounds the damage whatever the cause; the user reads `logs/latest.log` after the first session and the cause is reported then.
- **Ray budget by age, not round robin.** Emitters are updated in order of their last ray (oldest first, new ones first), so a budget of 4 rays per frame reaches all 12 within a few frames without a cursor.

## Review Focus

- A booth with more subwoofers than the always-kept limit (6 subs, `MAX_ALWAYS = 4`): the nearest 4 stay, the rest compete as normal speakers. Pinned in Task 2.
- A listener standing inside a block or against a wall (ray distances 0): reverb parameters stay finite and in range. Pinned in Task 5.
- No speaker within 6 blocks of the listener: stack gain is 1, not a division by zero. Pinned in Task 4.
- A source that stopped by itself (starved) or a huge drift right after a restart: at most one restart per 250 ms, never a storm. Pinned in Task 1.
- A wildly overdriven input (1e9, infinity) into the limiter stays finite and under the ceiling. Pinned in Task 3.

## Global Constraints

- Pure classes contain no `net.minecraft`, `org.lwjgl` or `com.mojang` imports.
- Client code only; nothing in this plan runs on a dedicated server.
- Constants live once, in the class named by the spec's table.
- Commit messages end with the attribution lines:
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF`.
- Never replace the mod jar in the profile's `mods/` while `javaw` runs. This plan builds and tests only; it does not copy or push.

## Setup (once, before Task 1)

- [ ] **Step 1: Confirm the tree builds.**

Run: `cd C:/Users/Anwender/dev/createbrewery && git status --short && ./gradlew compileJava test -q`
Expected: no output from `git status` (or only files that are not yours), `BUILD SUCCESSFUL`. If another author's uncommitted edits break compilation, do the work in a `git worktree` at HEAD (as sub-project 2 did) and cherry-pick back.

---

### Task 1: `SyncPolicy`

**Files:**
- Create: `src/main/java/com/createbrewery/drunk/SyncPolicy.java`
- Test: `src/test/java/com/createbrewery/drunk/SyncPolicyTest.java`

**Interfaces:**
- Produces: `SyncPolicy` with `Decision decide(boolean running, int queued, long here, long target, double rate, double now)`; `enum Action { KEEP, SERVO, RESTART }`; `record Decision(Action action, float pitch, String reason)`; constants `DRIFT_SOFT = 0.02`, `DRIFT_HARD = 0.25`, `COOLDOWN = 0.25`, `SERVO_RANGE = 0.02f`. `reason` is `"stopped"`, `"starved"`, `"drift"` for `RESTART`, else `""`. `pitch` is 1 unless `SERVO`.

- [ ] **Step 1: Write the failing test**

```java
package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** An emitter restarts only for real trouble, and not twice within a quarter second. */
class SyncPolicyTest {
    private static final double RATE = 44100;

    private static long frames(double seconds) {
        return (long) (seconds * RATE);
    }

    private static SyncPolicy.Decision decide(SyncPolicy p, double aheadSeconds, double now) {
        return p.decide(true, 5, frames(10) + frames(aheadSeconds), frames(10), RATE, now);
    }

    @Test
    void inStepKeepsAndLeavesThePitchAlone() {
        SyncPolicy.Decision d = decide(new SyncPolicy(), 0.005, 1);
        assertEquals(SyncPolicy.Action.KEEP, d.action());
        assertEquals(1f, d.pitch());
    }

    @Test
    void smallDriftIsCorrectedThroughPitchNotRestarted() {
        SyncPolicy.Decision early = decide(new SyncPolicy(), 0.1, 1);
        assertEquals(SyncPolicy.Action.SERVO, early.action());
        assertTrue(early.pitch() < 1f && early.pitch() >= 1f - SyncPolicy.SERVO_RANGE - 1e-6f, "ahead: slow down, within 2 %");
        SyncPolicy.Decision late = decide(new SyncPolicy(), -0.1, 1);
        assertEquals(SyncPolicy.Action.SERVO, late.action());
        assertTrue(late.pitch() > 1f && late.pitch() <= 1f + SyncPolicy.SERVO_RANGE + 1e-6f, "behind: speed up, within 2 %");
    }

    @Test
    void hugeDriftRestarts() {
        SyncPolicy.Decision d = decide(new SyncPolicy(), 0.5, 1);
        assertEquals(SyncPolicy.Action.RESTART, d.action());
        assertEquals("drift", d.reason());
    }

    @Test
    void aStoppedSourceRestarts() {
        SyncPolicy.Decision d = new SyncPolicy().decide(false, 5, frames(10), frames(10), RATE, 1);
        assertEquals(SyncPolicy.Action.RESTART, d.action());
        assertEquals("stopped", d.reason());
    }

    @Test
    void aSourceThatRanDryRestarts() {
        SyncPolicy.Decision d = new SyncPolicy().decide(true, 0, frames(10), frames(10), RATE, 1);
        assertEquals(SyncPolicy.Action.RESTART, d.action());
        assertEquals("starved", d.reason());
    }

    @Test
    void theFirstRestartNeverWaits() {
        assertEquals(SyncPolicy.Action.RESTART, new SyncPolicy().decide(false, 0, 0, 0, RATE, 0).action());
    }

    @Test
    void restartsAreRateLimited() {
        SyncPolicy p = new SyncPolicy();
        assertEquals(SyncPolicy.Action.RESTART, decide(p, 5, 1.0).action());
        // Still wildly off a tenth of a second later: no second restart, the pitch pulls it back instead.
        SyncPolicy.Decision again = decide(p, 5, 1.1);
        assertNotEquals(SyncPolicy.Action.RESTART, again.action());
        assertEquals(SyncPolicy.Action.RESTART, decide(p, 5, 1.3).action());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests com.createbrewery.drunk.SyncPolicyTest -q`
Expected: FAIL, compile error `cannot find symbol: class SyncPolicy`.

- [ ] **Step 3: Implement**

```java
package com.createbrewery.drunk;

/**
 * What an emitter's source needs to stay in step with its song, decided once a frame: nothing, a
 * slightly different playing speed, or a restart - and a restart at most every quarter second, so
 * a stalled frame cannot turn into a storm of clicks. Free of Minecraft and OpenAL classes.
 */
final class SyncPolicy {
    enum Action { KEEP, SERVO, RESTART }

    record Decision(Action action, float pitch, String reason) {}

    /** Under this (seconds) it is in step; up to {@link #DRIFT_HARD} the speed is nudged; over it, restart. */
    static final double DRIFT_SOFT = 0.02, DRIFT_HARD = 0.25;
    /** Seconds between restarts. */
    static final double COOLDOWN = 0.25;
    /** The most the playing speed is moved to pull a drift back. */
    static final float SERVO_RANGE = 0.02f;

    private double lastRestart = Double.NEGATIVE_INFINITY;

    /**
     * @param running the source is playing or paused
     * @param queued  buffers still queued on it
     * @param here    the song frame it is playing, {@code target} the one it should be at
     * @param now     seconds, any clock that only goes forward
     */
    Decision decide(boolean running, int queued, long here, long target, double rate, double now) {
        double ahead = (here - target) / rate;
        float pitch = Math.abs(ahead) < DRIFT_SOFT ? 1f
            : (float) Math.max(1 - SERVO_RANGE, Math.min(1 + SERVO_RANGE, 1 - ahead * 4));
        boolean stopped = !running, starved = running && queued == 0, drifted = Math.abs(ahead) > DRIFT_HARD;
        if ((stopped || starved || drifted) && now - lastRestart >= COOLDOWN) {
            lastRestart = now;
            return new Decision(Action.RESTART, 1f, stopped ? "stopped" : starved ? "starved" : "drift");
        }
        return new Decision(pitch == 1f ? Action.KEEP : Action.SERVO, pitch, "");
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew test --tests com.createbrewery.drunk.SyncPolicyTest -q`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/drunk/SyncPolicy.java src/test/java/com/createbrewery/drunk/SyncPolicyTest.java
git commit -m "feat(sound): SyncPolicy, restart only for real trouble and at most every 250 ms" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF"
```

---

### Task 2: `EmitterBudget`

**Files:**
- Create: `src/main/java/com/createbrewery/drunk/EmitterBudget.java`
- Test: `src/test/java/com/createbrewery/drunk/EmitterBudgetTest.java`

**Interfaces:**
- Produces: `EmitterBudget.Offer<K>(K key, double distSq, boolean always)`; `static <K> Set<K> choose(List<Offer<K>> offers, int max, Set<K> running)`; `MAX_ALWAYS = 4`, `STICKY = 0.64`.

- [ ] **Step 1: Write the failing test**

```java
package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Which speakers of a song may play: the nearest, subwoofers first, and no flapping at the border. */
class EmitterBudgetTest {
    private static EmitterBudget.Offer<String> top(String key, double dist) {
        return new EmitterBudget.Offer<>(key, dist * dist, false);
    }

    private static EmitterBudget.Offer<String> sub(String key, double dist) {
        return new EmitterBudget.Offer<>(key, dist * dist, true);
    }

    @Test
    void theNearestAreKept() {
        Set<String> kept = EmitterBudget.choose(List.of(top("e", 5), top("b", 2), top("a", 1), top("d", 4), top("c", 3)), 3, Set.of());
        assertEquals(Set.of("a", "b", "c"), kept);
    }

    @Test
    void aFarSubwooferStillPlays() {
        Set<String> kept = EmitterBudget.choose(List.of(top("a", 1), top("b", 2), sub("s", 30)), 2, Set.of());
        assertTrue(kept.contains("s"));
        assertEquals(2, kept.size());
        assertTrue(kept.contains("a"));
    }

    @Test
    void moreSubsThanTheAlwaysLimitKeepTheNearestFourAsSubs() {
        List<EmitterBudget.Offer<String>> offers = List.of(
            sub("s1", 10), sub("s2", 11), sub("s3", 12), sub("s4", 13), sub("s5", 14), sub("s6", 15),
            top("t1", 1), top("t2", 2));
        Set<String> kept = EmitterBudget.choose(offers, 6, Set.of());
        assertEquals(6, kept.size());
        assertTrue(kept.containsAll(Set.of("s1", "s2", "s3", "s4")), "the four nearest subs");
        assertTrue(kept.containsAll(Set.of("t1", "t2")), "the rest of the places go to the nearest");
    }

    @Test
    void aLimitBelowTheAlwaysCountStillHoldsTheLimit() {
        Set<String> kept = EmitterBudget.choose(List.of(sub("s1", 3), sub("s2", 2), sub("s3", 1)), 2, Set.of());
        assertEquals(Set.of("s2", "s3"), kept);
    }

    @Test
    void fewerOffersThanPlacesKeepsAll() {
        assertEquals(Set.of("a", "b"), EmitterBudget.choose(List.of(top("a", 1), top("b", 9)), 12, Set.of()));
    }

    @Test
    void oneAlreadyPlayingWinsAnAlmostTie() {
        List<EmitterBudget.Offer<String>> offers = List.of(top("playing", 10), top("new", 9));
        assertEquals(Set.of("playing"), EmitterBudget.choose(offers, 1, Set.of("playing")));
        // ... but not when the newcomer is clearly nearer.
        assertEquals(Set.of("new"), EmitterBudget.choose(List.of(top("playing", 10), top("new", 4)), 1, Set.of("playing")));
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests com.createbrewery.drunk.EmitterBudgetTest -q`
Expected: FAIL, compile error `cannot find symbol: class EmitterBudget`.

- [ ] **Step 3: Implement**

```java
package com.createbrewery.drunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which of a song's speakers may run this frame (each is an OpenAL source with its own DSP): the
 * {@code max} nearest, with the subwoofers - few and carrying the bass - always among them.
 * Free of Minecraft classes.
 */
final class EmitterBudget {
    private EmitterBudget() {}

    record Offer<K>(K key, double distSq, boolean always) {}

    /** At most this many offers are kept for being "always" (subwoofers). */
    static final int MAX_ALWAYS = 4;
    /** One already playing counts as this much nearer (0.8 squared), so a tie at the border does not flap. */
    static final double STICKY = 0.64;

    static <K> Set<K> choose(List<Offer<K>> offers, int max, Set<K> running) {
        List<Offer<K>> sorted = new ArrayList<>(offers);
        sorted.sort(Comparator.comparingDouble(o -> running.contains(o.key()) ? o.distSq() * STICKY : o.distSq()));
        Set<K> kept = new LinkedHashSet<>();
        int always = 0;
        for (Offer<K> o : sorted) {
            if (o.always() && always < MAX_ALWAYS && kept.size() < max) {
                kept.add(o.key());
                always++;
            }
        }
        for (Offer<K> o : sorted) {
            if (kept.size() >= max) break;
            kept.add(o.key());
        }
        return kept;
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew test --tests com.createbrewery.drunk.EmitterBudgetTest -q`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/drunk/EmitterBudget.java src/test/java/com/createbrewery/drunk/EmitterBudgetTest.java
git commit -m "feat(sound): EmitterBudget, nearest speakers play and subwoofers always do" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF"
```

---

### Task 3: Soft knee in `DeckFx.Limiter`

**Files:**
- Modify: `src/main/java/com/createbrewery/drunk/DeckFx.java` (class `Limiter`, around line 391)
- Test: `src/test/java/com/createbrewery/drunk/LimiterTest.java`

**Interfaces:**
- Produces: `DeckFx.Limiter.KNEE = 0.8f`; `static float soften(float x)`. `process` now runs every sample through `soften` after the gain.

- [ ] **Step 1: Write the failing test**

```java
package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The amp rounds off its peaks before the hard limit: no flat tops, nothing above the ceiling. */
class LimiterTest {
    private static final float C = DeckFx.Limiter.CEILING, K = DeckFx.Limiter.KNEE;

    @Test
    void belowTheKneeTheSignalIsUntouched() {
        for (float x : new float[] {0f, 0.1f, -0.5f, K, -K}) assertEquals(x, DeckFx.Limiter.soften(x), "moved " + x);
    }

    @Test
    void itIsContinuousAtTheKnee() {
        assertEquals(K, DeckFx.Limiter.soften(K + 1e-4f), 1e-3);
        assertEquals(-K, DeckFx.Limiter.soften(-K - 1e-4f), 1e-3);
    }

    @Test
    void itRisesMonotonicallyAndStaysUnderTheCeiling() {
        float last = -2f;
        for (float x = -3f; x <= 3f; x += 0.01f) {
            float y = DeckFx.Limiter.soften(x);
            assertTrue(y >= last, "went down at " + x);
            assertTrue(Math.abs(y) <= C, "over the ceiling at " + x);
            last = y;
        }
    }

    @Test
    void anAbsurdInputIsStillFiniteAndUnderTheCeiling() {
        for (float x : new float[] {1e9f, -1e9f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            float y = DeckFx.Limiter.soften(x);
            assertTrue(Float.isFinite(y) && Math.abs(y) <= C, "bad output for " + x + ": " + y);
        }
    }

    @Test
    void processRoundsOffAPeakTheHardLimitWouldFlatten() {
        DeckFx.Limiter lim = new DeckFx.Limiter(44100);
        float[] buf = new float[4800];
        for (int i = 0; i < buf.length; i++) buf[i] = (float) (1.2 * Math.sin(i * 0.05));
        lim.process(buf, buf.length);
        for (float v : buf) assertTrue(Math.abs(v) <= C, "over the ceiling: " + v);
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests com.createbrewery.drunk.LimiterTest -q`
Expected: FAIL, compile error `cannot find symbol: variable KNEE`.

- [ ] **Step 3: Implement**

In `DeckFx.Limiter`, add after `CEILING`:

```java
        /** Below this the signal passes untouched; above it a tanh curve approaches {@link #CEILING}. */
        public static final float KNEE = 0.8f;

        /** A soft clip: linear up to the knee, then rounded towards the ceiling, never over it. */
        public static float soften(float x) {
            float a = Math.abs(x);
            if (a <= KNEE) return x;
            // The min keeps float rounding from putting the saturated end a hair over the ceiling.
            float y = Math.min(CEILING, KNEE + (CEILING - KNEE) * (float) Math.tanh((a - KNEE) / (CEILING - KNEE)));
            return x < 0 ? -y : y;
        }
```

and in `process`, replace `buf[i] *= gain;` with `buf[i] = soften(buf[i] * gain);`.

- [ ] **Step 4: Run the new test and the whole drunk package**

Run: `./gradlew test --tests 'com.createbrewery.drunk.*' -q`
Expected: PASS, including the existing `DeckFxTest.limiterHoldsPeaksUnderTheCeilingAndLetsQuietThrough` (0.5 amplitude sits under the knee, so it is bit-for-bit unchanged).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/drunk/DeckFx.java src/test/java/com/createbrewery/drunk/LimiterTest.java
git commit -m "feat(sound): soft knee before the hard limit" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF"
```

---

### Task 4: `PaLevel`

**Files:**
- Create: `src/main/java/com/createbrewery/drunk/PaLevel.java`
- Test: `src/test/java/com/createbrewery/drunk/PaLevelTest.java`

**Interfaces:**
- Produces: `PaLevel.stackGain(int near)`, `PaLevel.STACK_RADIUS = 6.0`.

- [ ] **Step 1: Write the failing test**

```java
package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A stack of speakers is not eight times as loud as one. */
class PaLevelTest {
    @Test
    void oneSpeakerPlaysAtFullLevel() {
        assertEquals(1f, PaLevel.stackGain(1));
    }

    @Test
    void nobodyNearIsNotADivisionByZero() {
        assertEquals(1f, PaLevel.stackGain(0));
        assertEquals(1f, PaLevel.stackGain(-3));
    }

    @Test
    void levelFallsWithTheSquareRootOfTheCount() {
        assertEquals(0.5f, PaLevel.stackGain(4), 1e-6);
        assertEquals((float) (1 / Math.sqrt(8)), PaLevel.stackGain(8), 1e-6);
    }

    @Test
    void moreSpeakersNeverPlayLouderEach() {
        float last = 2f;
        for (int n = 0; n <= 32; n++) {
            float g = PaLevel.stackGain(n);
            assertTrue(g <= last, "rose at " + n);
            last = g;
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests com.createbrewery.drunk.PaLevelTest -q`
Expected: FAIL, compile error `cannot find symbol: class PaLevel`.

- [ ] **Step 3: Implement**

```java
package com.createbrewery.drunk;

/** The PA's level plan. Free of Minecraft classes. */
final class PaLevel {
    private PaLevel() {}

    /** Speakers of one band this close to the listener (blocks) add up; further ones are not counted. */
    static final double STACK_RADIUS = 6.0;

    /** The gain each of {@code near} speakers of one band plays at: coherent copies of one signal add up, so each backs off by the square root. */
    static float stackGain(int near) {
        return near <= 1 ? 1f : (float) (1 / Math.sqrt(near));
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew test --tests com.createbrewery.drunk.PaLevelTest -q`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/drunk/PaLevel.java src/test/java/com/createbrewery/drunk/PaLevelTest.java
git commit -m "feat(sound): PaLevel stack gain" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF"
```

---

### Task 5: `RoomAcoustics`

**Files:**
- Create: `src/main/java/com/createbrewery/drunk/RoomAcoustics.java`
- Test: `src/test/java/com/createbrewery/drunk/RoomAcousticsTest.java`

**Interfaces:**
- Produces: `RoomAcoustics.Params(float decayTime, float density, float diffusion, float lateGain, float reflectionsGain)` with `static Params lerp(Params a, Params b, double k)`; `static Params estimate(double[] dist, boolean[] hit)`; `MAX_RAY = 24.0`, `ABSORPTION = 0.25`.

- [ ] **Step 1: Write the failing test**

```java
package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** A closet is short and tight, a hall long, the open air dry. */
class RoomAcousticsTest {
    private static RoomAcoustics.Params room(double d) {
        double[] dist = new double[16];
        boolean[] hit = new boolean[16];
        Arrays.fill(dist, d);
        Arrays.fill(hit, true);
        return RoomAcoustics.estimate(dist, hit);
    }

    @Test
    void aHallRingsLongerThanACloset() {
        assertTrue(room(3).decayTime() < room(15).decayTime());
    }

    @Test
    void decayGrowsWithTheRoomAndStaysInRange() {
        float last = 0f;
        for (double d = 0; d <= 24; d += 0.5) {
            RoomAcoustics.Params p = room(d);
            assertTrue(p.decayTime() >= last - 1e-6f, "shrank at " + d);
            assertTrue(p.decayTime() >= 0.1f && p.decayTime() <= 3.5f, "out of range at " + d + ": " + p.decayTime());
            last = p.decayTime();
        }
    }

    @Test
    void standingInsideABlockStaysFiniteAndInRange() {
        RoomAcoustics.Params p = room(0);
        for (float v : new float[] {p.decayTime(), p.density(), p.diffusion(), p.lateGain(), p.reflectionsGain()}) {
            assertTrue(Float.isFinite(v) && v >= 0f, "bad value " + v);
        }
        assertTrue(p.decayTime() <= 0.3f, "a closet should be short");
    }

    @Test
    void openAirIsDry() {
        double[] dist = new double[16];
        boolean[] hit = new boolean[16];
        Arrays.fill(dist, RoomAcoustics.MAX_RAY);
        assertEquals(0f, RoomAcoustics.estimate(dist, hit).lateGain());
    }

    @Test
    void outdoorsWithOnlyTheGroundHitIsNearlyDry() {
        double[] dist = new double[16];
        boolean[] hit = new boolean[16];
        Arrays.fill(dist, RoomAcoustics.MAX_RAY);
        for (int i = 12; i < 16; i++) { // the four downward rays meet the ground
            dist[i] = 1.7;
            hit[i] = true;
        }
        assertTrue(RoomAcoustics.estimate(dist, hit).lateGain() < 0.15f);
        assertTrue(room(8).lateGain() > 1f, "a closed room keeps its full late reverb");
    }

    @Test
    void lerpMovesEveryFieldTowardsTheTarget() {
        RoomAcoustics.Params a = room(3), b = room(15);
        RoomAcoustics.Params half = RoomAcoustics.lerp(a, b, 0.5);
        assertEquals((a.decayTime() + b.decayTime()) / 2, half.decayTime(), 1e-5);
        assertEquals(b.decayTime(), RoomAcoustics.lerp(a, b, 1).decayTime(), 1e-6);
        assertEquals(a.decayTime(), RoomAcoustics.lerp(a, b, 0).decayTime(), 1e-6);
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests com.createbrewery.drunk.RoomAcousticsTest -q`
Expected: FAIL, compile error `cannot find symbol: class RoomAcoustics`.

- [ ] **Step 3: Implement**

```java
package com.createbrewery.drunk;

/**
 * What the room around the listener does to sound, from how far sixteen rays travel before they
 * hit something: the mean free distance gives a volume and a surface (Sabine: RT60 = 0.161 V / A),
 * rays that find nothing mean open air, which has no reverb. The numbers are OpenAL EFX reverb
 * settings. Free of Minecraft and OpenAL classes.
 */
final class RoomAcoustics {
    private RoomAcoustics() {}

    /** Rays are cast this far (blocks); one that finds nothing counts as this long and as open air. */
    static final double MAX_RAY = 24.0;
    /** Average absorption of walls (stone and wood, little carpet). */
    static final double ABSORPTION = 0.25;

    /** EFX units: decay in seconds, the others 0..1 (gains 0..10 and 0..3.16). */
    record Params(float decayTime, float density, float diffusion, float lateGain, float reflectionsGain) {}

    static Params estimate(double[] dist, boolean[] hit) {
        int n = dist.length, misses = 0;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            if (!hit[i]) misses++;
            sum += hit[i] ? Math.min(dist[i], MAX_RAY) : MAX_RAY;
        }
        double d = Math.max(1.0, sum / n);
        double open = (double) misses / n;
        double volume = 4.0 / 3 * Math.PI * d * d * d, surface = 4 * Math.PI * d * d;
        double rt60 = 0.161 * volume / (surface * ABSORPTION);
        double decay = clamp(rt60, 0.2, 3.5) * (1 - 0.7 * open);
        double dry = (1 - open) * (1 - open);
        return new Params((float) Math.max(0.1, decay), 1f, (float) clamp(1 - d / 60, 0.6, 1), (float) (1.26 * dry), (float) (0.3 * dry));
    }

    static Params lerp(Params a, Params b, double k) {
        return new Params(mix(a.decayTime, b.decayTime, k), mix(a.density, b.density, k), mix(a.diffusion, b.diffusion, k),
            mix(a.lateGain, b.lateGain, k), mix(a.reflectionsGain, b.reflectionsGain, k));
    }

    private static float mix(float a, float b, double k) {
        return (float) (a + (b - a) * k);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
```

Note: record components are accessed as `a.decayTime` inside the enclosing class (private field access is allowed within the same top-level class); if the compiler objects, use the accessor methods `a.decayTime()`.

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew test --tests com.createbrewery.drunk.RoomAcousticsTest -q`
Expected: PASS, 6 tests. If `outdoorsWithOnlyTheGroundHitIsNearlyDry` fails, print `lateGain`: with 12 of 16 misses, `dry = 0.0625`, `lateGain = 0.0788`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/drunk/RoomAcoustics.java src/test/java/com/createbrewery/drunk/RoomAcousticsTest.java
git commit -m "feat(sound): RoomAcoustics, reverb settings from ray distances" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF"
```

---

### Task 6: Config values

**Files:**
- Modify: `src/main/java/com/createbrewery/Config.java` (declarations after line 13, builder after the `enableVeilLights` definition, before `CLIENT_SPEC = client.build();`)

**Interfaces:**
- Produces: `Config.MAX_SPEAKERS` (`IntValue`, 4..32, default 12), `Config.CLUB_REVERB` (`BooleanValue`, default true), `Config.REVERB_AMOUNT` (`DoubleValue`, 0..1, default 0.7). Read them only behind `Config.CLIENT_SPEC.isLoaded()`.

- [ ] **Step 1: Add the declarations** after `public static final ModConfigSpec.BooleanValue ENABLE_VEIL_LIGHTS;`:

```java
    public static final ModConfigSpec.IntValue MAX_SPEAKERS;
    public static final ModConfigSpec.BooleanValue CLUB_REVERB;
    public static final ModConfigSpec.DoubleValue REVERB_AMOUNT;
```

- [ ] **Step 2: Add the values** after `.define("enableVeilLights", false);`:

```java
        MAX_SPEAKERS = client
            .comment("Most speakers one song plays out of at once: the nearest, subwoofers first. Fewer is lighter on the sound thread.")
            .defineInRange("maxSpeakers", 12, 4, 32);
        CLUB_REVERB = client
            .comment("Room reverb on the club's speakers, from the size of the room around you. Needs OpenAL EFX (on by default).")
            .define("clubReverb", true);
        REVERB_AMOUNT = client
            .comment("How much of that reverb you hear, 0 to 1.")
            .defineInRange("reverbAmount", 0.7, 0.0, 1.0);
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/createbrewery/Config.java
git commit -m "feat(sound): client config for speaker budget and club reverb" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF"
```

---

### Task 7: `Emitter` stability, reach and stack easing

**Files:**
- Modify: `src/main/java/com/createbrewery/drunk/Emitter.java`

**Interfaces:**
- Consumes: `SyncPolicy` (Task 1).
- Produces: `boolean Emitter.update(Track t, int original, long cursor, float gain, float pitch, Level world, Entity listener, double now, float dt, boolean mayRay)` (true when it cast rays this call); fields `double lastRay` (package-private), `float stack` (package-private, 1 default); constant `SUB_REACH = 1.5f`.

This task is wiring of OpenAL code; the verification is a clean compile and the whole suite, then the manual checks in Task 10. The behaviour that can be tested is in Tasks 1-5.

- [ ] **Step 1: Fields.** Replace

```java
    private double lastRay = -1;
    private int resyncs;
```

with

```java
    double lastRay = -1;
    private final SyncPolicy sync = new SyncPolicy();
    /** Restarts so far, and how many of them were for each reason (see {@link SyncPolicy}); logged every 20th. */
    private int resyncs, stoppedResyncs, starvedResyncs, driftResyncs;
    /** Processed buffers kept to be filled again: no alGenBuffers / alDeleteBuffers every 23 ms. */
    private final ArrayDeque<Integer> free = new ArrayDeque<>();
    private static final int POOL = 48;
    /** Bass carries further than the highs: a subwoofer is heard from this much further away. */
    static final float SUB_REACH = 1.5f;
    /** Set each frame to the PA's stack gain (see {@link PaLevel}) and eased, so a speaker crossing the radius does not step the level. */
    float stack = 1f;
    private float stackNow = 1f;
    private float baseRef;
    private int reachBand = -1;
```

- [ ] **Step 2: Replace the whole `update` method** (from its javadoc-less signature `void update(` to the closing brace before `private void create`) with:

```java
    boolean update(MusicPulse.Track t, int original, long cursor, float gain, float pitch, Level world, Entity listener, double now, float dt, boolean mayRay) {
        if (source <= 0 || !AL10.alIsSource(source)) create(original);
        if (source <= 0) return false;
        reclaim();

        int songState = AL10.alGetSourcei(original, AL10.AL_SOURCE_STATE);
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (songState != AL10.AL_PLAYING || (t.deck ? t.deckFrame < 0 : cursor < 0)) {
            // The game paused (menu) or the song has not started: hold still with it.
            if (state == AL10.AL_PLAYING) AL10.alSourcePause(source);
            return false;
        }

        // With the speed of sound on, a speaker is heard as far behind the song as its sound takes to reach you.
        long target = t.deck ? Math.max(0, t.deckFrame) : cursor;
        if (delay >= 0 && !phones && listener != null) {
            target = target - (long) (DeckFx.propagation(delay, listener.getEyePosition().distanceTo(pos)) * rate * pitch);
        }
        long here = fed - queuedFrames() + AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
        boolean running = state == AL10.AL_PLAYING || state == AL10.AL_PAUSED;
        SyncPolicy.Decision decision = sync.decide(running, queued.size(), here, target, rate, now);
        if (decision.action() == SyncPolicy.Action.RESTART) {
            // Only the first start is expected; more mean lag spikes or drift, worth seeing in the log.
            if (fed >= 0) {
                switch (decision.reason()) {
                    case "stopped" -> stoppedResyncs++;
                    case "starved" -> starvedResyncs++;
                    default -> driftResyncs++;
                }
                if (++resyncs % 20 == 1) {
                    LOGGER.info("Speaker at {} resynced ({} times: {} stopped, {} starved, {} drift; now {})",
                        home, resyncs, stoppedResyncs, starvedResyncs, driftResyncs, decision.reason());
                }
            }
            restart(target);
        }
        // A small drift is pulled back through the playing speed (within 2 %), not by a restart.
        float servo = decision.action() == SyncPolicy.Action.SERVO ? decision.pitch() : 1f;

        boolean cast = false;
        if (mayRay && !phones && listener != null && now - lastRay >= RAY_EVERY) {
            lastRay = now;
            hear(world, listener);
            cast = true;
        }
        // Eased, so walking through a door opens the sound up over a fifth of a second, not in one click.
        float ease = Math.min(1f, dt * 10f);
        muffle += (targetMuffle - muffle) * ease;
        walls += (targetWalls - walls) * ease;
        filter.set(muffle, Math.max(1f, walls));

        if (t.deck && fx == null) fx = new DeckFx(rate);
        if (fx != null) {
            fx.set(t.eq[2], t.eq[1], t.eq[0], t.filter, t.fx, t.fxAmount, t.song.period() * pitch, t.beatFxBeats);
            fx.setColorFx(t.colorType, t.filter, t.colorParam);
            fx.setMasterTempo(t.masterTempo, pitch);
        }
        // A rack placed or removed, the last sub switched off, the corner moved: taken up at once.
        if (band == FULL) split = null;
        else {
            if (split == null) split = new DeckFx.Crossover(rate);
            split.set(band == LOW, crossover);
        }
        feed(t, target + (long) (LEAD * rate * pitch));
        stackNow += (stack - stackNow) * Math.min(1f, dt * 5f);
        if (!phones && reachBand != band) {
            reachBand = band;
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, baseRef * (band == LOW ? SUB_REACH : 1f));
        }
        AL10.alSourcef(source, AL10.AL_GAIN, gain * level * stackNow);
        AL10.alSourcef(source, AL10.AL_PITCH, pitch * servo);
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && !queued.isEmpty()) AL10.alSourcePlay(source);
        return cast;
    }
```

- [ ] **Step 3: Remember the reference distance.** In `create`, in the non-phones part, right after the `for (int param : ...)` loop add:

```java
            baseRef = AL10.alGetSourcef(original, AL10.AL_REFERENCE_DISTANCE);
            reachBand = -1;
```

- [ ] **Step 4: Buffer pool.** Replace `reclaim`, `restart` and the buffer lines in `feed`:

```java
    /** Drops the buffers the source has played. */
    private void reclaim() {
        for (int n = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED); n > 0; n--) {
            recycle(AL10.alSourceUnqueueBuffers(source));
            queued.pollFirst();
        }
    }

    /** Keeps an unqueued buffer to fill again; past the pool's size it is freed. */
    private void recycle(int buffer) {
        if (free.size() < POOL) free.addLast(buffer);
        else AL10.alDeleteBuffers(buffer);
    }
```

in `restart`, replace `for (int n = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED); n > 0; n--) AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(source));` with `for (int n = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED); n > 0; n--) recycle(AL10.alSourceUnqueueBuffers(source));`

in `feed`, replace `int buffer = AL10.alGenBuffers();` with `int buffer = free.isEmpty() ? AL10.alGenBuffers() : free.pollFirst();`

in `delete`, after the `alDeleteBuffers(alSourceUnqueueBuffers(source))` loop and before `alDeleteSources`, nothing changes; after the `if (source > 0) { ... }` block add:

```java
        try {
            for (int buffer : free) AL10.alDeleteBuffers(buffer);
        } catch (Throwable ignored) {}
        free.clear();
```

- [ ] **Step 5: Fix the existing callers.** `MusicPulse.play` calls `e.update(...)` twice (emitters and headphones) with the old signature; give both a trailing `false` for now so the tree compiles (Task 8 replaces the emitter call).

Run: `grep -n "\.update(t, t.source" src/main/java/com/createbrewery/drunk/MusicPulse.java` and append `, false` before the closing `)` of each.

- [ ] **Step 6: Compile and run the whole suite**

Run: `./gradlew compileJava test -q`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/createbrewery/drunk/Emitter.java src/main/java/com/createbrewery/drunk/MusicPulse.java
git commit -m "feat(sound): emitters sync through SyncPolicy, pool buffers, ease stack gain, bass reach" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF"
```

---

### Task 8: `MusicPulse` budget, stack gain and ray order

**Files:**
- Modify: `src/main/java/com/createbrewery/drunk/MusicPulse.java` (`Want` record near line 65, `play` near lines 527-587, `update` start near line 469)

**Interfaces:**
- Consumes: `EmitterBudget.choose` (Task 2), `PaLevel.stackGain` and `PaLevel.STACK_RADIUS` (Task 4), `Config.MAX_SPEAKERS` (Task 6), `Emitter.update(..., mayRay)` and `Emitter.lastRay`, `Emitter.stack` (Task 7).

- [ ] **Step 1: `Want` gets a flag for PA speakers.** Replace

```java
    private record Want(float level, int band, float drive, double delay) {}
```

with

```java
    private record Want(float level, int band, float drive, double delay, boolean pa) {}

    /** Rays cast per frame across a song's speakers (see {@link Emitter#update}). */
    private static final int RAYS_PER_FRAME = 4;
    private static int raysLeft;

    private static int maxSpeakers() {
        return Config.CLIENT_SPEC.isLoaded() ? Config.MAX_SPEAKERS.get() : 12;
    }
```

- [ ] **Step 2: Mark the PA speakers.** In `play`, change the three `Want` constructions:

```java
        for (Vec3 top : tops) wanted.put(top, new Want(1f, split ? Emitter.HIGH : Emitter.FULL, topDrive, align(flight, top, boothAt, ref), true));
        if (split) {
            float subDrive = DeckFx.eqGain(rack.getSubGain());
            for (Vec3 sub : subs) wanted.put(sub, new Want(1f, Emitter.LOW, subDrive, align(flight, sub, boothAt, ref), true));
        }
        wanted.putIfAbsent(at, new Want(wanted.isEmpty() ? 1f : MONITOR, Emitter.FULL, 1f, flight ? 0 : -1, false));
```

- [ ] **Step 3: Budget and stack counts.** After the `float crossover = ...` line and before the loop that removes emitters no longer wanted, insert:

```java
        // Only the nearest speakers play (subwoofers first): each is a source with its own DSP.
        Vec3 ear = player == null ? at : player.getEyePosition();
        List<EmitterBudget.Offer<Vec3>> offers = new ArrayList<>();
        for (Map.Entry<Vec3, Want> w : wanted.entrySet()) {
            offers.add(new EmitterBudget.Offer<>(w.getKey(), w.getKey().distanceToSqr(ear), w.getValue().band() == Emitter.LOW));
        }
        wanted.keySet().retainAll(EmitterBudget.choose(offers, maxSpeakers(), t.emitters.keySet()));
        // A stack adds up: each speaker of a band near you backs off by the square root of how many there are.
        int[] near = new int[3];
        double radiusSq = PaLevel.STACK_RADIUS * PaLevel.STACK_RADIUS;
        for (Map.Entry<Vec3, Want> w : wanted.entrySet()) {
            if (w.getValue().pa() && w.getKey().distanceToSqr(ear) <= radiusSq) near[w.getValue().band()]++;
        }
```

- [ ] **Step 4: Update the emitters oldest-ray first.** Replace the loop

```java
        for (Map.Entry<Vec3, Want> w : wanted.entrySet()) {
            Emitter e = t.emitters.computeIfAbsent(w.getKey(), pos -> new Emitter(pos, w.getValue().level(), t.rate));
            e.level = w.getValue().level();
            e.band = w.getValue().band();
            e.drive = w.getValue().drive();
            e.delay = w.getValue().delay();
            e.crossover = crossover;
            e.update(t, t.source, cursor, base * lift * t.mix, t.pitch, mc.level, player, now, dt, false);
        }
```

(the one Task 7 left with `false`) by

```java
        // The ones that went longest without a ray get this frame's rays.
        List<Map.Entry<Vec3, Want>> order = new ArrayList<>(wanted.entrySet());
        order.sort(java.util.Comparator.comparingDouble(w -> {
            Emitter known = t.emitters.get(w.getKey());
            return known == null ? -2 : known.lastRay;
        }));
        for (Map.Entry<Vec3, Want> w : order) {
            Emitter e = t.emitters.computeIfAbsent(w.getKey(), pos -> new Emitter(pos, w.getValue().level(), t.rate));
            e.level = w.getValue().level();
            e.band = w.getValue().band();
            e.drive = w.getValue().drive();
            e.delay = w.getValue().delay();
            e.crossover = crossover;
            e.stack = w.getValue().pa() ? PaLevel.stackGain(near[w.getValue().band()]) : 1f;
            if (e.update(t, t.source, cursor, base * lift * t.mix, t.pitch, mc.level, player, now, dt, raysLeft > 0)) raysLeft--;
        }
```

The headphones call keeps its `false`.

- [ ] **Step 5: Reset the ray budget.** At the top of `update()`, right after `lastFrame = now;` add `raysLeft = RAYS_PER_FRAME;`.

- [ ] **Step 6: Compile and run the whole suite**

Run: `./gradlew compileJava test -q`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/createbrewery/drunk/MusicPulse.java
git commit -m "feat(sound): speaker budget, stack loudness and ray budget in MusicPulse" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF"
```

---

### Task 9: Room reverb: `ReverbBus`, `RoomProbe`, sends

**Files:**
- Create: `src/main/java/com/createbrewery/drunk/ReverbBus.java`
- Create: `src/main/java/com/createbrewery/drunk/RoomProbe.java`
- Modify: `src/main/java/com/createbrewery/drunk/Emitter.java` (send filter, `wet()`, `create`, `delete`, `update`)
- Modify: `src/main/java/com/createbrewery/drunk/MusicPulse.java` (`update`: the reverb hook)

**Interfaces:**
- Consumes: `RoomAcoustics.estimate` / `Params` / `lerp` (Task 5), `WallFilter.loss`, `Config.CLUB_REVERB`, `Config.REVERB_AMOUNT` (Task 6).
- Produces: `ReverbBus.ensure()`, `slot()`, `apply(Params, float wet, double now)`, `send(int band, float wallLoss)`, `newFilter()`, `setFilterGain(int, float)`, `deleteFilter(int)`, `release()`; `RoomProbe.update(Level, Entity, double now, float dt)`, `params()`, `reset()`.

- [ ] **Step 1: `ReverbBus`**

```java
package com.createbrewery.drunk;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * The one reverb the club's speakers share: an OpenAL EFX auxiliary slot with an (EAX) reverb
 * effect, set from {@link RoomAcoustics}. Each emitter sends to it through a low-pass filter whose
 * gain says how much of its sound reaches the room. Without EFX nothing here does anything.
 * Render thread only.
 */
final class ReverbBus {
    private ReverbBus() {}

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** How much of a speaker goes to the reverb, and the share of that for a subwoofer's band (reverb on bass is mud). */
    static final float SEND = 0.6f, LOW_FACTOR = 0.3f;

    private static int slot = -1, effect = -1;
    private static boolean eax, failed;
    private static float amount;
    private static double lastApply = -1;

    /** The slot to send to, or 0 when there is none. */
    static int slot() {
        return slot > 0 ? slot : 0;
    }

    /** Makes the slot if it is missing; false, quietly, when this OpenAL has no EFX. */
    static boolean ensure() {
        if (failed) return false;
        try {
            if (slot > 0 && EXTEfx.alIsAuxiliaryEffectSlot(slot)) return true;
            long context = ALC10.alcGetCurrentContext();
            if (context == 0 || !ALC10.alcIsExtensionPresent(ALC10.alcGetContextsDevice(context), "ALC_EXT_EFX")) {
                failed = true;
                return false;
            }
            AL10.alGetError();
            slot = EXTEfx.alGenAuxiliaryEffectSlots();
            effect = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
            eax = AL10.alGetError() == AL10.AL_NO_ERROR;
            if (!eax) EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) throw new IllegalStateException("no reverb effect");
            lastApply = -1;
            return true;
        } catch (Throwable e) {
            LOGGER.info("Club reverb off: {}", e.toString());
            failed = true;
            release();
            return false;
        }
    }

    /** Sets the room and how much of it is heard ({@code wet} 0 keeps the slot but sends nothing). At most ten times a second. */
    static void apply(RoomAcoustics.Params p, float wet, double now) {
        if (slot <= 0) return;
        amount = wet;
        if (now - lastApply < 0.1) return;
        lastApply = now;
        try {
            if (eax) {
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, p.decayTime());
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, p.density());
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, p.diffusion());
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAIN, 0.32f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, p.lateGain());
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, p.reflectionsGain());
            } else {
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, p.decayTime());
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DENSITY, p.density());
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DIFFUSION, p.diffusion());
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, 0.32f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, p.lateGain());
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, p.reflectionsGain());
            }
            // The slot only takes the new settings when the effect is loaded into it again.
            EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
            AL10.alGetError(); // the game must not log an EFX hiccup as its own error
        } catch (Throwable ignored) {}
    }

    /** The send gain for an emitter of {@code band} behind {@code wallLoss} (1 in the open). */
    static float send(int band, float wallLoss) {
        return Math.min(1f, SEND * amount * wallLoss * (band == Emitter.LOW ? LOW_FACTOR : 1f));
    }

    static int newFilter() {
        try {
            int filter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            AL10.alGetError();
            return filter;
        } catch (Throwable e) {
            return -1;
        }
    }

    static void setFilterGain(int filter, float gain) {
        try {
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
            AL10.alGetError();
        } catch (Throwable ignored) {}
    }

    static void deleteFilter(int filter) {
        try {
            EXTEfx.alDeleteFilters(filter);
            AL10.alGetError();
        } catch (Throwable ignored) {}
    }

    /** Frees the slot. Only call with no emitter left: OpenAL Soft refuses to delete a slot sources still send to. */
    static void release() {
        try {
            if (slot > 0) {
                EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
                EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
            }
            if (effect > 0) EXTEfx.alDeleteEffects(effect);
            AL10.alGetError();
        } catch (Throwable ignored) {}
        slot = -1;
        effect = -1;
    }
}
```

`AL_EFFECT_NULL` was not in the verified constant list; if it does not compile use `0`.

- [ ] **Step 2: `RoomProbe`**

```java
package com.createbrewery.drunk;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Listens to the room: sixteen rays from the listener's eye (eight round, four up and four down at
 * 45 degrees) four times a second, turned into reverb settings by {@link RoomAcoustics} and eased
 * over about a second, so walking through a door changes the sound gradually. Render thread only.
 */
final class RoomProbe {
    static final int RAYS = 16;
    private static final double EVERY = 0.25, EASE_SECONDS = 0.7;
    private static final Vec3[] DIRS = new Vec3[RAYS];

    static {
        int i = 0;
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            DIRS[i++] = new Vec3(Math.cos(a), 0, Math.sin(a));
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI * 2 * k / 4 + Math.PI / 4;
            DIRS[i++] = new Vec3(Math.cos(a), 1, Math.sin(a)).normalize();
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI * 2 * k / 4 + Math.PI / 4;
            DIRS[i++] = new Vec3(Math.cos(a), -1, Math.sin(a)).normalize();
        }
    }

    private double last = -1;
    private RoomAcoustics.Params target, eased;

    void update(Level level, Entity listener, double now, float dt) {
        if (now - last >= EVERY) {
            last = now;
            Vec3 eye = listener.getEyePosition();
            double[] dist = new double[RAYS];
            boolean[] hit = new boolean[RAYS];
            for (int i = 0; i < RAYS; i++) {
                Vec3 to = eye.add(DIRS[i].scale(RoomAcoustics.MAX_RAY));
                BlockHitResult r = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, listener));
                hit[i] = r.getType() != HitResult.Type.MISS;
                dist[i] = hit[i] ? r.getLocation().distanceTo(eye) : RoomAcoustics.MAX_RAY;
            }
            target = RoomAcoustics.estimate(dist, hit);
            if (eased == null) eased = target;
        }
        if (eased != null) eased = RoomAcoustics.lerp(eased, target, 1 - Math.exp(-dt / EASE_SECONDS));
    }

    /** The room as eased so far; null before the first look. */
    RoomAcoustics.Params params() {
        return eased;
    }

    /** Nothing plays any more: the next song looks at the room afresh. */
    void reset() {
        last = -1;
        target = null;
        eased = null;
    }
}
```

- [ ] **Step 3: Emitter sends.** In `Emitter` add fields

```java
    /** Its send to the room reverb: the low-pass filter, the gain last set on it, and whether the send is connected. */
    private int sendFilter = -1;
    private float sent = -1f;
    private boolean sendOn;
```

add the method

```java
    /** Connects the source to the room reverb, or lets go of it when there is none; the filter follows how walled off it is. */
    private void wet() {
        int slot = ReverbBus.slot();
        if (slot == 0) {
            if (sendOn) {
                AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
                AL10.alGetError();
                sendOn = false;
            }
            return;
        }
        if (sendFilter < 0) sendFilter = ReverbBus.newFilter();
        if (sendFilter < 0) return;
        float g = ReverbBus.send(band, (float) WallFilter.loss(muffle, walls));
        if (sendOn && Math.abs(g - sent) < 0.02f) return;
        ReverbBus.setFilterGain(sendFilter, g);
        // A changed filter only counts once it is attached again.
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 0, sendFilter);
        AL10.alGetError();
        sent = g;
        sendOn = true;
    }
```

In `create`, after `source = AL10.alGenSources();` add `sendOn = false; sent = -1f;`. In `update`, just before the `if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING ...` line add `if (!phones) wet();`. In `delete`, after the `source = -1;` line add:

```java
        if (sendFilter >= 0) ReverbBus.deleteFilter(sendFilter);
        sendFilter = -1;
```

- [ ] **Step 4: The hook in `MusicPulse.update`.** Add near the other statics:

```java
    private static final RoomProbe ROOM = new RoomProbe();

    /** Once a frame: keeps the shared reverb on the room around the listener while any club speaker plays, and frees it when none does. */
    private static void reverb(Minecraft mc, LocalPlayer player, double now, float dt) {
        boolean any = false;
        for (Track t : tracks) {
            if (!t.emitters.isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) {
            ReverbBus.release();
            ROOM.reset();
            return;
        }
        if (player == null || mc.level == null || !ReverbBus.ensure()) return;
        ROOM.update(mc.level, player, now, dt);
        boolean on = Config.CLIENT_SPEC.isLoaded() && Config.CLUB_REVERB.get();
        float amount = on ? Config.REVERB_AMOUNT.get().floatValue() : 0f;
        if (ROOM.params() != null) ReverbBus.apply(ROOM.params(), amount, now);
    }
```

and in `update()` call `reverb(mc, player, now, dt);` directly after the `for (Track t : tracks) { ... }` loop (before `playing = heard;`).

- [ ] **Step 5: Compile and run the whole suite**

Run: `./gradlew compileJava test -q`
Expected: `BUILD SUCCESSFUL`, all tests pass. A missing LWJGL constant is a compile error naming it; fix it with the closest `EXTEfx` field (`javap -cp <lwjgl-openal-3.3.3.jar> -constants org.lwjgl.openal.EXTEfx`).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/createbrewery/drunk/ReverbBus.java src/main/java/com/createbrewery/drunk/RoomProbe.java src/main/java/com/createbrewery/drunk/Emitter.java src/main/java/com/createbrewery/drunk/MusicPulse.java
git commit -m "feat(sound): room reverb through one shared EFX slot" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RCSSQVJrcekTXHV8ii45BF"
```

---

### Task 10: Full verification and hand-over

**Files:** none (verification only).

- [ ] **Step 1: Whole build.**

Run: `./gradlew test runGameTestServer jar`
Expected: `BUILD SUCCESSFUL`; unit tests and the existing GameTests pass; a jar under `build/libs/`. If the main tree does not compile because of someone else's uncommitted edits, repeat this in a clean `git worktree` at HEAD.

- [ ] **Step 2: Boundary check.**

Run: `grep -n "net.minecraft\|org.lwjgl\|com.mojang" src/main/java/com/createbrewery/drunk/{SyncPolicy,EmitterBudget,PaLevel,RoomAcoustics}.java`
Expected: no output.

- [ ] **Step 3: Write the ledger line and list for the user** (no jar copy, no push; the user asks for those). The in-game checks from the spec for the user:
  - `logs/latest.log`: no `resynced` storm while standing in a booth with 8 or more speakers for 5 minutes; after a deliberate lag (F3+T, alt-tab) a few lines and no storm. The new line names the reason (`stopped`, `starved`, `drift`); report which one dominates.
  - Walk from the dance floor to a stairwell, a toilet and outside: the reverb changes gradually, is dry outside, no clicks.
  - With Sound Physics Remastered on: the club speakers are not reverberated twice.
  - 2 and 8 speakers: about the same loudness on the dance floor; the clip light still reacts.
  - `reverbAmount` 0 and `clubReverb` off: the club sounds as before; `maxSpeakers` 4 with a large club: far speakers fall silent, subs stay.
  - No `AL lib` or OpenAL error lines, no `createbrewery` errors, no `ZipException` in the log.
