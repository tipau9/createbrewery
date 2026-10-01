# Club Lights Realism Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Club fixtures respond and look like their real counterparts (dimmer curve, lamp inertia, motor speed, colour fades, soft beams, zoom, gobo, prism, pixel colours, light spots, room light) and the console can program them (fan, colour programs, position presets).

**Architecture:** A pure `FixtureResponse` models each fixture's physics. `DmxProgram` gains pure per-fixture functions (`levelFor`, `colorFor`, `pixelColor`, `aim` with fan) and new settings (`colorFx`, `gobo`, `prism`, `zoom`). `FixtureBlockEntity` uses both and exposes optics to `FixtureRenderer`. A small generic `LightBudget` picks which moving heads may cast a Veil room light.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit 5 (pure logic), NeoForge GameTests (`ClubGameTests`), Registrate datagen (lang).

**Spec:** `docs/superpowers/specs/2026-10-01-club-lights-realism-design.md`

## Rulings made while planning (the plan follows these)

- **Order.** Tasks run logic first, then fixtures and renderer, then GUI, so every renderer change compiles against finished data. The spec's stages map to: Stage 1 = Tasks 1-2, Stage 2 = Tasks 3, 5, 6-8 (data plumbing in Task 3), Stage 3 = Tasks 4, 9 (pure logic in Task 4, controls in Task 9).
- **Position presets are relative to the fixture's facing**, because pan/tilt are "degrees off its facing" (`LaserBeams.direction`). So "ceiling" becomes `STRAIGHT` (along the facing: a floor-mounted head points at the ceiling, a hung one at the floor), and "crowd" is a gentle converging sway, not an absolute aim.
- **Motor timing.** With speed limit 9 deg/tick and acceleration 1.5 deg/tick^2, a 100-degree move takes about 17 ticks (the spec's "about 11" ignored the ramps). Tests assert relative behaviour, not that number.
- **Photosensitivity glide for fanned fixtures.** `DmxProgram.update` glides each group level; a fixture with `fan > 0` takes `levelFor` (no glide), so the fixture applies the same 0.1/tick glide itself when `noFlashing`.
- **Gobo 0 (circle) draws today's `renderImpactDot`**, so defaults look unchanged.

## Global Constraints

- Dimmer: `level ^ 1.6`; `DIMMER_EXPONENT = 1.6f`. Motors: pan 9, tilt 6 degrees per tick, acceleration 1.5 degrees per tick squared. Lamp time constants in ticks (rise/fall): LED bar and PAR 1/2, moving head 2/3, blinder 2/7. Colour fade: moving head 5, LED bar and PAR 2, blinder none (fixed tungsten `0xFFB060`).
- Settings ranges: `colorFx` 0..3 (static, fade, rainbow, complement), `gobo` 0..3 (circle, star, dots, bar), `zoom` 0..2 (default 1), `prism` boolean, `fan` 0..7 on the fixture, `MOVES` 8 (old 0..3 keep their meaning).
- Cost budget per fixture per tick: at most 3 raycasts (prism); beams drawn as 3 nested cones of 10 quads; Veil head lights capped at 6 per client.
- Pure classes (`FixtureResponse`, `GoboShape`, `LightBudget`, the new `DmxProgram` functions) have no Minecraft imports.
- `DmxProgramTest` and `ClubStateTest` stay green **unchanged** after every task. Defaults (static colour, circle, prism off, zoom normal, fan 0) must look like today.
- `DmxControl` registrar version "2" becomes "3"; new actions `COLORFX = 13, GOBO = 14, PRISM = 15, ZOOM = 16`; the handler keeps its finite check, and unknown actions stay no-ops.
- Every task ends with green `./gradlew test`; tasks touching block entities or controls also run `./gradlew runGameTestServer`. Run Gradle from `C:\Users\Anwender\dev\createbrewery`.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Never copy the jar into `mods/` while `javaw` runs.

## Review Focus

- A held target converges and never oscillates forever (dimmer, colour, motor) (Task 1 tests).
- A motor never exceeds speed, and a target equal to the position produces no movement (Task 1).
- Old console saves (only `Faders` present) and old cues load with colour static, gobo circle, prism off, zoom 1 (Task 3 GameTest and unit test).
- `move` values from old saves 0..3 are unchanged; unknown or huge values wrap, never throw (Task 3 and 4).
- `levelFor(s, g, 0)` equals the group's level for every program (Task 4); fan wraps for fan 0..7 and for out-of-range input.
- With "no flashing", a fanned fixture still glides (Task 6 logic test through `FixtureResponse.glide`).
- No fixture draws anything when dark; prism does not triple raycasts for non-heads (Task 6/7 review by reading).
- Veil absent or Iris active: no head light is allocated and nothing throws (Task 8).

---

## File structure

| File | Responsibility |
|---|---|
| `block/club/FixtureResponse.java` (new) | Pure: dimmer curve, lamp inertia, colour fade, motor, glide |
| `block/club/GoboShape.java` (new) | Pure: spot outlines for the four gobos |
| `block/club/LightBudget.java` (new) | Pure: keeps the N nearest of the offered keys |
| `block/club/DmxProgram.java` | New settings, 8 moves, `colorFor`, `pixelColor`, `levelFor`, `aim` with fan |
| `block/club/DmxConsoleBlockEntity.java` | Setters and NBT for the new settings |
| `block/club/DmxTimecode.java` | `Look` carries the new settings |
| `block/club/DmxControl.java` | New actions, version "3" |
| `block/club/FixtureBlockEntity.java` | `fan`, `FixtureResponse`, optics getters, prism beams, head light |
| `block/club/FixtureBlock.java` | Sneak-click cycles the fan |
| `block/club/FixtureRenderer.java` | Soft cones, zoom, gobo spot, prism, pixel colours, light spots |
| `block/club/StrobeRoomLight.java` | Overload placing a light at a point |
| `block/club/DmxConsoleScreen.java` | Four new buttons, eight move labels |
| `CreateBrewery.java`, `lang/de_de.json`, generated lang | Lang keys |
| tests in `src/test/java/com/createbrewery/block/club/` | `FixtureResponseTest`, `GoboShapeTest`, `LightBudgetTest`, `DmxProgramLightTest`, `DmxTimecodeTest` (extended) |
| `ClubGameTests.java`, `ClubTestAccess.java` | GameTests and package-private access |

---

### Task 1: `FixtureResponse` (pure physics) with tests

**Files:**
- Create: `src/main/java/com/createbrewery/block/club/FixtureResponse.java`
- Test: `src/test/java/com/createbrewery/block/club/FixtureResponseTest.java`

**Interfaces:**
- Produces:
  - `record Lamp(float riseTicks, float fallTicks)` with constants `LED`, `PAR`, `HEAD`, `TUNGSTEN`
  - `float dimmer(Lamp lamp, float level)` (steps once, returns the output intensity 0..1)
  - `static float curve(float level)`
  - `int color(int target, float fadeTicks)` (0 = instant; first call is instant)
  - `void motor(float panTarget, float tiltTarget)` then fields `pan`, `tilt` (degrees)
  - `static float glide(float current, float target)` (max 0.1 per tick, same as `DmxProgram.GLIDE`)

- [ ] **Step 1: Write the failing test**

```java
package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixtureResponseTest {

    @Test
    void dimmerCurveKeepsTheEndsAndIsMonotonic() {
        assertEquals(0f, FixtureResponse.curve(0f), 1e-6);
        assertEquals(1f, FixtureResponse.curve(1f), 1e-6);
        assertTrue(FixtureResponse.curve(0.5f) < 0.5f, "low levels must look darker than linear");
        float prev = -1f;
        for (int i = 0; i <= 100; i++) {
            float v = FixtureResponse.curve(i / 100f);
            assertTrue(v >= prev);
            prev = v;
        }
        assertEquals(0f, FixtureResponse.curve(-3f), 1e-6, "out of range is clamped");
        assertEquals(1f, FixtureResponse.curve(7f), 1e-6);
    }

    private static int ticksTo(FixtureResponse r, FixtureResponse.Lamp lamp, float level, float goal, boolean up) {
        for (int i = 1; i < 200; i++) {
            float out = r.dimmer(lamp, level);
            if (up ? out >= goal : out <= goal) return i;
        }
        return 200;
    }

    @Test
    void tungstenFallsSlowerThanItRisesAndLedIsNearlyInstant() {
        FixtureResponse tungsten = new FixtureResponse();
        int rise = ticksTo(tungsten, FixtureResponse.Lamp.TUNGSTEN, 1f, 0.9f, true);
        int fall = ticksTo(tungsten, FixtureResponse.Lamp.TUNGSTEN, 0f, 0.1f, false);
        assertTrue(fall > rise * 2, "the filament must glow on: rise " + rise + ", fall " + fall);

        FixtureResponse led = new FixtureResponse();
        assertTrue(ticksTo(led, FixtureResponse.Lamp.LED, 1f, 0.9f, true) <= 3);
    }

    @Test
    void aHeldTargetConvergesAndStaysPut() {
        FixtureResponse r = new FixtureResponse();
        float out = 0;
        for (int i = 0; i < 100; i++) out = r.dimmer(FixtureResponse.Lamp.HEAD, 0.6f);
        assertEquals(FixtureResponse.curve(0.6f), out, 1e-3);
        assertEquals(out, r.dimmer(FixtureResponse.Lamp.HEAD, 0.6f), 1e-6, "an at-rest output drifted");
    }

    @Test
    void colourFadesToTheTargetAndFirstCallIsInstant() {
        FixtureResponse r = new FixtureResponse();
        assertEquals(0xFF0000, r.color(0xFF0000, 5f), "the first colour must not fade up from black");
        int c = r.color(0x0000FF, 5f);
        assertNotEquals(0x0000FF, c, "a fade is not instant");
        for (int i = 0; i < 100; i++) c = r.color(0x0000FF, 5f);
        assertEquals(0x0000FF, c);
        assertEquals(0x00FF00, r.color(0x00FF00, 0f), "zero ticks is instant");
    }

    @Test
    void motorNeverExceedsSpeedAndALongMoveTakesLonger() {
        FixtureResponse r = new FixtureResponse();
        int shortMove = ticksToAim(r, 5f);
        FixtureResponse l = new FixtureResponse();
        int longMove = ticksToAim(l, 100f);
        assertTrue(longMove > shortMove + 5, "short " + shortMove + ", long " + longMove);

        FixtureResponse m = new FixtureResponse();
        float last = 0, lastVel = 0;
        for (int i = 0; i < 60; i++) {
            m.motor(100f, 80f);
            assertTrue(Math.abs(m.pan - last) <= FixtureResponse.PAN_SPEED + 1e-3, "pan jumped");
            if (m.pan != 100f) assertTrue(Math.abs(Math.abs(m.pan - last) - lastVel) <= FixtureResponse.ACCEL + 1e-3, "accelerated too hard");
            lastVel = Math.abs(m.pan - last);
            last = m.pan;
        }
        assertEquals(100f, m.pan, 1e-3);
        assertEquals(80f, m.tilt, 1e-3);
    }

    private static int ticksToAim(FixtureResponse r, float deg) {
        for (int i = 1; i < 200; i++) {
            r.motor(deg, 0f);
            if (Math.abs(r.pan - deg) < 0.01f) return i;
        }
        return 200;
    }

    @Test
    void aMotorAtItsTargetDoesNotMove() {
        FixtureResponse r = new FixtureResponse();
        for (int i = 0; i < 5; i++) r.motor(0f, 0f);
        assertEquals(0f, r.pan);
        assertEquals(0f, r.tilt);
    }

    @Test
    void glideMovesAtMostATenth() {
        assertEquals(0.1f, FixtureResponse.glide(0f, 1f), 1e-6);
        assertEquals(0.9f, FixtureResponse.glide(1f, 0f), 1e-6);
        assertEquals(0.55f, FixtureResponse.glide(0.5f, 0.55f), 1e-6);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.createbrewery.block.club.FixtureResponseTest"`
Expected: FAIL (compile error: `FixtureResponse` not found).

- [ ] **Step 3: Implement**

```java
package com.createbrewery.block.club;

/**
 * How one club fixture answers the console: the dimmer curve and the lamp's inertia, a colour that
 * fades instead of snapping, and a moving head's motors (limited speed and acceleration). One
 * instance per fixture, stepped once per client tick. Free of Minecraft classes (unit-tested).
 */
final class FixtureResponse {
    /** Between linear and the square law of real dimmers. */
    static final float DIMMER_EXPONENT = 1.6f;
    /** Degrees per tick (20 ticks a second): 180 and 120 degrees a second; and how hard a motor may speed up or brake. */
    static final float PAN_SPEED = 9f, TILT_SPEED = 6f, ACCEL = 1.5f;
    /** At most this much a tick when flashing lights are hidden (the same as {@code DmxProgram.GLIDE}). */
    private static final float GLIDE = 0.1f;

    /** Ticks a lamp takes to rise to, and fall from, 63 percent of a step. */
    record Lamp(float riseTicks, float fallTicks) {
        static final Lamp LED = new Lamp(1, 2), PAR = new Lamp(1, 2), HEAD = new Lamp(2, 3), TUNGSTEN = new Lamp(2, 7);
    }

    float pan, tilt;
    private float panVel, tiltVel;
    private float out;
    private float r, g, b;
    private boolean colored;

    static float curve(float level) {
        float l = level < 0 ? 0 : level > 1 ? 1 : level;
        return (float) Math.pow(l, DIMMER_EXPONENT);
    }

    /** One tick: the lamp's output intensity for the console level {@code level}. */
    float dimmer(Lamp lamp, float level) {
        float target = curve(level);
        float ticks = target > out ? lamp.riseTicks() : lamp.fallTicks();
        out += (target - out) * (1f - (float) Math.exp(-1f / Math.max(0.01f, ticks)));
        if (Math.abs(target - out) < 0.001f) out = target;
        return out;
    }

    /** One tick: the colour (0xRRGGBB) fading toward {@code target} over about {@code fadeTicks}; 0 is instant. */
    int color(int target, float fadeTicks) {
        float tr = (target >> 16 & 255) / 255f, tg = (target >> 8 & 255) / 255f, tb = (target & 255) / 255f;
        if (!colored || fadeTicks <= 0f) {
            r = tr;
            g = tg;
            b = tb;
            colored = true;
        } else {
            float k = 1f - (float) Math.exp(-1f / fadeTicks);
            r += (tr - r) * k;
            g += (tg - g) * k;
            b += (tb - b) * k;
        }
        return Math.round(r * 255f) << 16 | Math.round(g * 255f) << 8 | Math.round(b * 255f);
    }

    /** One tick of both motors toward the target angles (degrees off the facing). */
    void motor(float panTarget, float tiltTarget) {
        float[] p = axis(pan, panVel, panTarget, PAN_SPEED);
        pan = p[0];
        panVel = p[1];
        float[] t = axis(tilt, tiltVel, tiltTarget, TILT_SPEED);
        tilt = t[0];
        tiltVel = t[1];
    }

    private static float[] axis(float pos, float vel, float target, float maxSpeed) {
        float err = target - pos;
        // The speed from which braking at ACCEL stops exactly on the target.
        float desired = Math.signum(err) * Math.min(maxSpeed, (float) Math.sqrt(2f * ACCEL * Math.abs(err)));
        vel += Math.max(-ACCEL, Math.min(ACCEL, desired - vel));
        pos += vel;
        // Close and slow: arrived (a discrete motor would otherwise ring around the target).
        if (Math.abs(target - pos) < ACCEL && Math.abs(vel) < 2f * ACCEL) {
            pos = target;
            vel = 0f;
        }
        return new float[] {pos, vel};
    }

    /** Photosensitivity: a level moves at most a tenth a tick toward {@code target}. */
    static float glide(float current, float target) {
        return current + Math.max(-GLIDE, Math.min(GLIDE, target - current));
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "com.createbrewery.block.club.FixtureResponseTest"`
Expected: PASS (7 tests). If `motorNeverExceedsSpeedAndALongMoveTakesLonger` fails on the acceleration assertion at the snap tick, loosen only that assertion to skip the tick where `m.pan == 100f` (already written); do not weaken the speed assertion.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/FixtureResponse.java src/test/java/com/createbrewery/block/club/FixtureResponseTest.java
git commit -m "feat(club): FixtureResponse, dimmer curve, lamp inertia, colour fade and motor limits"
```

---

### Task 2: Fixtures use `FixtureResponse`

**Files:**
- Modify: `src/main/java/com/createbrewery/block/club/FixtureBlockEntity.java` (fields, `clientTick`)
- Test: existing suites (client-only change)

**Interfaces:**
- Consumes: `FixtureResponse` (Task 1), `DmxProgram.level[]`, `color[]`, `aim(Settings, int)`.
- Produces: `FixtureBlockEntity.level(float)`, `color()`, `direction(...)` unchanged signatures, now backed by the response model.

This task has no unit-testable client logic; the check is compile + both suites + the manual step at the end of Task 9.

- [ ] **Step 1: Add the response and the lamp choice**

In `FixtureBlockEntity`, add the field next to the other client fields:

```java
    private final FixtureResponse response = new FixtureResponse();
```

and this helper:

```java
    private static FixtureResponse.Lamp lampOf(FixtureBlock.Kind kind) {
        return switch (kind) {
            case LED_BAR -> FixtureResponse.Lamp.LED;
            case PAR -> FixtureResponse.Lamp.PAR;
            case MOVING_HEAD -> FixtureResponse.Lamp.HEAD;
            case BLINDER -> FixtureResponse.Lamp.TUNGSTEN;
        };
    }
```

- [ ] **Step 2: Use it in `clientTick`**

Replace these existing lines:

```java
        prevLit = lit;
        lit = program.level[group];
        color = kind == FixtureBlock.Kind.BLINDER ? TUNGSTEN : program.color[group];
```

with:

```java
        prevLit = lit;
        lit = response.dimmer(lampOf(kind), program.level[group]);
        int targetColor = kind == FixtureBlock.Kind.BLINDER ? TUNGSTEN : program.color[group];
        color = response.color(targetColor, kind == FixtureBlock.Kind.BLINDER ? 0f : kind == FixtureBlock.Kind.MOVING_HEAD ? 5f : 2f);
```

and replace the moving head block:

```java
        if (kind == FixtureBlock.Kind.MOVING_HEAD) {
            // Motors take a few ticks to get there, like a real head.
            float[] aim = program.aim(settings, group);
            pan += (aim[0] - pan) * 0.3f;
            tilt += (aim[1] - tilt) * 0.3f;
        }
```

with:

```java
        if (kind == FixtureBlock.Kind.MOVING_HEAD) {
            // Motors with a top speed and a limit on how hard they speed up and brake, like a real head.
            float[] aim = program.aim(settings, group);
            response.motor(aim[0], aim[1]);
            pan = response.pan;
            tilt = response.tilt;
        }
```

- [ ] **Step 3: Run the suites**

Run: `./gradlew test runGameTestServer`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/FixtureBlockEntity.java
git commit -m "feat(club): fixtures answer the console through FixtureResponse"
```

---

### Task 3: New console settings (data, NBT, timecode, controls)

**Files:**
- Modify: `DmxProgram.java` (Settings fields, constants), `DmxConsoleBlockEntity.java` (setters, `write`/`read`), `DmxTimecode.java` (`Look`), `DmxControl.java`, `ClubTestAccess.java`
- Test: `DmxTimecodeTest.java` (extend), `ClubGameTests.java`

**Interfaces:**
- Consumes: existing `Settings`, `Look.of`, `Look.applyTo`, `DmxConsoleBlockEntity.write/read`.
- Produces:
  - `DmxProgram.Settings.colorFx` (int), `gobo` (int), `zoom` (int, default 1), `prism` (boolean)
  - constants `DmxProgram.COLOR_FX = 4`, `GOBOS = 4`, `ZOOMS = 3`, `MOVES = 8`
  - `DmxConsoleBlockEntity.setColorFx(int)`, `setGobo(int)`, `setZoom(int)`, `setPrism(boolean)`
  - `DmxControl.COLORFX = 13`, `GOBO = 14`, `PRISM = 15`, `ZOOM = 16`
  - `ClubTestAccess.lightSettingsRoundTrip(DmxConsoleBlockEntity)`, `ClubTestAccess.oldTagDefaults()`

- [ ] **Step 1: Find every `Look` construction**

Run: `rg -n "new DmxTimecode.Look\\(|new Look\\(" src`
Expected: the list of call sites you will update in Step 4 (the tests build Looks too).

- [ ] **Step 2: Write the failing tests**

Extend `DmxTimecodeTest` (keep its existing tests; add):

```java
    @Test
    void aLookCarriesTheLightSettingsAndAppliesThemBack() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colorFx = 2;
        s.gobo = 3;
        s.zoom = 0;
        s.prism = true;
        DmxTimecode.Look look = DmxTimecode.Look.of(s);
        DmxProgram.Settings back = new DmxProgram.Settings();
        look.applyTo(back);
        assertEquals(2, back.colorFx);
        assertEquals(3, back.gobo);
        assertEquals(0, back.zoom);
        assertTrue(back.prism);
    }

    @Test
    void freshSettingsLookLikeToday() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        assertEquals(0, s.colorFx);
        assertEquals(0, s.gobo);
        assertEquals(1, s.zoom, "zoom defaults to normal");
        assertFalse(s.prism);
        assertEquals(8, DmxProgram.MOVES);
    }
```

Add GameTests to `ClubGameTests`:

```java
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
```

`ClubTestAccess` additions:

```java
    /** Sets the light settings (out of range on purpose), writes and reads them back: true if they wrapped and survived. */
    public static boolean lightSettingsRoundTrip(DmxConsoleBlockEntity dmx) {
        dmx.setColorFx(99);
        dmx.setGobo(-1);
        dmx.setZoom(7);
        dmx.setPrism(true);
        dmx.setMove(13);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        DmxConsoleBlockEntity.write(dmx.settings, tag);
        DmxProgram.Settings back = new DmxProgram.Settings();
        DmxConsoleBlockEntity.read(back, tag);
        return back.colorFx == Math.floorMod(99, DmxProgram.COLOR_FX) && back.gobo == Math.floorMod(-1, DmxProgram.GOBOS)
            && back.zoom == Math.floorMod(7, DmxProgram.ZOOMS) && back.prism && back.move == Math.floorMod(13, DmxProgram.MOVES);
    }

    /** A tag from before the light settings: only the old keys. */
    public static boolean oldTagDefaults() {
        DmxProgram.Settings old = new DmxProgram.Settings();
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        DmxConsoleBlockEntity.write(old, tag);
        tag.remove("ColorFx");
        tag.remove("Gobo");
        tag.remove("Prism");
        tag.remove("Zoom");
        DmxProgram.Settings back = new DmxProgram.Settings();
        back.zoom = 2;
        back.colorFx = 3;
        DmxConsoleBlockEntity.read(back, tag);
        return back.colorFx == 0 && back.gobo == 0 && back.zoom == 1 && !back.prism;
    }
```

Note: `CONSOLE` already exists in `ClubGameTests` (the sub-project 1 tests define it); reuse it. `setMove` is package-private, which `ClubTestAccess` (same package) can call.

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew test runGameTestServer`
Expected: compile errors (fields/setters missing).

- [ ] **Step 4: Implement**

`DmxProgram`: change `static final int CIRCLE = 0, FIGURE8 = 1, SWEEP = 2, BALLYHOO = 3, MOVES = 4;` to

```java
    static final int CIRCLE = 0, FIGURE8 = 1, SWEEP = 2, BALLYHOO = 3, CROWD = 4, STRAIGHT = 5, FAN = 6, MIRROR = 7, MOVES = 8;
    static final int STATIC = 0, FADE = 1, RAINBOW = 2, COMPLEMENT = 3, COLOR_FX = 4;
    static final int GOBOS = 4, ZOOMS = 3;
```

and in `Settings` add:

```java
        /** Colour program (see COLOR_FX), the moving heads' gobo and zoom, and whether their prism is in. */
        int colorFx, gobo, zoom = 1;
        boolean prism;
```

`DmxConsoleBlockEntity`: setters next to the others:

```java
    void setColorFx(int v) {
        settings.colorFx = Math.floorMod(v, DmxProgram.COLOR_FX);
        changed();
    }

    void setGobo(int v) {
        settings.gobo = Math.floorMod(v, DmxProgram.GOBOS);
        changed();
    }

    void setZoom(int v) {
        settings.zoom = Math.floorMod(v, DmxProgram.ZOOMS);
        changed();
    }

    void setPrism(boolean on) {
        settings.prism = on;
        changed();
    }
```

In `write(Settings, CompoundTag)` add after `tag.putBoolean("Blackout", ...)`:

```java
        tag.putInt("ColorFx", s.colorFx);
        tag.putInt("Gobo", s.gobo);
        tag.putInt("Zoom", s.zoom);
        tag.putBoolean("Prism", s.prism);
```

In `read(Settings, CompoundTag)` add after the `s.blackout = ...` line:

```java
        s.colorFx = Math.floorMod(tag.getInt("ColorFx"), DmxProgram.COLOR_FX);
        s.gobo = Math.floorMod(tag.getInt("Gobo"), DmxProgram.GOBOS);
        s.zoom = tag.contains("Zoom") ? Math.floorMod(tag.getInt("Zoom"), DmxProgram.ZOOMS) : 1;
        s.prism = tag.getBoolean("Prism");
```

(`getInt` of a missing key is 0, which is right for colour program and gobo; only zoom needs an explicit default.) The existing `s.move = Math.floorMod(tag.getInt("Move"), DmxProgram.MOVES)` and `setMove` already use the constant, so they now wrap at 8.

`DmxTimecode.Look`: extend the record and its two methods:

```java
    record Look(float[] faders, int[] colors, float master, int program, int move, int rate, boolean blackout,
                int colorFx, int gobo, int zoom, boolean prism) {
        static Look of(DmxProgram.Settings s) {
            return new Look(s.faders.clone(), s.colors.clone(), s.master, s.program, s.move, s.rate, s.blackout,
                s.colorFx, s.gobo, s.zoom, s.prism);
        }

        void applyTo(DmxProgram.Settings s) {
            System.arraycopy(faders, 0, s.faders, 0, DmxProgram.GROUPS);
            System.arraycopy(colors, 0, s.colors, 0, DmxProgram.GROUPS);
            s.master = master;
            s.program = program;
            s.move = move;
            s.rate = rate;
            s.blackout = blackout;
            s.colorFx = colorFx;
            s.gobo = gobo;
            s.zoom = zoom;
            s.prism = prism;
        }
    }
```

Update every call site from Step 1 that builds a `Look` with the old 7-argument constructor (tests: append `0, 0, 1, false`).

`DmxControl`: constants and handler cases, registrar version:

```java
        BLACKOUT = 8, STORE = 9, RECALL = 10, RECORD = 11, CLEAR_SHOW = 12, COLORFX = 13, GOBO = 14, PRISM = 15, ZOOM = 16;
```

```java
            case COLORFX -> dmx.setColorFx((int) c.value);
            case GOBO -> dmx.setGobo((int) c.value);
            case PRISM -> dmx.setPrism(c.value > 0.5f);
            case ZOOM -> dmx.setZoom((int) c.value);
```

placed before `default -> {}`, and change `event.registrar("2")` to `event.registrar("3")` with the comment line `// "3": colour programs, gobo, prism and zoom were added.` above it.

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew test runGameTestServer`
Expected: PASS (all prior tests plus the two unit tests and two GameTests).

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "feat(club): console settings for colour programs, gobo, prism and zoom"
```

---

### Task 4: `DmxProgram` per-fixture functions (fan, colour programs, presets)

**Files:**
- Modify: `src/main/java/com/createbrewery/block/club/DmxProgram.java` (the `update` method, `aim`, `pixel`, new methods)
- Test: `src/test/java/com/createbrewery/block/club/DmxProgramLightTest.java` (new); `DmxProgramTest` and `DmxProgramMixerTest` and `DmxProgramGateTest` stay unchanged and green

**Interfaces:**
- Consumes: `Settings` fields from Task 3, `ClubState` (`beat`, `env`, `breakdown`, `dropLevel`, `buildUp`, `bassCut`, `filterClosed`, `period`, `playing`, `noFlashing`).
- Produces:
  - `float levelFor(Settings s, int group, int fan)` (the target level, no glide; equals `level[g]` for fan 0 when not gliding)
  - `int colorFor(Settings s, int group, int fan)`
  - `int pixelColor(Settings s, int i, int pixels, int group, int fan)`
  - `float[] aim(Settings s, int g, int fan)` (and the old `aim(Settings, int)` delegating with fan 0)
  - `boolean noFlashing()`

- [ ] **Step 1: Write the failing tests**

```java
package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmxProgramLightTest {

    private static DmxProgram run(DmxProgram.Settings s, int beats) {
        DmxProgram p = new DmxProgram();
        ClubState c = new ClubState();
        long t = 0;
        for (int i = 0; i < beats; i++) {
            c.update(0.05f, 1f, 0, 0, true, 0.5, ClubState.Mixer.NONE, false);
            p.update(s, t++, 0.05f, c);
            for (int k = 0; k < 9; k++) {
                c.update(0.05f, 0f, 0, 0, true, 0.5, ClubState.Mixer.NONE, false);
                p.update(s, t++, 0.05f, c);
            }
        }
        return p;
    }

    @Test
    void fanZeroEqualsTheGroupLevelInEveryProgram() {
        for (int program = 0; program < DmxProgram.PROGRAMS; program++) {
            DmxProgram.Settings s = new DmxProgram.Settings();
            s.program = program;
            DmxProgram p = run(s, 3);
            for (int g = 0; g < DmxProgram.GROUPS; g++) {
                assertEquals(p.level[g], p.levelFor(s, g, 0), 1e-5, "program " + program + " group " + g);
            }
        }
    }

    @Test
    void fanShiftsTheChaseSoThePatternTravels() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.program = DmxProgram.CHASE;
        DmxProgram p = run(s, 2);
        int onAt0 = -1, onAt1 = -1;
        for (int g = 0; g < DmxProgram.GROUPS; g++) {
            if (p.levelFor(s, g, 0) > 0.5f) onAt0 = g;
            if (p.levelFor(s, g, 1) > 0.5f) onAt1 = g;
        }
        assertTrue(onAt0 >= 0 && onAt1 >= 0);
        assertNotEquals(onAt0, onAt1, "a fan of 1 must move the running light by a step");
    }

    @Test
    void fanWrapsAndNeverThrows() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        DmxProgram p = run(s, 1);
        for (int fan : new int[] {-9, -1, 0, 7, 8, 100}) {
            for (int g = 0; g < DmxProgram.GROUPS; g++) {
                float l = p.levelFor(s, g, fan);
                assertTrue(l >= 0f && l <= 1f);
                assertTrue(p.colorFor(s, g, fan) >= 0);
            }
        }
    }

    @Test
    void staticColourIsThePaletteAndEveryPixelMatches() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colors[2] = 4;
        DmxProgram p = run(s, 1);
        int base = DmxProgram.PALETTE[4];
        assertEquals(base, p.colorFor(s, 2, 0));
        for (int i = 0; i < 8; i++) assertEquals(p.colorFor(s, 2, 0), p.pixelColor(s, i, 8, 2, 0));
    }

    @Test
    void rainbowDiffersByGroupFanAndPixel() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colorFx = DmxProgram.RAINBOW;
        s.program = DmxProgram.MANUAL;
        DmxProgram p = run(s, 1);
        assertNotEquals(p.colorFor(s, 0, 0), p.colorFor(s, 1, 0));
        assertNotEquals(p.colorFor(s, 0, 0), p.colorFor(s, 0, 1));
        assertNotEquals(p.pixelColor(s, 0, 8, 0, 0), p.pixelColor(s, 7, 8, 0, 0));
    }

    @Test
    void complementAlternatesWithTheStep() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colorFx = DmxProgram.COMPLEMENT;
        s.colors[0] = 1;
        DmxProgram p = run(s, 1);
        int a = p.colorFor(s, 0, 0), b = p.colorFor(s, 0, 1);
        assertEquals(a ^ 0xFFFFFF, b, "fan 1 shows the other half of the pair");
    }

    @Test
    void fadeMovesThroughThePaletteOverTime() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colorFx = DmxProgram.FADE;
        s.program = DmxProgram.MANUAL;
        DmxProgram early = run(s, 1), late = run(s, 12);
        assertNotEquals(early.colorFor(s, 0, 0), late.colorFor(s, 0, 0));
    }

    @Test
    void everyMoveGivesFiniteAnglesForEveryGroupAndFan() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        DmxProgram p = run(s, 2);
        for (int move = 0; move < DmxProgram.MOVES; move++) {
            s.move = move;
            for (int g = 0; g < DmxProgram.GROUPS; g++) {
                for (int fan = 0; fan < 8; fan++) {
                    float[] a = p.aim(s, g, fan);
                    assertTrue(Float.isFinite(a[0]) && Float.isFinite(a[1]), "move " + move);
                }
            }
        }
    }

    @Test
    void theOldMovesKeepTheirMeaning() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        DmxProgram p = new DmxProgram(); // movePhase 0
        s.move = DmxProgram.CIRCLE;
        float[] a = p.aim(s, 0, 0);
        assertEquals(30f, a[0], 1e-4);
        assertEquals(0f, a[1], 1e-4);
        s.move = DmxProgram.SWEEP;
        assertEquals(15f, p.aim(s, 3, 0)[1], 1e-4);
    }

    @Test
    void theDropStillGoesWhite() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colors[3] = 1;
        DmxProgram p = new DmxProgram();
        ClubState c = new ClubState();
        c.update(0.05f, 0, 1f, 0, true, 0.5, ClubState.Mixer.NONE, false);
        p.update(s, 0, 0.05f, c);
        assertEquals(0xFFFFFF, p.colorFor(s, 3, 0));
        assertEquals(0xFFFFFF, p.color[3]);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.createbrewery.block.club.DmxProgramLightTest"`
Expected: FAIL (compile errors: `levelFor`, `colorFor`, `pixelColor`, `aim(.., fan)` missing).

- [ ] **Step 3: Restructure `DmxProgram`**

Add fields (next to `step`, `time`):

```java
    private double clock;
    private boolean noFlashing;
    private final List<Scene> stored = new ArrayList<>();
    // What the last update saw, for levelFor / colorFor of fanned fixtures.
    private float snapEnv, snapTension, snapDrop, snapBass;
    private boolean snapBreakdown, snapShutter;
```

Replace the body of `update(Settings s, long tick, float dt, ClubState c)` with the version below (the loop now only delegates to `target` / `colorFor`):

```java
    void update(Settings s, long tick, float dt, ClubState c) {
        time += dt;
        sinceStep += dt;
        clock += dt / c.period;
        playing = c.playing;
        noFlashing = c.noFlashing;
        float tension = c.buildUp;
        if (c.beat && ++beats % RATES[Math.floorMod(s.rate, RATES.length)] == 0) nextStep();
        if (!playing && sinceStep >= 1.0) nextStep();
        beatPhase = c.beatPhase;
        // One turn of a pattern every four beats; slow and steady with no music.
        movePhase += (float) (playing ? dt * Math.PI / (2 * c.period) : dt * 0.5);

        snapEnv = c.env;
        snapTension = tension;
        snapDrop = c.dropLevel;
        snapBass = Math.max(c.bassCut, c.filterClosed);
        snapBreakdown = c.breakdown;
        // Build-ups strobe faster and faster: a flash every 5 ticks (4 a second) up to every other tick (10).
        // Counted in ticks, like the strobe: a sine at those rates, sampled once a tick, aliases to nothing.
        snapShutter = noFlashing || tension < 0.15f || tick % Math.round(5 - 3 * tension) == 0;

        stored.clear();
        for (Scene sc : s.scenes) if (sc != null) stored.add(sc);

        for (int g = 0; g < GROUPS; g++) {
            float target = target(s, g, 0);
            // Photosensitivity: at most a tenth of full range a tick, half a second from dark to full.
            level[g] = noFlashing ? level[g] + Math.max(-GLIDE, Math.min(GLIDE, target - level[g])) : target;
            color[g] = colorFor(s, g, 0);
        }
    }

    boolean noFlashing() {
        return noFlashing;
    }

    /** The level group {@code group} would show for a fixture {@code fan} steps along (no glide, see {@link #update}). */
    float levelFor(Settings s, int group, int fan) {
        return target(s, Math.floorMod(group, GROUPS), fan);
    }

    private float target(Settings s, int g, int fan) {
        float fader = s.faders[g], lv;
        int stp = step + fan;
        switch (s.program) {
            case CHASE -> {
                if (stored.isEmpty()) {
                    // No scenes: a running light through the groups.
                    lv = Math.floorMod(stp, GROUPS) == g ? fader : 0f;
                } else {
                    lv = stored.get(Math.floorMod(stp, stored.size())).levels()[g];
                }
            }
            case AUTO -> {
                if (!playing) {
                    lv = fader * (0.15f + 0.1f * (float) Math.sin(time * 1.2 + g * 0.8 + fan * 0.4));
                } else if (snapBreakdown) {
                    lv = fader * 0.08f;
                } else {
                    // Odd and even groups trade the beat: a punch that dies before the next kick.
                    boolean on = Math.floorMod(g + stp, 2) == 0;
                    lv = fader * (on ? 0.15f + 0.85f * snapEnv : 0.1f);
                    if (!snapShutter) lv = 0f;
                    else if (snapTension >= 0.15f) lv = Math.max(lv, fader * snapTension);
                }
                // The DJ takes the lows or closes the filter: the room darkens with it (a drop below still hits full).
                lv *= 1f - 0.6f * snapBass;
                if (snapDrop > 0.02f) lv = Math.max(lv, fader * snapDrop);
            }
            default -> lv = fader;
        }
        if ((s.flash >> g & 1) != 0) lv = 1f;
        return s.blackout ? 0f : clamp(lv * s.master);
    }

    /** The colour of group {@code group} for a fixture {@code fan} steps along: palette, colour program, scene or drop white. */
    int colorFor(Settings s, int group, int fan) {
        return colorFor(s, Math.floorMod(group, GROUPS), fan, 0.0);
    }

    /** The colour of pixel {@code i} of {@code pixels} on an LED bar: a colour program spreads along the bar. */
    int pixelColor(Settings s, int i, int pixels, int group, int fan) {
        return colorFor(s, Math.floorMod(group, GROUPS), fan, i / (double) Math.max(1, pixels));
    }

    private int colorFor(Settings s, int g, int fan, double spread) {
        int stp = step + fan;
        int col;
        if (s.program == CHASE && !stored.isEmpty()) {
            col = PALETTE[Math.floorMod(stored.get(Math.floorMod(stp, stored.size())).colors()[g], PALETTE.length)];
        } else {
            int idx = Math.floorMod(s.colors[g], PALETTE.length), base = PALETTE[idx];
            col = switch (s.colorFx) {
                case FADE -> {
                    // One palette colour every 8 beats, each fixture a step along the palette per fan.
                    double p = clock / 8.0 + idx + fan / 8.0 * PALETTE.length / 8.0 + spread * 2;
                    int k = (int) Math.floor(p);
                    yield mix(PALETTE[Math.floorMod(k, PALETTE.length)], PALETTE[Math.floorMod(k + 1, PALETTE.length)], (float) (p - k));
                }
                case RAINBOW -> hsv((float) (((clock / 16.0 + g / 8.0 + fan / 8.0 + spread * 0.5) % 1.0 + 1.0) % 1.0));
                case COMPLEMENT -> Math.floorMod(stp + (spread >= 0.5 ? 1 : 0), 2) == 0 ? base : base ^ 0xFFFFFF;
                default -> base;
            };
            if (s.program == AUTO && snapDrop > 0.02f) col = mix(col, 0xFFFFFF, snapDrop);
            return col;
        }
        return col;
    }

    /** A fully saturated colour of the given hue, 0..1. */
    static int hsv(float h) {
        float x = h * 6f;
        int i = (int) Math.floor(x);
        float f = x - i, q = 1f - f;
        float r, g, b;
        switch (Math.floorMod(i, 6)) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return Math.round(r * 255f) << 16 | Math.round(g * 255f) << 8 | Math.round(b * 255f);
    }
```

Note on the colour fix: in the original loop the drop white was mixed into the group colour only in `AUTO`, including over a chase scene colour never (chase is a different program); the version above matches that, because the scene branch returns before the drop mix.

Replace `aim` with the fan-aware version and keep the old signature:

```java
    /** Where a moving head of group {@code g} points: {pan, tilt} in degrees off its facing. */
    float[] aim(Settings s, int g) {
        return aim(s, g, 0);
    }

    float[] aim(Settings s, int g, int fan) {
        double t = movePhase + g * Math.PI / 4 + fan * Math.PI / 8;
        return switch (s.move) {
            case FIGURE8 -> new float[] {(float) (35 * Math.sin(t)), (float) (20 * Math.sin(2 * t))};
            // All heads side by side, fanning out and back.
            case SWEEP -> new float[] {(float) ((g - 3.5) * 8 * Math.sin(movePhase + fan * Math.PI / 8)), 15f};
            // Snapping to a new spot on every chase step.
            case BALLYHOO -> {
                int k = (step + fan) * 31 + g * 17;
                yield new float[] {(float) (Math.floorMod(k * 7919, 100) - 50), (float) (Math.floorMod(k * 104729, 60) - 20)};
            }
            // Heads converge on the floor in front of them and sway slowly.
            case CROWD -> new float[] {(float) ((g - 3.5) * 3 + 6 * Math.sin(movePhase * 0.5 + fan * Math.PI / 8)), (float) (20 + 8 * Math.sin(movePhase * 0.25))};
            // Straight along the facing: a floor-mounted head points at the ceiling, a hung one at the floor.
            case STRAIGHT -> new float[] {0f, 0f};
            // A fixed spread across the room, a little wider for each fan step.
            case FAN -> new float[] {(float) ((g - 3.5) * 10 + fan * 2), 15f};
            // The left half of the groups mirrors the right.
            case MIRROR -> new float[] {(float) ((g < GROUPS / 2 ? -1 : 1) * 30 * Math.cos(t)), (float) (30 * Math.sin(t))};
            default -> new float[] {(float) (30 * Math.cos(t)), (float) (30 * Math.sin(t))};
        };
    }
```

Replace `pixel(...)` so it is unchanged in behaviour (it already returns a brightness multiplier); leave it as is.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew test`
Expected: PASS: `DmxProgramLightTest` (10 tests) and the unchanged `DmxProgramTest`, `DmxProgramMixerTest`, `DmxProgramGateTest`, `ClubStateTest`.
If `DmxProgramTest` fails, the refactor changed the AUTO/CHASE look: fix `target`/`colorFor`, never the test.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/DmxProgram.java src/test/java/com/createbrewery/block/club/DmxProgramLightTest.java
git commit -m "feat(club): per-fixture levelFor, colour programs, pixel colours and position presets"
```

---

### Task 5: `GoboShape` and `LightBudget` (pure) with tests

**Files:**
- Create: `GoboShape.java`, `LightBudget.java` (in `src/main/java/com/createbrewery/block/club/`)
- Test: `GoboShapeTest.java`, `LightBudgetTest.java`

**Interfaces:**
- Produces:
  - `static List<float[]> GoboShape.shapes(int gobo, float rotation, float radius)`: closed polygons as flat `x0,y0,x1,y1,...` arrays in the spot's plane, centre at the origin offset per polygon (dots), rotated by `rotation` radians
  - `final class LightBudget<K>` with `LightBudget(int max)`, `void offer(K key, double distSq)`, `void endTick()`, `boolean allowed(K key)`

- [ ] **Step 1: Write the failing tests**

```java
package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoboShapeTest {
    private static void assertInside(List<float[]> shapes, float radius) {
        for (float[] poly : shapes) {
            assertTrue(poly.length >= 6 && poly.length % 2 == 0, "a polygon needs at least 3 points");
            for (float v : poly) assertTrue(Float.isFinite(v));
            for (int i = 0; i < poly.length; i += 2) {
                assertTrue(Math.hypot(poly[i], poly[i + 1]) <= radius * 1.001f, "point outside the spot");
            }
        }
    }

    @Test
    void circleStarDotsAndBarHaveTheirShapes() {
        assertEquals(1, GoboShape.shapes(0, 0f, 1f).size());
        assertEquals(24, GoboShape.shapes(0, 0f, 1f).get(0).length, "a 12-gon");
        assertEquals(20, GoboShape.shapes(1, 0f, 1f).get(0).length, "a 10-point star");
        assertEquals(3, GoboShape.shapes(2, 0f, 1f).size(), "three dots");
        assertEquals(8, GoboShape.shapes(3, 0f, 1f).get(0).length, "a bar is a quad");
    }

    @Test
    void everyShapeStaysInsideItsRadiusWhateverTheRotation() {
        for (int gobo = 0; gobo < DmxProgram.GOBOS; gobo++) {
            for (float rot = 0; rot < 6.3f; rot += 0.7f) {
                assertInside(GoboShape.shapes(gobo, rot, 0.5f), 0.5f);
            }
        }
    }

    @Test
    void anUnknownGoboFallsBackToTheCircle() {
        assertEquals(GoboShape.shapes(0, 0f, 1f).get(0).length, GoboShape.shapes(99, 0f, 1f).get(0).length);
        assertEquals(GoboShape.shapes(0, 0f, 1f).get(0).length, GoboShape.shapes(-2, 0f, 1f).get(0).length);
    }

    @Test
    void rotationActuallyTurnsTheBar() {
        float[] a = GoboShape.shapes(3, 0f, 1f).get(0), b = GoboShape.shapes(3, 1f, 1f).get(0);
        assertNotEquals(a[0], b[0], 1e-4);
    }
}
```

```java
package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightBudgetTest {
    @Test
    void keepsTheNearestNOfferedLastTick() {
        LightBudget<String> b = new LightBudget<>(2);
        b.offer("far", 900);
        b.offer("near", 4);
        b.offer("mid", 100);
        assertFalse(b.allowed("near"), "nothing is allowed before the first endTick");
        b.endTick();
        assertTrue(b.allowed("near"));
        assertTrue(b.allowed("mid"));
        assertFalse(b.allowed("far"));
    }

    @Test
    void aKeyThatStopsOfferingLosesItsSlot() {
        LightBudget<String> b = new LightBudget<>(1);
        b.offer("a", 1);
        b.endTick();
        assertTrue(b.allowed("a"));
        b.offer("b", 5);
        b.endTick();
        assertFalse(b.allowed("a"));
        assertTrue(b.allowed("b"));
    }

    @Test
    void ties_goToTheFirstOffered() {
        LightBudget<String> b = new LightBudget<>(1);
        b.offer("x", 10);
        b.offer("y", 10);
        b.endTick();
        assertTrue(b.allowed("x"));
        assertFalse(b.allowed("y"));
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "*GoboShapeTest" --tests "*LightBudgetTest"`
Expected: FAIL (compile errors).

- [ ] **Step 3: Implement**

```java
package com.createbrewery.block.club;

import java.util.ArrayList;
import java.util.List;

/** The outlines of a moving head's gobos, as closed polygons in the plane of the spot. Free of Minecraft classes. */
final class GoboShape {
    private GoboShape() {}

    /**
     * Gobo 0 a circle, 1 a five-pointed star, 2 three dots, 3 a bar; anything else a circle. Each polygon is
     * {@code x0, y0, x1, y1, ...}, turned by {@code rotation} radians, within {@code radius} of the centre.
     */
    static List<float[]> shapes(int gobo, float rotation, float radius) {
        List<float[]> out = new ArrayList<>();
        switch (gobo) {
            case 1 -> {
                float[] p = new float[20];
                for (int i = 0; i < 10; i++) {
                    float r = i % 2 == 0 ? radius : radius * 0.45f;
                    double a = rotation + Math.PI * i / 5.0;
                    p[i * 2] = (float) (Math.cos(a) * r);
                    p[i * 2 + 1] = (float) (Math.sin(a) * r);
                }
                out.add(p);
            }
            case 2 -> {
                for (int d = 0; d < 3; d++) {
                    double a = rotation + Math.PI * 2 * d / 3.0;
                    float cx = (float) (Math.cos(a) * radius * 0.6), cy = (float) (Math.sin(a) * radius * 0.6);
                    float[] p = new float[16];
                    for (int i = 0; i < 8; i++) {
                        double b = Math.PI * 2 * i / 8.0;
                        p[i * 2] = cx + (float) (Math.cos(b) * radius * 0.3);
                        p[i * 2 + 1] = cy + (float) (Math.sin(b) * radius * 0.3);
                    }
                    out.add(p);
                }
            }
            case 3 -> {
                float hx = radius * 0.95f, hy = radius * 0.18f;
                float[] corners = {-hx, -hy, hx, -hy, hx, hy, -hx, hy};
                float c = (float) Math.cos(rotation), s = (float) Math.sin(rotation);
                float[] p = new float[8];
                for (int i = 0; i < 4; i++) {
                    p[i * 2] = corners[i * 2] * c - corners[i * 2 + 1] * s;
                    p[i * 2 + 1] = corners[i * 2] * s + corners[i * 2 + 1] * c;
                }
                out.add(p);
            }
            default -> {
                float[] p = new float[24];
                for (int i = 0; i < 12; i++) {
                    double a = rotation + Math.PI * 2 * i / 12.0;
                    p[i * 2] = (float) (Math.cos(a) * radius);
                    p[i * 2 + 1] = (float) (Math.sin(a) * radius);
                }
                out.add(p);
            }
        }
        return out;
    }
}
```

```java
package com.createbrewery.block.club;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the {@code max} nearest of the keys offered during a tick: those may use a scarce resource
 * (a Veil light) the next tick. Free of Minecraft classes.
 */
final class LightBudget<K> {
    private final int max;
    private final List<Object[]> offers = new ArrayList<>();
    private Set<K> allowed = new HashSet<>();

    LightBudget(int max) {
        this.max = max;
    }

    void offer(K key, double distSq) {
        offers.add(new Object[] {key, distSq});
    }

    /** Call once a tick after every offer: chooses who may use the resource until the next call. */
    @SuppressWarnings("unchecked")
    void endTick() {
        offers.sort((a, b) -> Double.compare((Double) a[1], (Double) b[1]));
        Set<K> next = new HashSet<>();
        for (int i = 0; i < Math.min(max, offers.size()); i++) next.add((K) offers.get(i)[0]);
        allowed = next;
        offers.clear();
    }

    boolean allowed(K key) {
        return allowed.contains(key);
    }
}
```

(`List.sort` is stable, so ties go to the first offered.)

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/GoboShape.java src/main/java/com/createbrewery/block/club/LightBudget.java src/test/java/com/createbrewery/block/club/GoboShapeTest.java src/test/java/com/createbrewery/block/club/LightBudgetTest.java
git commit -m "feat(club): GoboShape outlines and LightBudget for head room lights"
```

---

### Task 6: Fixtures: fan, per-fixture level and colour, optics getters, prism beams

**Files:**
- Modify: `FixtureBlockEntity.java`, `FixtureBlock.java`, `ClubTestAccess.java`, `CreateBrewery.java`, `lang/de_de.json`
- Test: `ClubGameTests.java`

**Interfaces:**
- Consumes: `DmxProgram.levelFor/colorFor/pixelColor/aim(.., fan)/noFlashing()`, `Settings.zoom/gobo/prism`, `FixtureResponse.glide`, `program.movePhase`.
- Produces on `FixtureBlockEntity`:
  - `int getFan()`, `void setFan(int)` (saved and synced)
  - `int zoom()`, `int gobo()`, `boolean prism()`, `float goboRotation()`
  - `int pixelColor(int i)`
  - `int beamCount()` (1, or 3 with prism on a moving head), `Vec3 beamDirection(int b, float partialTick)`, `float beamLength(int b)`, `@Nullable BlockHitResult beamHit(int b)`
  - keeps `direction(Direction, float)`, `beam()`, `hit()` for beam 0 (so existing callers keep working)

- [ ] **Step 1: Write the failing GameTest**

```java
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
```

`ClubTestAccess` additions:

```java
    public static int fixtureFan(Level level, BlockPos pos) {
        return ((FixtureBlockEntity) level.getBlockEntity(pos)).getFan();
    }

    public static void setFixtureFan(Level level, BlockPos pos, int fan) {
        ((FixtureBlockEntity) level.getBlockEntity(pos)).setFan(fan);
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew runGameTestServer`
Expected: compile error (`getFan` missing).

- [ ] **Step 3: Implement `fan` and its persistence**

In `FixtureBlockEntity` add next to `group`:

```java
    private int fan;

    public int getFan() {
        return fan;
    }

    void setFan(int f) {
        fan = Math.floorMod(f, 8);
        sync();
    }
```

In `saveAdditional` add `tag.putInt("Fan", fan);` after the group line; in `loadAdditional` add `fan = Math.floorMod(tag.getInt("Fan"), 8);`.

`FixtureBlock.useWithoutItem`: replace the group-cycling block with:

```java
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                fixture.setFan(fixture.getFan() + 1);
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.6f);
                player.displayClientMessage(Component.translatable("createbrewery.dmx.fixture_fan", fixture.getFan()), true);
                return InteractionResult.sidedSuccess(false);
            }
            fixture.setGroup(fixture.getGroup() + 1);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.2f);
        }
