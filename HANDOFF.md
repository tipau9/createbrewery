# HANDOFF — Create Brewery, Phase 1

**For whoever picks this up next (human or AI).** Written 2026-09-24.

You have zero context. This file is everything you need. Read it fully before touching anything.

---

## 0. FIRST, BEFORE ANYTHING ELSE

The previous session may have been cut off mid-task. Run this:

```bash
cd C:/Users/Anwender/dev/createbrewery
git status --short
git log --oneline | head -5
```

- **Uncommitted changes in `src/main/java`?** A task was interrupted. Read `.superpowers/sdd/2026-09-22-create-brewery-phase1/progress.md` (the ledger), find the last `dispatched` line with no matching `complete`, and finish that task. Do not discard the work without reading it.
- **Uncommitted `src/generated/`?** Ignore it. That is datagen output and is regenerated on demand. Often it is only CRLF line-ending noise.
- **Clean tree?** Good. Go to section 5.

---

## 1. What this project is

A **Minecraft mod**: `Create Brewery`, a NeoForge 1.21.1 addon for the **Create** mod. It adds realistic, fully automatable beer production — the player builds a brewery out of Create's own machinery rather than a parallel set of bespoke blocks.

The chain:

```
barley → (Basin+Mixer: steep) → green malt
       → (Encased Fan through fire: bulk smoking) → malt
       → (Millstone) → grist
       → (Basin+Mixer, HEATED, + water) → wort + spent grain
       → (Basin+Mixer, SUPERHEATED, + hops) → hopped wort
       → (★ FERMENTER, + yeast, one in-game day) → beer
       → (Spout fills bottles / Press makes cans) → packaged beer
```

**The Fermenter is the only new machine.** Everything else reuses Create. It is a *passive* block — no rotational force — that stores a world-time stamp and computes progress as `level.getGameTime() - startedAt`. That design is deliberate: fermentation continues while the chunk is unloaded, so no chunkloader is needed. **Do not "improve" this into a per-tick counter.**

Authoritative documents, in the repo:
- Spec: `docs/superpowers/specs/2026-09-22-create-brewery-phase1-design.md`
- Plan: `docs/superpowers/plans/2026-09-22-create-brewery-phase1.md`
- Ledger (every decision and why): `.superpowers/sdd/2026-09-22-create-brewery-phase1/progress.md`
- Per-task reports with evidence: `.superpowers/sdd/2026-09-22-create-brewery-phase1/task-*-report.md`

Phase 2 (wine/cider) is already designed but **not started**: `docs/superpowers/specs/2026-09-22-create-brewery-phase2-design.md` and the matching plan. Do not start Phase 2 until Phase 1 closes.

---

## 2. State as of this writing

Branch `phase-1-beer-chain`, at commit `7b81b52`. Never merged to any main branch; there is no remote.

| Task | Status |
|---|---|
| 1 — scaffold + toolchain | complete, reviewed |
| 2–4 — fluids, items, crops, recipe type | complete, reviewed |
| D — datagen + run configs (added mid-flight; not in the original plan) | complete, reviewed |
| 5 — Fermenter | complete, reviewed, 1 fix round |
| 7 — recipe chain | complete, reviewed |
| 8 — acquisition, tags, crop models | complete, reviewed |
| **6 — goggle overlay** | **was in flight when this was written — check `git status`** |
| 9 — JEI category | not started |
| 10 — Ponder scenes | not started |

---

## 3. How to verify anything

```bash
./gradlew build                # compiles + 7 JUnit tests
./gradlew runGameTestServer    # 9 GameTests — expect "All 9 required tests passed"
./gradlew runData              # regenerates src/generated
```

All three were green at `7b81b52`. There is **no `gradle` CLI** on this machine — always use `./gradlew`. JDK 21 is at `C:\Program Files\Java\jdk-21`, `JAVA_HOME` is set. Gradle tasks can take minutes; allow long timeouts.

**Never run `./gradlew runClient` from an automated agent.** It launches a GUI Minecraft client you cannot observe and it will hang your session. That was a standing rule for the whole project. A human runs it.

