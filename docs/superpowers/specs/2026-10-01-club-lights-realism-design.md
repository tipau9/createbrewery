# Club Lights: realism of fixtures, beams and console programs

Sub-project 2 of 4 of the club overhaul (1: symbiosis, shipped; 3: sound; 4: console and DJ booth features).
It builds on `ClubState` / `ClubStates` / `DmxProgram` from sub-project 1.

## Goal

Club lights behave and look like their real counterparts, and the console can program them like a real desk. Three stages, each shippable and tested on its own:
1. Fixture physics: how a light responds (dimmer curve, lamp inertia, motor speed, colour fades).
2. Optics and beams: what the light looks like (soft beams, zoom, gobos, prism, pixel colours, light spots, room light).
3. Console programs: what the desk can ask of the lights (fan, colour programs, positions, controls, recorded shows).

Not in this sub-project: new fixture types (wash, beam, pixel panel, follow spot), strobe / laser / fog changes, audio, a DMX channel model, a redesign of the console screen beyond new buttons.

## What is true today (from the code)

- `FixtureBlockEntity.clientTick` sets `lit = program.level[group]` directly: linear, instant, the same for every fixture type. The blinder's tungsten lamps have no warm-up or afterglow.
- A moving head's pan and tilt move by `(target - current) * 0.3` per tick: no speed limit, so a 100-degree jump takes about the same few ticks as a 5-degree one.
- Colour comes from `program.color[group]` and snaps. An LED bar has eight pixels with one colour; only the brightness (`DmxProgram.pixel`) varies.
- `FixtureRenderer.cone` draws one hard-edged cone per beam (10 quads, alpha fading along its length). The moving head's impact spot is a round dot (`LaserProjectorRenderer.renderImpactDot`). Only the moving head draws a spot at the hit; PAR and LED bar do not.
- Room light (`StrobeRoomLight`, through Veil) exists for PAR, LED bar and blinder, not for moving heads, and is off with Iris shaderpacks.
- The console has three programs (manual, auto, chase), four moves (circle, figure 8, sweep, ballyhoo), eight palette colours per group and no per-fixture offset.

## Stage 1: Fixture physics

New pure class `FixtureResponse` (no Minecraft classes), one instance per fixture, stepped once per client tick.

- **Dimmer curve.** The output intensity is `level ^ DIMMER_EXPONENT` with `DIMMER_EXPONENT = 1.6f` (between linear and the square law of real dimmers). 0 and 1 map to themselves; the curve is monotonic. Low levels look darker, as on a real desk.
- **Lamp response per type** (time constants in ticks, 20 per second):

| Type | Rise | Fall |
|---|---|---|
| LED bar, PAR | 1 | 2 |
| Moving head | 2 | 3 |
| Blinder (tungsten) | 2 (about 80 ms) | 7 (about 350 ms, the filament cools) |

  Exponential approach: each tick the output moves toward the target by `1 - exp(-1 / tau)`.
- **Moving head motors.** Pan up to 9 degrees per tick (180 deg/s), tilt up to 6 per tick (120 deg/s), with an acceleration limit of 1.5 degrees per tick squared, so a head eases in and out. A 100-degree jump takes about 11 ticks, a 5-degree one about 3. This replaces the `* 0.3` smoothing.
- **Colour fades.** Colour moves toward the target in RGB: moving head about 5 ticks, LED bar and PAR about 2 ticks, blinder is fixed tungsten (no change).
- **Photosensitivity.** Smoothing only slows changes; it never adds flashes. With "no flashing" the existing `DmxProgram` glide still applies on top.

## Stage 2: Optics and beams

Renderer and client-tick work; `FixtureRenderer`, `FixtureBlockEntity`, `LaserProjectorRenderer.renderImpactDot`, `StrobeRoomLight`.

- **Soft beams.** Each cone is drawn as three nested cones (radius factors 1, 0.6, 0.3; alpha split 0.4 / 0.35 / 0.25 of the old alpha), so the edge fades out instead of ending hard. Haze scaling stays as it is.
- **Moving head optics** (settings come from the console, see Stage 3; Stage 2 adds the fields and the rendering, so the defaults must look as today):
  - `zoom` 0..2 (narrow, normal, wide): beam end radius factors 0.6 / 1.0 / 1.8.
  - `gobo` 0..3 (circle, star, three dots, bar): the shape of the spot at the hit, rotating with the program's move clock, drawn as polygons (no textures).
  - `prism`: three beams, one straight and two at plus/minus 6 degrees off pan. Each beam does its own raycast; cost is capped at 3 raycasts per head per tick.