```

keeping the status message and the `return` that follow (the status message now runs only for the group click; the sneak path returns early with its own message). Read the method first and keep its existing structure otherwise. Lang: in `CreateBrewery.java` add `REGISTRATE.addRawLang("createbrewery.dmx.fixture_fan", "Fan position %s");` next to the other `dmx` keys, and in `de_de.json` `"createbrewery.dmx.fixture_fan": "Fächer-Position %s",`.

- [ ] **Step 4: Per-fixture level, colour and glide in `clientTick`**

Replace the lines from Task 2:

```java
        lit = response.dimmer(lampOf(kind), program.level[group]);
        int targetColor = kind == FixtureBlock.Kind.BLINDER ? TUNGSTEN : program.color[group];
```

with:

```java
        float target;
        if (fan == 0) {
            target = program.level[group];
        } else {
            target = program.levelFor(settings, group, fan);
            // The group's own level already glides; a fanned fixture takes the raw target, so it glides here.
            if (program.noFlashing()) target = FixtureResponse.glide(glideLevel, target);
            glideLevel = target;
        }
        lit = response.dimmer(lampOf(kind), target);
        int targetColor = kind == FixtureBlock.Kind.BLINDER ? TUNGSTEN : fan == 0 ? program.color[group] : program.colorFor(settings, group, fan);
