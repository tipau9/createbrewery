# Club Sound: stable speakers, room reverb, a level plan for the PA

Sub-project 3 of 4 of the club overhaul (1: symbiosis, shipped; 2: lights, shipped; 4: console and DJ booth features).
It builds on `MusicPulse`, `Emitter`, `WallFilter`, `DeckFx` and the speaker / subwoofer / amp rack block entities.

## Goal

The club sounds like a room with a PA in it, and does not drop out. Three stages in this order, each shippable and tested on its own:
1. Stability: no resync storms, no per-frame buffer churn, a budget on how many speakers and rays run at once.
2. Room reverb: one shared EFX reverb whose character follows the room around the listener.
3. PA level plan: sub and top balance, stack loudness, softer clipping, bass that carries further.

Not in this sub-project: mixer features (EQ, FX, sync, cue), the DJ booth, new blocks, stereo or line-array speakers, microphone and voice-chat routing, Etched compat, any change to the beat / drop detection. The amp rack GUI keeps its knobs; nothing new is added to it.

## What is true today (from the code and `logs/latest.log`)

- `latest.log` of one session holds 4044 `Speaker at ... resynced` lines. Only every 20th resync is logged, so that is about 80 000 restarts. They come in bursts of up to 138 lines per second, all speakers at once. A restart is `alSourceStop` plus dropping every queued buffer (`Emitter.restart`): audible as a click or dropout.
- A resync happens when the source is not running, or when its position differs from the target by more than `DRIFT = 0.07` s. The cause is not known. Candidates: the source starved because the render thread stalled for longer than the 0.3 s lead; the propagation target moving with the listener (delay towers); a paused or stopped game source; the offset query after a restart.
- `Emitter.feed` generates and deletes one OpenAL buffer per 1024 frames, per emitter, so a dozen speakers do about 500 `alGenBuffers` / `alDeleteBuffers` pairs a second.
- There is no cap on emitters per song, and each emitter casts its own rays (`hear`, 4 rays, each up to 5 clips) every 50 ms.
- Walls: `WallFilter` (24 dB/oct low-pass plus broadband loss). Air: `AL_AIR_ABSORPTION_FACTOR` 1.5. Distance: copied from the game's own source. Room: nothing; the only reverb is the DJ effect in `DeckFx`.
- `sound-physics-remastered` is installed. The emitters are raw OpenAL sources that it does not create, so it most likely leaves them alone. This is verified in game, not assumed (see Verification).
- Amp rack: crossover (Linkwitz-Riley 4th order), sub and top gain, speed of sound. `Limiter` is a hard peak limiter without look-ahead, release 80 ms.
- Levels: every speaker emitter plays at level 1 whatever the number of speakers; the booth itself plays at `MONITOR = 0.2` when speakers are linked.
- Threading: `MusicPulse.Track.read` and `kicks` are written on the sound thread and read on the render thread without `volatile`; `cursor()` does compound operations on a `CopyOnWriteArrayList`.

## Stage 1: Stability

1. **Say why it restarts.** Every restart logs its reason (`stopped`, `drift <ms>`, `starved`) in the existing `resynced` line, and the counter is per reason. This runs first: the fix follows the measured cause, not the list of candidates above. The cause found is recorded in the plan's ledger.
2. **Sync policy as a pure class `SyncPolicy`** (no Minecraft or OpenAL classes). Input: position of the source, target, rate, whether the source is running, how many frames are queued. Output: `KEEP`, `SERVO(pitchFactor)` or `RESTART`.
   - Drift under `DRIFT_SOFT = 20 ms`: keep.
   - Between `DRIFT_SOFT` and `DRIFT_HARD = 250 ms`: correct through pitch, at most plus or minus 2 percent (`servo` already exists for the propagation case; this generalises it). No restart.
   - Over `DRIFT_HARD`, or a source that stopped with frames still queued, or an empty queue: `RESTART`, but at most once per 250 ms per emitter.
3. **Buffer pool.** Each emitter keeps a free list. `reclaim` returns processed buffers to it; `feed` takes one from it and calls `alBufferData` again. `alGenBuffers` only when the list is empty. Everything is freed in `delete`.
4. **Emitter budget as a pure class `EmitterBudget<K>`** (same shape as `LightBudget`): each frame the song offers its wanted emitters with their distance to the listener; the `MAX_EMITTERS = 12` nearest keep running, the others are deleted. The subwoofers are offered first and always kept (up to 4 of them); the booth monitor counts as one. `MAX_EMITTERS` is a config value (`maxSpeakers`, 4..32, default 12), read on the client.
5. **Ray budget.** `hear` runs for at most 4 emitters per frame, round robin, each at least 50 ms apart. The eased `muffle` and `walls` values keep the sound smooth in between.
6. **Races.** `Track.read` and `kicks` become `volatile`; `cursor()` takes a local copy of the list before reading it twice.

## Stage 2: Room reverb

Only when the OpenAL device supports EFX (`ALC_EFX_MAJOR_VERSION`, already the check in `Emitter.create`); without it nothing changes and nothing is logged as an error.

