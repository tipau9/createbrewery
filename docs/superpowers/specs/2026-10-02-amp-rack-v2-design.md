# Amp Rack v2: a real PA amp rack that anyone can drive

Replaces the amp rack as it stands at `bd56b48` (tipau9's "19-inch touring amplifier"). Its settings are migrated, its code is replaced. Branch `amp-rack-v2`, worktree `C:/Users/Anwender/dev/createbrewery-amp`.

## Goal

The user's words: "komplett neu, komplett funktionsfähig und mit allen Funktionen eines echten AMP Racks ... in perfekter Symbiose mit dem Club ... Bedienung so einfach wie möglich für Leute, die noch nie mit so etwas gearbeitet haben".

Success looks like this:
- Someone with no PA knowledge places a rack, links it to the booth, presses **Auto Setup**, and the club sounds right: subs carry the lows, far speakers land in step, side rooms are quieter, the DJ monitor is not deafening.
- A traffic light says in plain words whether everything is fine and, if not, what to do.
- Everything a real amp/DSP rack offers is there for those who want it: power, master, zones with gain/mute/EQ/high-pass/delay/polarity/limiter, crossover with slope, speaker alignment, meters, presets.
- The rest of the club follows the rack: power off silences the booth's speakers and subs and stops the lights reacting to the music; the DJ mixer shows the rack's status; the block itself shows LEDs.

Chosen by the user: Auto Setup + traffic light, zones (automatic, changeable per speaker), power/master/meters/presets, expert DSP; symbiosis via power, booth status, live block LEDs. **No redstone control.**

## What is there today (bd56b48, read from the code)

- `AmpRackBlockEntity` extends `SpeakerBlockEntity` (linked to a booth like a speaker). Fields: crossover 60..200 Hz, sub/top gain knobs (0..1, `DeckFx.eqGain`), propagation, muteTops, muteSubs, bassContour (flat/deep/punch, multiplies sub drive by 1.35/1.25), subCut (off/30/40 Hz), delayMs 0..50.
- `AmpControl` packet `(pos, action byte, value float)`, registrar version "2".
- `MusicPulse.play` finds the first linked rack, splits tops (HIGH) and active subs (LOW) at the crossover, sets `Emitter.drive`, `delay`, `subCut`; the booth's own emitter plays at the fixed `MONITOR = 0.2`.
- `Emitter.feed` chain: deck FX, `DeckFx.Crossover` (LR24), sub high-pass, drive, `DeckFx.Limiter` (ceiling 0.97), wall filter.
- `AmpRackScreen` (bd56b48): LCD style screen with VU meters reading `MusicPulse.hats/level/bass/high` (global values, not the rack's).
- Lang: en_us via `REGISTRATE.addRawLang` in `CreateBrewery.java`; German in the hand-kept `src/main/resources/assets/createbrewery/lang/de_de.json`. The game runs `lang:en_us`.

## Design

### Zones

Five fixed zones. Every linked speaker belongs to exactly one; the booth itself is always MONITOR.

| Zone | Holds | Auto rule |
|---|---|---|
| `FLOOR` (dance floor) | tops | clear line to the booth, not far back |
| `SUBS` | subwoofers | every subwoofer |
| `DELAY` (delay / back fill) | tops | clear line, distance to booth > nearest top's distance + 12 blocks |
| `ROOM` (side room: bar, toilets) | tops | at least one solid block between booth and speaker (same wall test as `Emitter.wallsBetween`, speaker blocks not counted) |
| `MONITOR` (DJ monitor) | the booth's own emitter | always |

A user can move any top to FLOOR/DELAY/ROOM in the Zones tab; that assignment is marked manual and the background auto-assign never touches it. **Auto Setup re-detects everything** (it is the "make it right" button), manual marks included. Subs always stay in SUBS.

### 1. `AmpSettings` (new, pure, no Minecraft classes except `BlockPos`/`CompoundTag`)

All rack state. Clamps everything it is given; never NaN.

- `power` (bool, default on), `master` dB −40..+6 (default 0), `preset` (CLUB, LIVE, BACKGROUND, NIGHT, CUSTOM; default CLUB).
- `crossover` Hz 60..200 (default 100), `slope` LR24 | LR48 (default LR24), `align` bool (speed of sound + delay towers, default off; Auto Setup turns it on when DELAY or ROOM has speakers).
- `Zone[5]`, each: `gain` dB −30..+6 (−30 shown as "off"), `mute`, `low`/`mid`/`high` EQ dB −12..+12 (shelves at 120 Hz / 8 kHz, bell at 1 kHz Q 0.7), `hpf` Hz 0 (off) or 20..200, `delayMs` 0..100, `invert` (polarity), `limit` dBFS −20..0 (0 = today's ceiling 0.97).
- `assign`: `Map<BlockPos, Assignment(zone, manual)>` for tops.
- Any setter except power/assign/auto setup sets `preset = CUSTOM`. `applyPreset(p)` writes master, zone gains, EQs and sub high-pass only (never assignments, delays, crossover):

| Preset | master | FLOOR | SUBS | DELAY | ROOM | MONITOR | EQ / HPF |
|---|---|---|---|---|---|---|---|
| CLUB | 0 | 0 | +3 | 0 | −6 | −10 | flat, SUBS hpf 30 |
| LIVE | 0 | 0 | 0 | 0 | −6 | −6 | FLOOR mid +2, SUBS hpf 35 |
| BACKGROUND | −12 | 0 | −6 | −3 | −3 | −10 | all high −2, SUBS hpf 40 |
| NIGHT | −20 | 0 | −12 | −6 | −6 | −12 | SUBS hpf 40 |

- NBT: one compound `Amp` with a `V: 2` int. `load` without `Amp` migrates the old flat tags: `Crossover` → crossover; `SubGain`/`TopGain` knobs → dB via `20·log10(eqGain(knob))` into SUBS and FLOOR/DELAY/ROOM gains (clamped; knob 0 → −30); `Propagation` → align; `MuteTops` → mute FLOOR/DELAY/ROOM; `MuteSubs` → mute SUBS; `SubCut` 1/2 → SUBS hpf 30/40; `BassContour` deep/punch → SUBS gain +2.6/+1.9 dB (20·log10 of 1.35/1.25); `DelayMs` → every zone's delayMs. Preset after migration: CUSTOM.

### 2. `AutoSetup` (new, pure)

`plan(boothPos, List<Seen(pos, isSub, walled)>) → Result(assignments, crossover, align, summary counts)`.
- Classifies by the zone table above. "Nearest top" is over non-walled tops only; with no non-walled tops there is no DELAY.
- Crossover 100 Hz, 80 Hz with exactly one sub.
- `align` on when any DELAY or ROOM speaker exists.
- Applies preset CLUB afterwards, resets every zone's EQ/delay/polarity/limit to neutral, sets `power` on.
- The walled test runs on the server (`BlockEntity` adapter does the clip rays, `AutoSetup` only gets booleans).

### 3. `AmpRackBlockEntity` (rewritten)

- Holds one `AmpSettings`, synced to clients by the existing block update.
- Server tick every 40 ticks: speakers newly linked to its booth and not yet in `assign` get the auto zone (same classifier, one speaker at a time, not touching others); positions no longer linked are dropped. Needs a ticker on `AmpRackBlock`.
- `autoSetup()` on the server: runs `AutoSetup` over all linked speakers, stores it, sends the player a chat line: "Set up 5 speakers, 2 subwoofers, 1 side room".
- Only the first rack (lowest `BlockPos`, as today) drives a booth; a second rack's screen says "Another rack drives this booth (x y z)" and is read-only.

### 4. `AmpControl` (rewritten)

`(BlockPos rack, byte action, byte zone, float value, BlockPos target)`, registrar version "3". Actions: POWER, MASTER, PRESET, AUTO_SETUP, CROSSOVER, SLOPE, ALIGN, ZONE_GAIN, ZONE_MUTE, ZONE_LOW, ZONE_MID, ZONE_HIGH, ZONE_HPF, ZONE_DELAY, ZONE_INVERT, ZONE_LIMIT, ASSIGN (target = the speaker, zone = new zone), RESET (reset one control to default). Server keeps today's guards: player can interact with the rack, value finite, zone in range; ASSIGN only for a top linked to the same booth. The screen applies the move locally at once, as today.

### 5. DSP (`DeckFx`, new pure classes)

- `Crossover` gains a slope: LR24 = two Butterworth biquads (today), LR48 = four biquads with Q 0.5412, 1.3066, 0.5412, 1.3066.
- `ZoneDsp`: high-pass (12 dB/oct Butterworth, skipped when off), low shelf / mid bell / high shelf (RBJ), polarity, gain. Retunes only on change. At neutral settings it is bit-exact pass-through.
- `Limiter` takes a threshold (dBFS → linear, max 0.97) and reports input peak as well as reduction, for the meters.

### 6. Audio path (`MusicPulse.play`, `Emitter`)

Per frame, for a booth with a powered-or-ramping rack:
- Each emitter gets its zone's `ZoneDsp` settings, the crossover side and slope (tops HIGH and subs LOW only while at least one active sub exists; otherwise tops play FULL, as today), the limiter threshold, and `drive = power ramp · master · zone gain · (mute ? 0 : 1)`.
- `delay` = speed-of-sound alignment (when `align`) + zone `delayMs`, through the existing `delay` mechanism.
- The booth's own emitter uses MONITOR (replaces the `MONITOR` constant when a rack drives the booth).
- Chain in `Emitter.feed`: deck FX → crossover → `ZoneDsp` → drive → limiter → wall filter.
- **Power ramp** (client, per booth): towards 1 over 2 s on power on, towards 0 over 0.5 s on power off. At 0 the booth's speaker and sub emitters are removed; the booth's own emitter plays at the old 0.2 so the DJ still hears the deck. `Track.pulse`, and with it `kickNear/dropNear/tensionNear/playingNear`, is multiplied by the ramp, so lights, strobes, lasers, sub cones and camera shake stop following a switched-off rig.
- **Meters** (client, per booth, per zone): peak in dBFS after the limiter and limiter reduction in dB, each held 1 s and falling at 20 dB/s. Exposed as `MusicPulse.meters(booth)`.
- A booth without a rack behaves exactly as today.

Removed from bd56b48: `bassContour`, `subCut`, the global `delayMs`, mutes per side (all now per zone), `MusicPulse.hats()/level()/bass()/high()` if nothing else uses them.

### 7. `AmpStatus` (new, pure): the traffic light

`of(inputs) → (Light, message key, args)`; the worst rule wins:

| Light | Condition | Message (en) |
|---|---|---|
| RED | rack not linked | "Not linked: right-click the DJ booth with the rack in hand" |
| RED | another rack drives the booth | "Another rack drives this booth" |
| RED | power off | "Power is off: press POWER" |
| RED | limiter > 6 dB in any zone | "Too loud! Turn MASTER down" |
| YELLOW | limiter 1..6 dB | "At the limit: a little quieter" |
| YELLOW | no speakers linked | "No speakers linked: right-click the booth with speakers in hand" |
| YELLOW | no active subwoofer | "No subwoofers on: bass comes from the speakers" |
| YELLOW | a zone with speakers is muted | "Zone <name> is muted" |
| GREEN | otherwise | "All good" |

### 8. `AmpRackScreen` (rewritten)

Three tabs, opens on **Simple**. Dark theme, sized for GUI scale 2 at 1080p (~300×200).
- **Simple**: big POWER button (lit when on), traffic light + message, MASTER slider (dB), **Auto Setup** button, four preset buttons (active one lit, "Custom" label when CUSTOM), one row per zone: name, gain slider, M (mute) toggle, meter bar (green / yellow above −6 dBFS / red when limiting). Zones without speakers are greyed with "(none)".
- **Zones**: list of linked tops, sorted by distance: "12 blocks north", zone button (click cycles FLOOR → DELAY → ROOM), "A" when auto. Subs listed read-only. The speaker the player looks at is highlighted. Scrolls.
- **Expert**: zone selector; low/mid/high EQ, high-pass, delay ms, polarity, limit; global crossover, slope, align. 
- Every control: tooltip in plain words ("Bass: how much low end this zone plays"); right-click resets it.
- Sliders send at most once a tick (as today).

### 9. Symbiosis

- **DJ mixer** (`DjMixerScreen`): one line at the top: traffic light dot + message from `AmpStatus` for the booth's rack, and "LIMIT −x dB" in red when limiting. Nothing when the booth has no rack.
- **Block LEDs** (`AmpRackRenderer`, `ClubRenderTypes.GLOW`): three small glowing squares on the front face: POWER green (dim amber while ramping), SIGNAL blue flashing with `kickNear`, LIMIT red when any zone limits. Registered in `DrunkClient.registerRenderers`.
- Power-off silencing via the ramp (section 6).

### 10. Lang

All new strings in English via `addRawLang` (the game runs en_us) and German in `de_de.json`. The bd56b48 amp strings in `de_de.json` and `CreateBrewery.java` that are no longer used are removed.

## Testing

JUnit (no Minecraft or OpenAL):
- `AmpSettingsTest`: clamping and NaN, NBT round trip, migration of each old tag, setter → CUSTOM, preset table.
- `AutoSetupTest`: subs → SUBS, walled → ROOM, far → DELAY, nearest rule with only walled tops, crossover 80/100, align on/off.
- `ZoneDspTest` / `DeckFxTest`: neutral ZoneDsp is pass-through; LR24 and LR48 low+high sum to flat magnitude within 0.5 dB from 20 Hz to 10 kHz; polarity inverts; EQ +6 dB at the shelf end; limiter respects threshold.
- `AmpStatusTest`: each rule, priority order.

GameTest (`ClubGameTests`): replace `subsAndAmpRackLinkAndTheRackHoldsItsRange` with: rack links, settings clamp, `autoSetup` puts a sub in SUBS and a walled speaker in ROOM, a newly linked speaker gets auto-assigned within 2 s, migrated NBT loads.

`./gradlew build` and `./gradlew runGameTestServer` green. Never `runClient` from an agent.

Manual (user, in game): place rack, link, Auto Setup; traffic light green; power off silences club and stops lights; meters move; Zones tab reassign; preset buttons audibly change; LEDs flash; DJ mixer shows status.

## Risks

- LR48 adds 2 biquads per emitter per sample: ~12 biquads per emitter worst case with ZoneDsp. A dozen speakers at 48 kHz is ~7 M biquad steps/s, fine in Java; neutral stages are skipped.
- The server classifier ray-casts per new speaker every 2 s; bounded by `SpeakerBlock.MAX_LINK` and only for unassigned speakers.
- tipau9 works on the same repo. This work lives on its own branch; merging back into `drugs-apotheke` happens only after the user agrees, and conflicts in `MusicPulse`/`Emitter`/`CreateBrewery.java` are expected and resolved then.