- **LED bar pixel colours.** `DmxProgram` gets `int pixelColor(Settings s, int i, int pixels, int group, int fan)`; with the static colour program it equals the group colour, so today's look is unchanged.
- **Light spots.** PAR and LED bar draw a soft round spot where the beam hits a block (radius follows the beam spread, alpha about 0.25 times the level), the way a wash lights a wall.
- **Room light for moving heads.** `StrobeRoomLight.update` gets an overload taking an explicit position; a moving head lights the room at the point where its beam hits, in the beam colour. At most 6 such lights exist per client, chosen by distance to the camera. Off when Veil is absent or an Iris shaderpack is active (same condition as for the strobe); never allocated while a head is dark.

## Stage 3: Console programs

Console (`DmxProgram`, `DmxConsoleBlockEntity`, `DmxControl`, `DmxConsoleScreen`, `DmxTimecode`) and fixtures.

- **Fan.** A fixture has a `fan` value 0..7 (saved, synced), set by right-clicking it with an empty hand while sneaking (it cycles; the status message shows it). `DmxProgram.levelFor(Settings s, int group, int fan)` shifts the chase and auto-punch step by `fan`, so patterns travel across the fixtures of a group; a moving head's move phase is offset by `fan * PI / 8`. The group's own level (`program.level[g]`) is the same as `levelFor(s, g, 0)`.
- **Colour programs** (`colorFx`, console setting): 0 static (today), 1 fade (cross-fades through the palette, one colour every 8 beats), 2 rainbow (hue = time, group and fan offset), 3 complement (alternates the group colour with its opposite on each step). Pure functions in `DmxProgram`: `int colorFor(Settings s, int group, int fan)`.
- **Position presets.** `MOVES` grows from 4 to 8: after circle, figure 8, sweep and ballyhoo come crowd (all heads aim at the dance floor, tilt down), ceiling (mirror-ball style, straight up), fan (spread by group across the room) and mirror (left half of the groups mirror the right). The old values 0..3 keep their meaning, so saved consoles load unchanged.
- **Optics controls.** `gobo` (cycle 0..3), `prism` (toggle), `zoom` (cycle 0..2) as console settings.
- **Controls and protocol.** New `DmxControl` actions `COLORFX = 13, GOBO = 14, PRISM = 15, ZOOM = 16`; the registrar version goes from "2" to "3" (the channel is `optional()`). The server handler keeps clamping every value; an unknown action stays a no-op. New buttons in `DmxConsoleScreen` (colour program, gobo, prism, zoom) next to the existing program / move / rate buttons.
- **Recorded shows.** `DmxTimecode.Look` gains `colorFx`, `gobo`, `prism`, `zoom`, so cue playback restores them. NBT keys that are missing load as defaults.

## Constraints

- Unlinked fixtures and consoles from older saves load with defaults that look like today (colour program static, gobo circle, prism off, zoom normal, fan 0).
- Pure logic (`FixtureResponse`, `DmxProgram.levelFor`, `colorFor`, `pixelColor`, gobo polygon generator) has no Minecraft imports and is unit-tested. Client-only rendering stays behind `isClientSide` paths; no new packets except the new `DmxControl` actions.
- `DmxProgramTest` stays green unchanged after each stage.
- Cost budget per fixture per tick: at most 3 raycasts (prism), at most 3 nested cones of 10 quads per beam; Veil lights capped at 6 per client.
- "No flashing" stays honoured: pulsing zoom is not used, gobo rotation is slow (less than one turn per second), nothing here adds a flash.

## Testing

Unit tests (JUnit): `FixtureResponseTest` (curve endpoints and monotonic, tungsten fall takes longer than rise, LED nearly instant, a held target converges, motor never exceeds speed or acceleration and a long move takes longer than a short one, colour fade converges); `DmxProgramLightTest` (`levelFor` with fan 0 equals the group level, fan shifts the chase, colour programs differ by group and fan, `pixelColor` static equals group colour, new moves return finite angles for every group, old moves 0..3 unchanged); a gobo polygon test (point count, closed, scale); `DmxTimecodeTest` extended for the new `Look` fields and missing keys.

GameTests (`ClubGameTests`): console settings round trip through NBT with the new fields and an old-style tag loads defaults; `DmxControl` handler clamps the new actions; fan cycles by the sneak click and survives save and load.

Manual (cannot be automated): a booth with PAR, LED bar, moving head and blinder; blinder glows after a flash; a head swings visibly slower on a long move; rainbow and fan travel across a bar; gobo shapes and prism; spots on walls; room light from a moving head with Veil; with an Iris pack nothing breaks; "no flashing" on.

## Rollout

Each stage ends with green `./gradlew test runGameTestServer`:
1. `FixtureResponse` and its use in `FixtureBlockEntity` (physics).
2. Optics: soft cones, settings fields with defaults, zoom / gobo / prism rendering, pixel colours, spots, moving-head room light.
3. Console: fan, colour programs, presets, controls, timecode.

## Open points (defaults chosen, change on request)

- Gobo spots are drawn as polygons, not as shader textures (robust with Iris, a little angular).
- Dimmer exponent 1.6, motor limits 180 / 120 deg/s: tuning values, kept as named constants.