```

(add the field `private float glideLevel;`), change the pixel loop to also fill colours:

```java
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = program.pixel(settings, i, pixels.length);
            pixelColors[i] = program.pixelColor(settings, i, pixels.length, group, fan);
        }
```

(add `private final int[] pixelColors = new int[8];`), and change the aim call to `program.aim(settings, group, fan)`.

Store the optics for the renderer after the program is known:

```java
        zoom = settings.zoom;
        gobo = settings.gobo;
        prism = settings.prism && kind == FixtureBlock.Kind.MOVING_HEAD;
        goboRot = program.movePhase * 0.5f;
```

with fields `private int zoom = 1, gobo; private boolean prism; private float goboRot;` and getters `zoom()`, `gobo()`, `prism()`, `goboRotation()`, `pixelColor(int i) { return pixelColors[i]; }`.

The gobo rotation `movePhase * 0.5` turns at most `0.5 * PI / (2 * period)` rad per second (about 0.4 turns a second at 120 BPM): below the one turn a second the spec allows.

- [ ] **Step 5: Prism: up to three beams**

Replace `castBeam(Direction facing)` and the single `beam`/`hit` fields with arrays, keeping `beam()` and `hit()` as beam 0:

```java
    private final float[] beamLen = new float[3];
    private final BlockHitResult[] beamHits = new BlockHitResult[3];
    private int beams = 1;

    private void castBeams(Direction facing) {
        beams = prism ? 3 : 1;
        Vec3 from = lens(facing);
        for (int b = 0; b < beams; b++) {
            Vec3 dir = beamDirection(b, 1f);
            BlockHitResult h = level.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            beamLen[b] = h.getType() == HitResult.Type.MISS ? (float) RANGE : (float) h.getLocation().distanceTo(from);
            beamHits[b] = h.getType() == HitResult.Type.MISS ? null : h;
        }
    }

    int beamCount() {
        return beams;
    }

    /** Beam {@code b}: 0 is the head's own, 1 and 2 the prism's, six degrees either side in pan. */
    Vec3 beamDirection(int b, float partialTick) {
        Direction facing = getBlockState().getValue(FixtureBlock.FACING);
        if (b == 0 || kind() != FixtureBlock.Kind.MOVING_HEAD) return direction(facing, partialTick);
        double[] d = LaserBeams.direction(facing.getStepX(), facing.getStepY(), facing.getStepZ(),
            prevPan + (pan - prevPan) * partialTick + (b == 1 ? 6 : -6), prevTilt + (tilt - prevTilt) * partialTick);
        return new Vec3(d[0], d[1], d[2]);
    }

    float beamLength(int b) {
        return beamLen[b];
    }

    @Nullable
    BlockHitResult beamHit(int b) {
        return beamHits[b];
    }

    float beam() {
        return beamLen[0];
    }

    @Nullable
    BlockHitResult hit() {
        return beamHits[0];
    }