---

## 4. Hard-won facts. Do not re-derive these; several cost hours

Every one of these was discovered by checking the actual jars. Several contradict what the plan and spec originally said.

**Create / Registrate**
- `ProcessingRecipeSerializer` **does not exist** in Create 6.0.10. The plan invented it. `FermentingRecipe` extends `StandardProcessingRecipe<RecipeInput>` and reuses its nested `Serializer`.
- `ModRecipeTypes` holds its `DeferredRegister`s in a **nested `Registers` class**. This is not stylistic — enum constants initialize before textually later static fields of the enclosing class, so putting them directly on the enum NPEs on the first constant. Create's own `AllRecipeTypes` does exactly this.
- Recipe IDs are **type-prefixed** by `ProcessingRecipeBuilder.build()`: `createbrewery:mixing/steeping`, `createbrewery:fermenting/fermenting_ale`, `createbrewery:pressing/empty_can`. Only `createbrewery:kilning` (a vanilla smoking recipe) is unprefixed. Never build a recipe id by concatenating a name.
- Create 6 **unified item and fluid outputs into one `results` array**. The pre-6 `fluid_results` key does not exist. Fluid entries carry `amount` in mB; item entries do not.
- A Basin mixing recipe **can** emit an item and a fluid together (`BasinRecipe`: max 4 item / 2 fluid outputs). This was the plan's biggest open risk; it is resolved.
- **Every Basin step needs a Mechanical Mixer**, including the two that need no heat. `create:mixing` ≠ plain basin. A Basin with only a Blaze Burner under it runs no step of this chain.
- **Bulk smoking is the Encased Fan blowing through fire**, not the Blaze Burner (`AllFanProcessingTypes` lives under `content/kinetics/fan/processing/`). The Blaze Burner only heats Basins. The spec originally got this wrong.
- `AllItems.IRON_SHEET` is a real item, not a tag.
- Create **jarJars Registrate, Flywheel and Ponder** (`META-INF/jarjar/`). Grepping the outer jar for `com/tterrag/registrate` returns 0 — the classes are inside a nested jar. Do not conclude Registrate is absent.
- Registrate **auto-wires datagen**: `registerEventListeners(modEventBus)` adds a `GatherDataEvent` listener gated on `DatagenModLoader::isRunningDataGen`. No `GatherDataEvent` boilerplate is needed for Registrate's own providers.
- Registrate's `.blockstate()` **replaces** its queued default rather than layering.
- `CreateRegistrate.virtualFluid(...)` never calls `.defaultBucket()` or `.defaultLang()`. Our three fluids therefore have **no bucket item and no source block** — this is correct and intended (all transport is pipes and the Spout). The spec originally claimed otherwise.

