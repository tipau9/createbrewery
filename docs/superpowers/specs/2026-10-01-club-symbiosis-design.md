# Club Symbiosis: one shared state for light, effects and sound

Sub-project 1 of 4 for the club overhaul. The others (each gets its own spec after this one ships):
2. Lights: realism of fixtures and console programs.
3. Sound: PA realism (subwoofer, crossover, limiter, room), mixer features.
4. Console and DJ booth: features and workflow.

## Goal

The club behaves as one rig. What the DJ does and what the music does reaches every light and effect in the same tick, through one source, instead of each block guessing on its own.

Success looks like this (the user's words: "alles miteinander funktionieren, der Club in einer Symbiose"):
- A drop makes the lights, strobe, lasers, fog, CO2 and cold sparks react together, on the same tick.
- A bass kill or filter sweep by the DJ is visible: the room goes dark and cold during a build-up, and hits when the bass comes back.
- Every effect can be put under the DMX console: blackout, master and group faders also dim strobes, lasers and fog.
- No effect is wired to the console by force: unlinked effects keep today's behaviour, so existing worlds do not change.

## What is wrong today (read from the code)

- Only `Fixture` blocks link to a `DmxConsole` (`FixtureBlockEntity.console`). Strobe, laser, fog machine, CO2 jet and cold spark do not know it.
- Each of them derives beats and drops itself from `MusicPulse.kickNear/dropNear`, with its own thresholds and latch: `StrobeLightBlockEntity`, `LaserProjectorBlockEntity`, `ColdSparkBlockEntity`, `FogMachineBlockEntity`, `DmxProgram`. The same kick latches (0.38 / 0.20) are copied four times. A late chunk load or a different tick order puts them out of step.
- The mixer state (filter, EQ, FX, crossfader, loop) changes the sound only. Nothing in the light rig sees it.
- `DmxConsoleBlockEntity` and `FixtureBlockEntity` each call `Minecraft.getInstance().options` for the no-flashing setting.
- The console's booth link is set only when REC is pressed (`setRecording` scans for the nearest booth). Without REC it follows no booth.

## Design

### 1. `ClubState` (new, no Minecraft classes, unit-testable)

One instance per DJ booth, advanced once per client tick. The only place that turns raw audio signals and mixer settings into show events.

Inputs per tick:
- from `MusicPulse` (for the booth's speakers): `kick`, `drop`, `tension`, `period`, `playing`.
- from the `DjBoothBlockEntity` (already synced to clients): the loudest-mixed deck's `filter`, low EQ, crossfader position, loop active.
- `noFlashing` (the player's setting), passed in by the client adapter.

Outputs (read by anyone):
- `beat`: true for exactly one tick on a kick, plus `beatIndex`, `beatPhase` (0..1 through the beat), `env` (punch envelope).
- `drop` edge (true for one tick) and `dropLevel` (0..1, decaying).
- `buildUp` 0..1 (from `tension`, raised while the filter is swept closed or the bass is killed).
- `breakdown`: music playing but no kick for a few beats (same rule as `DmxProgram` today).
- `bassCut` 0..1: low EQ down or high-pass swept up. `filterClosed` 0..1: low-pass swept down.
- `energy` 0..1: smoothed overall level, the "how hard is the club going" number.
- `strobeGate` from `noFlashing`: one answer to "may this flash".

Beat detection keeps the current hysteresis (on above 0.38, off below 0.20) but lives in this one class. `DmxProgram` stops detecting beats and reads `beat`/`drop`/`breakdown` from the state.

### 2. Resolving the booth: `ClubStates.at(level, pos)`

Client-side registry, cache per game tick, in this order:
1. The linked console's booth (see 3), if the block is linked.
2. The nearest loaded booth that is playing within the club reach (32 blocks, the value `MusicPulse` already uses).
3. None: the effect keeps its current `MusicPulse.*Near` behaviour unchanged. This is the compatibility path for existing worlds and for effects far from any booth.

### 3. Console to booth link

The console remembers its booth when it is placed or first used (nearest booth within 16 blocks; today's `nearestBooth()` scan, rate limited, already exists), not only on REC. Pressing REC keeps working as before. The link is saved with the console, as today (`Booth` tag), so no new data format.

### 4. Effects under the console (opt-in)

Strobe, laser projector, fog machine, CO2 jet and cold spark get an optional console link and a group (1..8), saved on the block entity, using the same linker item and `SpeakerBlock.link` mechanism that fixtures use today (tag `LinkedConsole`). Linked effects:
- are dark under blackout, scaled by master and by their group's level (`DmxProgram.level[group]`);
- take beat, drop and build-up from the console's booth state.

Unlinked effects work as today, and take beats and drops from `ClubStates` when a booth is in reach (point 2).

### 5. What reacts to what (rules, so the result is predictable)

| Event | Fixtures | Strobe | Laser | Fog | CO2 / cold spark |
|---|---|---|---|---|---|
| `beat` | auto program as today | flash (BEAT mode) | new pattern step | none | none |
| `buildUp` rising | dim and shift cold, strobe faster (existing tension curve) | rate follows build-up | speed rises | steady | none |
| `bassCut` / `filterClosed` | level drops with the cut (room goes dark) | holds | holds | holds | holds |
| `drop` edge | full flash and white (as today) | one full frame | full burst | thicker blast | one burst, same tick |
| `breakdown` | almost dark (as today) | off | slow, ambient | steady | none |

The numbers (thresholds, rates) are the ones that already exist in the code. This spec moves them to one place and adds the `bassCut` / `filterClosed` coupling; it does not retune the look (that is sub-project 2).

### 6. Photosensitivity and server safety

- `noFlashing` is read once, in the client adapter that feeds `ClubState`. The `Minecraft.getInstance()` calls leave `DmxConsoleBlockEntity.output()` and `FixtureBlockEntity.clientTick()`. This also closes audit item 22 for these two classes.
- `ClubState` and `ClubStates` are client-only. No new packets. Server code does not reference them.

## Non-goals

- No retuning of looks, no new fixtures, no new GUI (sub-projects 2 and 4).
- No change to the audio path, speakers, subwoofer, amp rack or mixer sound (sub-project 3). Only the mixer's settings are read.
- No DMX-channel model (the larger rework that would break existing worlds).
- No change to `DropDetector` or `MusicPulse`.

## Testing

Unit tests (JUnit, no Minecraft), new `ClubStateTest`:
- beat latch: one `beat` per kick, hysteresis, no double fire across a late tick;
- drop edge fires once and `dropLevel` decays;
- breakdown starts after the same no-kick time as today and ends on the next kick;
- bass kill and filter sweep raise `bassCut` / `filterClosed`; build-up rises with `tension`;
- `strobeGate` follows `noFlashing`.

`DmxProgramTest` (existing) must stay green unchanged after `DmxProgram` reads `ClubState`: that is the proof the AUTO look is preserved.

GameTests (new, in `ClubGameTests`):
- an effect linked to a console is dark under blackout and follows its group fader;
- an unlinked effect near no booth behaves as today (no console reference);
- console link survives save and load, and is set without pressing REC;
- link to a console in another dimension or too far is refused, as for fixtures.

Manual (cannot be automated): in game, with a booth playing a track, trigger a drop and check that lights, strobe, laser, fog, CO2 and cold spark answer on the same beat; sweep the filter and see the room react; switch "no flashing" on and check that nothing strobes.

## Rollout

Behind unchanged defaults: unlinked blocks behave as before, so nothing breaks in saved worlds. Order of work, each step with green `gradlew test runGameTestServer` before the next:
1. `ClubState` + `ClubStates` + tests (no behaviour change yet).
2. `DmxProgram` reads `ClubState`; `DmxProgramTest` unchanged.
3. Strobe, laser, cold spark, fog, CO2 read `ClubState`.
4. Console link and group for effects; blackout, master and group scaling.
5. Mixer coupling (`bassCut`, `filterClosed`).

## Open points (defaults chosen, change on request)

- Link range for effects: the same `MAX_LINK` as fixtures.
- Group for an unlinked-then-linked effect: group 1.