```

and change the call `if (lit > 0.02f) castBeam(facing);` to `if (lit > 0.02f) castBeams(facing);`. Remove the old `beam`, `hit` fields, the old `beam()`/`hit()` methods and `castBeam`; initialise `beamLen[0] = (float) RANGE;` in the field block (the old default `beam = RANGE`) with `{ beamLen[0] = (float) RANGE; }` in an initializer.

- [ ] **Step 6: Run to verify**

Run: `./gradlew test runGameTestServer`
Expected: PASS, including `fixtureFanCyclesWrapsAndSurvivesSaveAndLoad`.

- [ ] **Step 7: Commit**

```bash
git add -A src
git commit -m "feat(club): fixture fan, per-fixture colour and level, prism beams"
```

---

### Task 7: Renderer: soft cones, zoom, gobo spot, prism, pixel colours, light spots

**Files:**
- Modify: `FixtureRenderer.java`
- Test: existing suites (render code); manual check in Task 9

**Interfaces:**
- Consumes: `FixtureBlockEntity.zoom()/gobo()/prism()/goboRotation()/pixelColor(i)/beamCount()/beamDirection/beamLength/beamHit`, `GoboShape.shapes`, `LaserProjectorRenderer.renderImpactDot`, `StrobeLightRenderer.quad/glow`.
- Produces: nothing for later tasks.

- [ ] **Step 1: Soft cones**

Replace the `cone(...)` method body with a nested draw, keeping its signature (so all callers get the soft edge):

```java
    private static void cone(VertexConsumer v, PoseStack pose, Vec3 from, Vec3 dir, float len, float r0, float r1,
                             float r, float g, float b, float a) {
        a = Math.min(1f, a * haze);
        if (len < 0.05f || a < 0.005f) return;
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z));
        Matrix4f m = pose.last().pose();
        int n = 10;
        // Three nested cones: a bright core inside a fading skirt, so the edge of the beam is soft.
        float[] scale = {1f, 0.6f, 0.3f}, share = {0.4f, 0.35f, 0.25f};
        for (int layer = 0; layer < 3; layer++) {
            float s0 = r0 * scale[layer], s1 = r1 * scale[layer], al = a * share[layer];
            for (int i = 0; i < n; i++) {
                float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
                float c0 = Mth.cos(a0), sn0 = Mth.sin(a0), c1 = Mth.cos(a1), sn1 = Mth.sin(a1);
                quad(v, m, c0 * s0, 0, sn0 * s0, c1 * s0, 0, sn1 * s0, c1 * s1, len, sn1 * s1, c0 * s1, len, sn0 * s1, r, g, b, al, 0f);
            }
        }
        pose.popPose();
    }