**Silent-failure traps**
- **`ModFluids.X.get()` returns the FLOWING fluid, not the source.** A tank filled via `.get()` holds `flowing_hopped_wort` and matches nothing, with no error. Use `.getSource()` anywhere you name or match a fluid by hand. (Create's datagen normalizes internally, so generated recipes are safe — the hazard is hand-written lookup code.)
- GameTests must live in **`src/main/java`**, not `src/test/java`, or they are silently never discovered.
- `@GameTest(template = "createbrewery:platform")` **doubles the namespace**; you need `@PrefixGameTestTemplate(false)`.

**Minecraft 1.21.1 / NeoForge**
- `ItemLike` is `net.minecraft.world.level.ItemLike`, **not** `net.minecraft.world.item.ItemLike`.
- `ItemHelper` is `com.simibubi.create.foundation.item.ItemHelper`, not `...foundation.utility`.
- `VillagerTradesEvent` fires on `NeoForge.EVENT_BUS`, **not** the mod bus. Wrong bus = the trade silently never appears.
- Composting is a NeoForge **data map** (`NeoForgeDataMaps.COMPOSTABLES`), not a tag.
- NeoForge ships **no insert-only** item handler wrapper, but it does ship `ForwardingItemHandler` you can extend.
- Vanilla `short_grass`/`tall_grass` use `random_chance` for wheat seeds, **not** pool weight.
- `onRemove` / `destroy()` only run **server-side** (`LevelChunk.setBlockState` guards on `!level.isClientSide`), so there is no client-side duplicate-drop risk.

---

## 5. What to do next, in order

1. **If Task 6 was interrupted**, finish it. Scope: override `addToGoggleTooltip` on `FermenterBlockEntity` (idle / percent / days-remaining / both tank contents via the `containedFluidTooltip` default), add fluid lang names, and fix the config desync below.
   - **Config desync:** `Config.FERMENTATION_DURATION_MULTIPLIER` is `COMMON`, which NeoForge does not sync. `getProgress()` runs client-side for goggles, so a server with a non-default multiplier shows a wrong bar. Fix by writing the scaled duration into `write(..., clientPacket)`.
   - Note the established pattern: lang strings go through `REGISTRATE.addRawLang(...)` in `CreateBrewery.java`, feeding the single generated `en_us.json`. **There is no hand-written lang file — do not create one, it would collide.**
2. **Task 9 — JEI category** for fermenting. Every other step is an existing Create category and appears automatically. Needs a JEI dependency in `build.gradle.kts` (`jei_version` to match the target pack: `19.57.0.444`). Display the duration as "1 day", not "24000 ticks".
3. **Task 10 — Ponder scenes.** Show the Fermenter needing **no shaft** (players will expect one) and show the Mixer on the Basin for the mash/boil heat distinction.
4. **Final whole-branch review** on the most capable model available, then merge.

Tasks 9 and 10 both add `addRawLang` calls to `CreateBrewery.java`. That file caused both merge conflicts during the three-way merge. **Run them sequentially, or expect a conflict there.**

---

## 6. Rules that were in force. Keep them unless you have a reason

- **Never run `runClient`** from an agent (section 3).
- **Never commit `src/generated/`** from a parallel task. Regenerate once, centrally, and commit as a single commit.
- **Never dispatch two implementers at the same repo** without isolating them; they collide on `CreateBrewery.java` and race the git index.
- Commit with `git -c user.email=monkememelover@gmail.com -c user.name="Anwender"` — there is no global git identity configured.
- **No worldgen or structure edits.** Acquisition is loot tables, villager trades and chest loot only. Hard scope boundary from the spec.
- Phase 1 scope only: **no wine, no spirits, no drunkenness, no status effects on beer.** Those are Phases 2–4.

---

## 7. THE BIG CAVEAT — read this one

**This mod has never been launched in Minecraft. Not once.**

Every check has been headless: compilation, unit tests, GameTests, datagen, and decompilation of the real jars. That caught many genuine bugs. It cannot tell you whether the thing actually works in a world.

Still completely unverified, and needing a human at a running client:

- Do barley and hops **plant, grow through 8 stages, and render as crosses** rather than cubes?
- Do the **13 placeholder textures** (82-byte PNGs) look like anything? They are deliberately blank.
- Does the **goggle overlay** read correctly, and does Jade show the same?
- Does the **Encased Fan** actually bulk-smoke our custom `minecraft:smoking` recipe? (Assumed, never tested.)
- Does the **villager trade** appear, does the **chest loot** appear, does **composting** work?
- Does a **hopper/funnel** feed and drain the Fermenter in a real contraption?
- Does a batch genuinely survive a **real chunk unload and reload**?
- Does the **full chain run end to end** in a world?

Install it into the target modpack (`C:\Users\Anwender\AppData\Roaming\ModrinthApp\profiles\Create Vanilla`, 165 mods, Create 6.0.10) and play it. Expect the first launch to surface things no static check could.

One known candidate: `dev.engine-room.vanillin` is not pulled in by the `:slim` + non-transitive Create dependency. It was deliberately not added, because Create declares only `flywheel` and `ponder` as required mods. If the client crashes with a vanillin `NoClassDefFoundError`, add one line: `runtimeOnly("dev.engine-room.vanillin:vanillin-neoforge-1.21.1:1.1.3-41")`.