1. **`RoomAcoustics`** (pure, no Minecraft classes). Input: the free distance of each of 16 rays from the listener's eye (8 horizontal, 4 up-diagonal, 4 down-diagonal) with a flag whether each ray hit something, capped at `MAX_RAY = 24` blocks. Output: `Params(decayTime, density, diffusion, lateGain, reflectionsGain)`.
   - Volume estimate from the mean free distance `d`: `V = (4/3) * pi * d^3` capped at 20000 m3. Surface `S = 4 * pi * d^2`. Decay `RT60 = 0.161 * V / (S * ABSORPTION)` with `ABSORPTION = 0.25`, clamped to 0.2 .. 3.5 s.
   - Open air: the fraction of rays that miss lowers `lateGain` and `decayTime` linearly; a fully open sky is 0 (no reverb).
   - Small rooms get short, dense reverb; a hall gets long, soft reverb. Monotonic in `d`; tested.
2. **`RoomProbe`** (client): casts the 16 rays at 4 Hz from the listener's eye through `Level.clip`, feeds `RoomAcoustics`, and eases the result over about a second so walking through a door changes the reverb gradually.
3. **One shared effect slot.** Created lazily when the first club emitter exists, one `AL_EFFECT_EAXREVERB` effect (or `AL_EFFECT_REVERB` if EAX is not available) loaded into one auxiliary slot, parameters set from the eased `Params`. Deleted when the last club track ends and on leaving the world.
4. **Per-emitter send.** Each world emitter (not the headphones) connects to the slot with an auxiliary send and a send filter. The send gain is `REVERB_SEND = 0.6` times the wet amount from the config, scaled by the broadband wall loss, so a walled-off speaker is not reverberant. Emitters of the `LOW` band send at 0.3 of that (reverb on bass is mud).
5. **Config** `clubReverb` (default on) and `reverbAmount` (0 .. 1, default 0.7), client side.

## Stage 3: PA level plan

1. **Stack loudness.** Each emitter's level is multiplied by `1 / sqrt(n)`, with `n` the number of emitters of its band (`FULL` / `HIGH` / `LOW`) within 6 blocks of the listener, at least 1 (pure function `PaLevel.stackGain`). Eight speakers on one wall no longer add up to eight times the level; one speaker alone plays at the old level. The rack's gain knobs stay as the master trim.
2. **Soft knee before the hard limiter.** `DeckFx.Limiter` gets a soft clip: below 0.8 the signal is linear, above it a `tanh` curve approaches `CEILING`. Peaks that were hard-clipped become rounded; the hard limit stays as the safety net. Pure and tested (monotonic, continuous at the knee, never above `CEILING`).
3. **Bass carries further.** The `LOW` band's reference distance is 1.5 times the one copied from the game's source, so subs fade out later than tops. Constant `SUB_REACH = 1.5`.

## Constants and config

| Name | Value | Where |
|---|---|---|
| `DRIFT_SOFT` / `DRIFT_HARD` | 20 ms / 250 ms | `SyncPolicy` |
| servo range | plus or minus 2 percent | `SyncPolicy` |
| restart cooldown | 250 ms | `SyncPolicy` |
| `MAX_EMITTERS` | 12, config `maxSpeakers` 4..32 | `EmitterBudget` |
| rays per frame | 4 emitters | `Emitter` / `MusicPulse` |
| probe rays / rate / range | 16 / 4 Hz / 24 blocks | `RoomProbe` |
| `ABSORPTION` | 0.25 | `RoomAcoustics` |
| `REVERB_SEND`, `LOW` factor | 0.6, 0.3 | `Emitter` |
| knee / ceiling | 0.8 / 0.97 | `Limiter` |
| `SUB_REACH` | 1.5 | `Emitter` |

All the numbers are first guesses to tune by ear in game; they sit in one place each so a retune is a one-line change.

## Testing

JUnit (no Minecraft or OpenAL): `SyncPolicyTest` (keep / servo / restart bands, cooldown, empty queue), `EmitterBudgetTest` (nearest kept, subs always kept, ties stable), `RoomAcousticsTest` (small room short, hall long, open sky dry, monotonic, clamps), `PaLevelTest` (stack gain for 1, 2, 8 and clamping), `LimiterTest` (soft knee: continuous, monotonic, under the ceiling, unity below the knee). The existing `DeckFxTest` and `WallFilterTest` stay green.

Client audio has no GameTest. In game, with `logs/latest.log` open:
- Resync lines: none while standing still in a booth with 8+ speakers for 5 minutes; a few after a deliberate lag (F3+T, alt-tab) and no storm after it.
- Walk from the dance floor to a stairwell, a toilet and outside: reverb changes gradually, dry outside, no clicks.
- Sound Physics Remastered on: the club speakers are not reverberated twice; with it off the same.
- 2 and 8 speakers: roughly the same loudness at the dance floor; the clip light still reacts.
- No `AL lib` or OpenAL error lines, no `createbrewery` errors, no `ZipException`.

## Risks

- The resync cause may be something other than the candidates (a game thread stall, another mod). Stage 1 step 1 settles it; if the measured cause is outside this code, the policy and budget still limit the damage and the finding is reported.
- `1/sqrt(n)` changes the felt loudness of existing clubs. The rack knobs and the game's volume slider compensate; if it is wrong, only the constant changes.
- EFX reverb on a raw source depends on the driver (OpenAL Soft is the default in Minecraft). Without EFX the club sounds as it does today.