```

The three shares add up to the old alpha at the beam's centre, so brightness is unchanged and only the edge softens.

- [ ] **Step 2: Moving head: zoom, prism, gobo spot**

Replace the `case MOVING_HEAD ->` block with:

```java
            case MOVING_HEAD -> {
                // Zoom: 0 narrow, 1 normal (as before), 2 wide.
                float zoomK = be.zoom() == 0 ? 0.6f : be.zoom() == 2 ? 1.8f : 1f;
                for (int bm = 0; bm < be.beamCount(); bm++) {
                    Vec3 bdir = be.beamDirection(bm, partialTick);
                    float blen = be.beamLength(bm);
                    // A tight beam with a hot core, and its spot on whatever it hits.
                    cone(v, pose, lens, bdir, blen, 0.07f, 0.07f + blen * 0.035f * zoomK, r, g, b, 0.35f * lv);
                    cone(v, pose, lens, bdir, blen, 0.03f, 0.03f + blen * 0.01f * zoomK, 1f, 1f, 1f, 0.3f * lv);
                    BlockHitResult hit = be.beamHit(bm);
                    if (hit != null) {
                        pose.pushPose();
                        Matrix4f m = pose.last().pose();
                        Vec3 at = hit.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
                        if (be.gobo() == 0) {
                            LaserProjectorRenderer.renderImpactDot(v, m, at, hit.getDirection(), r, g, b, lv * 6f);
                        } else {
                            goboSpot(v, m, at, hit.getDirection(), r, g, b, lv, be.gobo(), be.goboRotation(), 0.18f + 0.1f * zoomK);
                        }
                        pose.popPose();
                    }
                }
                lensGlow(v, pose, lens, 0.25f, r, g, b, lv);
            }
```

Add the spot helpers:

```java
    /** A gobo's shape on the surface that was hit, filled, bright in the middle. */
    private static void goboSpot(VertexConsumer v, Matrix4f m, Vec3 hit, Direction face, float r, float g, float b, float lv,
                                 int gobo, float rotation, float radius) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 u = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 w = n.cross(u);
        Vec3 c = hit.add(n.scale(0.01));
        for (float[] poly : GoboShape.shapes(gobo, rotation, radius)) {
            fan(v, m, c, u, w, poly, 1f, 1f, 1f, 0.7f * lv, 0.2f * lv);
            fan(v, m, c.add(n.scale(0.002)), u, w, poly, r, g, b, 0.9f * lv, 0.4f * lv);
        }
    }

    /** The polygon as a triangle fan around its middle, alpha {@code aMid} there and {@code aEdge} at the rim. */
    private static void fan(VertexConsumer v, Matrix4f m, Vec3 c, Vec3 u, Vec3 w, float[] poly, float r, float g, float b, float aMid, float aEdge) {
        int count = poly.length / 2;
        float mx = 0, my = 0;
        for (int i = 0; i < count; i++) {
            mx += poly[i * 2] / count;
            my += poly[i * 2 + 1] / count;
        }
        Vec3 mid = c.add(u.scale(mx)).add(w.scale(my));
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            Vec3 p0 = c.add(u.scale(poly[i * 2])).add(w.scale(poly[i * 2 + 1]));
            Vec3 p1 = c.add(u.scale(poly[j * 2])).add(w.scale(poly[j * 2 + 1]));
            v.addVertex(m, (float) mid.x, (float) mid.y, (float) mid.z).setColor(r, g, b, aMid);
            v.addVertex(m, (float) mid.x, (float) mid.y, (float) mid.z).setColor(r, g, b, aMid);
            v.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, aEdge);
            v.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r, g, b, aEdge);
        }
    }

    /** A soft round patch of light on the surface a wash hit; {@code alpha} in the middle, none at the rim. */
    private static void softSpot(VertexConsumer v, Matrix4f m, Vec3 hit, Direction face, float r, float g, float b, float alpha, float radius) {
        if (alpha < 0.01f) return;
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 u = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 w = n.cross(u);
        Vec3 c = hit.add(n.scale(0.012));
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            Vec3 p0 = c.add(u.scale(Math.cos(a0) * radius)).add(w.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(u.scale(Math.cos(a1) * radius)).add(w.scale(Math.sin(a1) * radius));
            v.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, b, alpha);
            v.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, b, alpha);
            v.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, 0f);
            v.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r, g, b, 0f);
        }
    }
```

If the project's `GLOW` render type is built with `VertexFormat` including more elements than position and colour, mirror what `StrobeLightRenderer.quad` writes (read it first) instead of the bare `addVertex(...).setColor(...)` pairs above.

- [ ] **Step 3: PAR and LED bar: light spots, pixel colours**

In `case PAR`, after the `lensGlow(...)` call, add:

```java
                BlockHitResult wash = be.hit();
                if (wash != null) {
                    pose.pushPose();
                    Vec3 at = wash.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
                    softSpot(v, pose.last().pose(), at, wash.getDirection(), r, g, b, 0.25f * lv, 0.22f + len * 0.2f);
                    pose.popPose();
                }
```

In `case LED_BAR`, replace the loop body so each pixel uses its own colour:

```java
                for (int i = 0; i < 8; i++) {
                    float p = lv * be.pixel(i);
                    if (p < 0.02f) continue;
                    int pc = be.pixelColor(i);
                    float pr = (pc >> 16 & 255) / 255f, pg = (pc >> 8 & 255) / 255f, pb = (pc & 255) / 255f;
                    Vec3 at = lens.add(side.scale((i - 3.5) / 8.0 * 0.9));
                    cone(v, pose, at, dir, Math.min(len, 8f), 0.05f, 0.05f + Math.min(len, 8f) * 0.08f, pr, pg, pb, 0.18f * p);
                    lensGlow(v, pose, at, 0.12f, pr, pg, pb, p);
                }
                BlockHitResult bar = be.hit();
                if (bar != null) {
                    pose.pushPose();
                    Vec3 at = bar.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
                    softSpot(v, pose.last().pose(), at, bar.getDirection(), r, g, b, 0.2f * lv, 0.6f);
                    pose.popPose();
                }
```

With the static colour program every pixel colour equals the group colour, so the bar looks as before.

- [ ] **Step 4: Compile and run the suites**

Run: `./gradlew test runGameTestServer jar`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/block/club/FixtureRenderer.java
git commit -m "feat(club): soft beams, zoom, gobo spots, prism, pixel colours and light spots"
```

---

### Task 8: Room light for moving heads

**Files:**
- Modify: `StrobeRoomLight.java`, `FixtureBlockEntity.java`
- Test: existing suites (client/Veil code; `LightBudget` already unit-tested in Task 5)

**Interfaces:**
- Consumes: `LightBudget` (Task 5), `FixtureBlockEntity.beamHit(0)`, existing `veil` flag and `roomLight` handle.
- Produces: `StrobeRoomLight.updateAt(Object handle, Vec3 at, float brightness, int color)`.

- [ ] **Step 1: Overload placing a light at a point**

In `StrobeRoomLight` add (next to the other `update`):

```java
    /** A light at {@code at} (a moving head's spot), in any colour; the handle is kept the way {@link #update} keeps it. */
    @SuppressWarnings("unchecked")
    static Object updateAt(Object handle, net.minecraft.world.phys.Vec3 at, float brightness, int color) {
        LightRenderHandle<PointLightData> light = (LightRenderHandle<PointLightData>) handle;
        if (light == null || !light.isValid()) {
            if (brightness <= 0f) return null;
            light = VeilRenderSystem.renderer().getLightRenderer().addLight(new PointLightData());
            light.getLightData().setRadius(12f);
            ALL.add(light);
        }
        light.getLightData().setPosition(at.x, at.y, at.z).setColor(color).setBrightness(brightness * 2.5f);
        light.markDirty();
        return light;
    }
```

- [ ] **Step 2: Budget and use in the moving head**

In `FixtureBlockEntity` add a shared budget and its per-tick maintenance:

```java
    /** Which moving heads may cast a room light: the six nearest to the camera. */
    private static final LightBudget<FixtureBlockEntity> HEAD_LIGHTS = new LightBudget<>(6);
    private static long headLightsTick = Long.MIN_VALUE;
    @Nullable
    private Object headLight;
```

At the end of the Veil block in `clientTick` (the existing `if (veil && kind != MOVING_HEAD)` block), add a sibling block:

```java
        if (veil && kind == FixtureBlock.Kind.MOVING_HEAD) {
            try {
                long now = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
                if (now != headLightsTick) {
                    headLightsTick = now;
                    HEAD_LIGHTS.endTick();
                }
                BlockHitResult spot = beamHit(0);
                boolean lit = this.lit > 0.2f && spot != null;
                if (lit) HEAD_LIGHTS.offer(this, net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(Vec3.atCenterOf(worldPosition)));
                // Never allocated while dark or when over the budget; freed the moment it loses its slot.
                if (lit && HEAD_LIGHTS.allowed(this)) {
                    Vec3 at = spot.getLocation().add(Vec3.atLowerCornerOf(spot.getDirection().getNormal()).scale(0.5));
                    headLight = StrobeRoomLight.updateAt(headLight, at, this.lit, color);
                } else if (headLight != null) {
                    StrobeRoomLight.free(headLight);
                    headLight = null;
                }
            } catch (RuntimeException | LinkageError e) {
                veil = false;
                LOGGER.warn("Veil head light unavailable", e);
            }
        }
```

(`HEAD_LIGHTS.endTick()` runs once per client tick, whichever head ticks first; the first tick after a budget change a head may keep or lose its light one tick late, which is harmless.)

In `setRemoved`, free it too:

```java
        if (headLight != null) {
            StrobeRoomLight.free(headLight);
            headLight = null;
        }
```

- [ ] **Step 3: Verify and commit**

Run: `./gradlew test runGameTestServer jar`
Expected: BUILD SUCCESSFUL.

```bash
git add src/main/java/com/createbrewery/block/club/StrobeRoomLight.java src/main/java/com/createbrewery/block/club/FixtureBlockEntity.java
git commit -m "feat(club): moving heads light the room where their beam lands"
```

---

### Task 9: Console screen: four buttons, eight moves, lang; final check

**Files:**
- Modify: `DmxConsoleScreen.java`, `CreateBrewery.java`, `src/main/resources/assets/createbrewery/lang/de_de.json`, generated lang (via `runData`)
- Test: existing suites; manual check

**Interfaces:**
- Consumes: `DmxControl.COLORFX/GOBO/PRISM/ZOOM` (Task 3), `Settings` fields.
- Produces: the final UI.

- [ ] **Step 1: Widen the screen and add the second column**

In `DmxConsoleScreen`: add `private static final int EXTRA = 124;` next to `W`, and change the first column's width expressions so they no longer depend on `W`:

- replace `private static final int W = 380, H = 200, COL = 30, PANEL = 256;` with `private static final int BASE_W = 380, EXTRA = 124, W = BASE_W + EXTRA, H = 200, COL = 30, PANEL = 256, PW = BASE_W - PANEL - 8;`
- in `init()` replace `int px = left + PANEL, pw = W - PANEL - 8;` with `int px = left + PANEL, pw = PW;`
- in the draw method replace the two expressions that use `W - PANEL - 8` with `PW`.
- the background panel fill (read the draw method) must cover the full `W`; the existing code fills `left .. left + W`, which now includes the new column.

Add the buttons after `record` is created and before `refresh();`:

```java
        int px2 = left + BASE_W + 4, pw2 = EXTRA - 12;
        colorFx = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.COLORFX, s -> s.colorFx)).bounds(px2, top + 18, pw2, 16).build());
        gobo = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.GOBO, s -> s.gobo)).bounds(px2, top + 36, pw2, 16).build());
        prism = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c != null) DmxControl.send(pos, DmxControl.PRISM, 0, c.settings.prism ? 0f : 1f);
        }).bounds(px2, top + 54, pw2, 16).build());
        zoom = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.ZOOM, s -> s.zoom)).bounds(px2, top + 72, pw2, 16).build());
```

Add the fields `private Button colorFx, gobo, prism, zoom;`, change `MOVES` to the eight names and add the new arrays:

```java
    private static final String[] MOVES = {"circle", "figure8", "sweep", "ballyhoo", "crowd", "straight", "fan", "mirror"};
    private static final String[] COLOR_FX = {"static", "fade", "rainbow", "complement"};
    private static final String[] GOBOS = {"circle", "star", "dots", "bar"};
    private static final String[] ZOOMS = {"narrow", "normal", "wide"};
```

and in `refresh()` after the `rate.setMessage(...)` line:

```java
        colorFx.setMessage(Component.translatable("createbrewery.dmx.colorfx." + COLOR_FX[s.colorFx]));
        gobo.setMessage(Component.translatable("createbrewery.dmx.gobo." + GOBOS[s.gobo]));
        prism.setMessage(Component.translatable("createbrewery.dmx.prism", CommonComponents.optionStatus(s.prism)));
        zoom.setMessage(Component.translatable("createbrewery.dmx.zoom." + ZOOMS[s.zoom]));
```

- [ ] **Step 2: Lang**

In `CreateBrewery.java`, next to `REGISTRATE.addRawLang("createbrewery.dmx.move.circle", ...)`:

```java
        REGISTRATE.addRawLang("createbrewery.dmx.move.crowd", "Heads: Crowd");
        REGISTRATE.addRawLang("createbrewery.dmx.move.straight", "Heads: Straight");
        REGISTRATE.addRawLang("createbrewery.dmx.move.fan", "Heads: Fan out");
        REGISTRATE.addRawLang("createbrewery.dmx.move.mirror", "Heads: Mirror");
        REGISTRATE.addRawLang("createbrewery.dmx.colorfx.static", "Colour: Static");
        REGISTRATE.addRawLang("createbrewery.dmx.colorfx.fade", "Colour: Fade");
        REGISTRATE.addRawLang("createbrewery.dmx.colorfx.rainbow", "Colour: Rainbow");
        REGISTRATE.addRawLang("createbrewery.dmx.colorfx.complement", "Colour: Complement");
        REGISTRATE.addRawLang("createbrewery.dmx.gobo.circle", "Gobo: Circle");
        REGISTRATE.addRawLang("createbrewery.dmx.gobo.star", "Gobo: Star");
        REGISTRATE.addRawLang("createbrewery.dmx.gobo.dots", "Gobo: Dots");
        REGISTRATE.addRawLang("createbrewery.dmx.gobo.bar", "Gobo: Bar");
        REGISTRATE.addRawLang("createbrewery.dmx.prism", "Prism: %s");
        REGISTRATE.addRawLang("createbrewery.dmx.zoom.narrow", "Zoom: Narrow");
        REGISTRATE.addRawLang("createbrewery.dmx.zoom.normal", "Zoom: Normal");
        REGISTRATE.addRawLang("createbrewery.dmx.zoom.wide", "Zoom: Wide");
```

In `de_de.json` after `createbrewery.dmx.move.ballyhoo`:

```json
  "createbrewery.dmx.move.crowd": "Köpfe: Publikum",
  "createbrewery.dmx.move.straight": "Köpfe: Geradeaus",
  "createbrewery.dmx.move.fan": "Köpfe: Auffächern",
  "createbrewery.dmx.move.mirror": "Köpfe: Spiegel",
  "createbrewery.dmx.colorfx.static": "Farbe: Statisch",
  "createbrewery.dmx.colorfx.fade": "Farbe: Verlauf",
  "createbrewery.dmx.colorfx.rainbow": "Farbe: Regenbogen",
  "createbrewery.dmx.colorfx.complement": "Farbe: Komplementär",
  "createbrewery.dmx.gobo.circle": "Gobo: Kreis",
  "createbrewery.dmx.gobo.star": "Gobo: Stern",
  "createbrewery.dmx.gobo.dots": "Gobo: Punkte",
  "createbrewery.dmx.gobo.bar": "Gobo: Balken",
  "createbrewery.dmx.prism": "Prisma: %s",
  "createbrewery.dmx.zoom.narrow": "Zoom: Eng",
  "createbrewery.dmx.zoom.normal": "Zoom: Normal",
  "createbrewery.dmx.zoom.wide": "Zoom: Weit",
```

Run `./gradlew runData` to regenerate `src/generated/resources/assets/createbrewery/lang/en_us.json` and `en_ud.json`.

- [ ] **Step 3: Full verification**

Run: `./gradlew test runGameTestServer jar runData`
Expected: BUILD SUCCESSFUL; unchanged tests `DmxProgramTest`, `DmxProgramMixerTest`, `DmxProgramGateTest`, `ClubStateTest` still pass (`git diff 2327970 --stat -- src/test` must show no edits to those four files).
Run: `rg -n "W - PANEL - 8" src/main/java/com/createbrewery/block/club/DmxConsoleScreen.java` — expected: no matches.

- [ ] **Step 4: Commit**

```bash
git add -A src
git commit -m "feat(club): console buttons for colour programs, gobo, prism, zoom and the new head moves"
```

- [ ] **Step 5: Manual check in game (cannot be automated)**

With `javaw` closed: copy `build/libs/createbrewery-0.1.2.jar` into the profile's `mods/`, start the game, build a booth, console, and one each of PAR, LED bar, moving head, blinder linked to the console, then:
1. A blinder flash glows on for a third of a second after the level drops; the LED bar follows instantly.
2. A moving head swings visibly slower on a long move than on a short one; with "Heads: Ballyhoo" it does not teleport.
3. With "Colour: Rainbow" an LED bar shows a rainbow along its eight pixels; with fan values 0..7 on several fixtures of one group (sneak-click each) a chase travels across them.
4. Gobo star / dots / bar show on the wall; prism gives three beams; zoom narrow/wide changes the beam width.
5. PAR and LED bar leave a soft light spot on the wall; with Veil (no Iris pack) a moving head lights the room where it lands, and at most six at once.
6. With an Iris pack and with "no flashing" on: nothing throws, nothing strobes.
7. Quit and reload: fan, colour program, gobo, prism, zoom and moves are kept.
Then check `logs/latest.log` for `createbrewery` errors.
