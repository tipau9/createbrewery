# Create Brewery — Phase 1 Design (Beer Chain)

**Date:** 2026-09-22
**Status:** Awaiting review
**Target:** Minecraft 1.21.1 · NeoForge 21.1.2xx · Create `[6.0.10,6.1.0)`

---

## Context

The goal is realistic, fully automatable alcohol production that lives *inside* Create rather than beside it — the player should build a brewery out of Create machinery, not out of a parallel set of bespoke blocks.

Nothing existing covers this:

- The target modpack (`Create Vanilla`, 165 mods) contains **no alcohol content at all**. Verified by scanning every jar's `en_us.json` for brewing vocabulary.
- **[Let's Do] Brewery** (modid `brewery`) is the closest prior art and is *not installed*. It adds beer and wine, but through its own hand-operated kettle and barrel blocks. It is not Create-automatable, which is the entire point of this request.
- `createfood` already contributes juice fluids (apple, berry, sugar cane, glow berry, chorus) — future fermentation feedstock, relevant to Phase 2.

**Intended outcome:** a standalone NeoForge addon mod providing a complete grain-to-bottle beer chain that a player automates end to end using Create's existing machines, plus exactly one new machine for the step Create cannot express.

**Repo:** `C:\Users\Anwender\dev\createbrewery` (outside the launcher profile — that directory is a game instance, not a project).

---

## Scope

**In scope (Phase 1):**

- Two crops: barley, hops
- Three fluids: wort, hopped wort, beer
- The full chain: malting → milling → mashing → boiling → fermentation → packaging
- One new block: the Fermenter
- One new recipe type: `createbrewery:fermenting`
- Packaging into bottles and cans
- JEI categories, Ponder scenes, Jade/goggle support
- Config + a GameTest covering fermentation

**Out of scope — roadmap only, not designed here:**

| Phase | Content | Why it's cheap later |
|---|---|---|
| 2 | Wine / cider | Same Fermenter, different feedstock. `createfood` juice fluids already exist. |
| 3 | Spirits | Adds one machine (still/column) on top of a finished fermentation stage. |
| 4 | Drunkenness, BAC, tolerance | Fully independent of the production chain. |

Phase 1 must ship as a playable, coherent mod on its own. It does.

---

## Build setup

All coordinates below are quoted verbatim from the [official Create addon wiki page for NeoForge 1.21.1](https://wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1). Kotlin DSL — the project uses `build.gradle.kts`.

```kotlin
repositories {
    maven("https://maven.createmod.net")            // Create, Ponder, Flywheel
    maven("https://maven.ithundxr.dev/snapshots")   // Registrate
}

dependencies {
    implementation("com.simibubi.create:create-${property("minecraft_version")}:${property("create_version")}:slim") { isTransitive = false }
    implementation("net.createmod.ponder:ponder-neoforge:${property("ponder_version")}+mc${property("minecraft_version")}")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-${property("minecraft_version")}:${property("flywheel_version")}")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-${property("minecraft_version")}:${property("flywheel_version")}")
    implementation("com.tterrag.registrate:Registrate:${property("registrate_version")}")
}
```

```properties
minecraft_version = 1.21.1
create_version = 6.0.10-280
ponder_version = 1.0.82
flywheel_version = 1.0.6
registrate_version = MC1.21-1.3.0+67
```

**Unverified, confirm at setup:** the wiki page does not state the Gradle plugin. Start from the NeoForge **ModDevGradle** MDK for 1.21.1 (current standard); if Create's own buildscript turns out to require NeoGradle, switch before writing any other code. This is step one of implementation, not a design question.

**`neoforge.mods.toml` dependency ranges** (matching what shipped addons in the pack declare):

```toml
[[dependencies.createbrewery]]
modId = "neoforge"
type = "required"
versionRange = "[21.1.228,)"

[[dependencies.createbrewery]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.1,1.22)"

[[dependencies.createbrewery]]
modId = "create"
type = "required"
versionRange = "[6.0.10,6.1.0)"
```

---

## The chain

Each arrow is a real brewing step. Every box except the Fermenter is a machine Create already ships.

```
barley  ──[ Basin: mix w/ water ]──▶  green malt
        ──[ Blaze Burner: bulk smoking ]──▶  malt          (kilning)
        ──[ Millstone ]──▶  grist                           (milling)
        ──[ Basin: HEATED, + water ]──▶  wort + spent grain (mashing + lauter)
        ──[ Basin: SUPERHEATED, + hops ]──▶  hopped wort    (the boil)
        ──[ ✦ FERMENTER, + yeast ]──▶  beer                 (fermentation)
        ──[ Spout / Press ]──▶  bottled or canned beer      (packaging)
```

Design notes per step:

- **Kilning reuses vanilla smoking.** `green_malt → malt` is registered as an ordinary vanilla smoking recipe. Create's Blaze Burner bulk-smokes items passing over it on a belt or depot, so this step should automate for free with zero new code and zero new blocks. *Confirm on first run* that a plain `minecraft:smoking` recipe is picked up by the burner's bulk processing, and that making green malt smokable doesn't leak into campfire cooking in a way we don't want. Low risk, self-correcting the moment it's tested.
- **Mashing and lautering are one recipe.** A heated Basin mixing recipe takes grist + water and outputs *both* `wort` fluid and a `spent_grain` item. Spent grain is a compostable byproduct and an animal feed item.

  *Verified:* `BasinBlockEntity.acceptOutputs(List<ItemStack>, List<FluidStack>, boolean)` takes items and fluids in a single call, and the basin maintains a separate `outputInventory` and `outputTank`. Combined item+fluid output is also a shipped pattern — `create:emptying`'s `honey_bottle` recipe returns `minecraft:glass_bottle` and 250 mB of `create:honey` from one `results` array.

  *Caveat:* no Create-shipped **mixing** recipe emits both, so this combination is exercised by the API but not by precedent in this specific machine. **Fallback if the basin refuses it in practice:** split lautering into its own stage — Basin outputs `mash` fluid only, then an Item Drain or Mechanical Press separates `wort` from `spent_grain`. This is arguably *more* realistic (lautering is a genuine separate stage) and costs only a line in the chain diagram, so the risk is bounded.

- **Recipe JSON format.** Create 6 unified item and fluid outputs into a single `results` array; the pre-6 `fluid_results` key no longer applies. Fluid entries carry an `amount` in mB, item entries do not. Datagen must target the current format.
- **The boil requires SUPERHEATED**, the mash only HEATED. This is a real distinction in brewing and Create already models the two tiers, so it costs nothing to be accurate.
- **Yeast** is an item, produced by mixing sugar + wheat in a Basin (a starter culture), consumed one per fermentation batch.

**Acquisition (entry into the chain):** barley seeds drop from tall grass alongside wheat seeds at a modest weight. Hop cones come from Farmer villager trades and as a village-chest loot entry. Both are then farmable and automatable through ordinary Create harvesting.

Deliberately **no worldgen or structure edits** — grass drop tables, villager trades, and chest loot are all plain datagen. Adding hops as a naturally generating plant in villages would mean modifying village structure templates, which is a disproportionate amount of work for a discoverability nicety. Revisit only if playtesting shows the chain's entry point is too hard to find.

---

## The Fermenter — the one new machine

Everything above maps onto Create. Fermentation does not, because **no Create machine waits on a timer**. This block is where the mod earns its existence.

### Key decision: passive vessel, driven by world time

The Fermenter requires **no rotational force**, and progress is computed from a stored world-time delta rather than a per-tick counter.

```java
// on recipe start
this.startedAt = level.getGameTime();

// progress, evaluated on tick and on load
long elapsed = level.getGameTime() - startedAt;
float progress = Mth.clamp(elapsed / (float) recipe.getProcessingDuration(), 0f, 1f);
```

Rationale — this was reconsidered after the decision to use **in-game-day timescales**, and it is better on three axes at once:

1. **Realistic.** A sealed fermentation vessel is not stirred. It sits. Requiring a shaft would be less authentic, not more.
2. **No chunkloader required.** A tick-counting machine stops when the chunk unloads, which at day-length durations would force every brewery to sit on a `create_power_loader`. A world-time delta means fermentation continues while the player is away — which is also what actually happens in a real cellar.
3. **Less code.** No kinetic block entity, no stress impact, no shaft connectivity, no rotation rendering.

Automation is unaffected: Create's fluid pipes and item funnels move wort and yeast in and beer out, because the block simply exposes the standard `IFluidHandler` and `IItemHandler` capabilities. It automates exactly like a Basin does.

> `ponytail:` single fluid tank per fermenter, one batch at a time. If throughput becomes the bottleneck, the upgrade path is parallel fermenters (the intended gameplay answer), not a multiblock.

### Block spec

| Property | Value |
|---|---|
| Fluid capacity | 1500 mB (input tank), 1500 mB (output tank) |
| Item input | 1 slot, yeast |
| Power | None |
| Heat | None required — fermentation is a cool process; heat tiers are used in mash/boil only |
| Progress display | Create goggle overlay via `IHaveGoggleInformation`, plus Jade |
| Persistence | `startedAt` (long) + recipe id, saved to NBT |

### Recipe type

`FermentingRecipe extends ProcessingRecipe<RecipeInput, ProcessingRecipeParams>`.

Confirmed subclassable by inspecting the shipped jar (`javap`): `ProcessingRecipe` is `public abstract` with a public constructor and a `Factory` interface. It already provides everything this needs —

- `NonNullList<SizedFluidIngredient> fluidIngredients` — the hopped wort
- `NonNullList<Ingredient> ingredients` — the yeast
- `NonNullList<FluidStack> fluidResults` — the beer
- `int processingDuration` — fermentation time in ticks
- `HeatCondition requiredHeat` — available, unused in Phase 1

Only `getMaxInputCount()` and `getMaxOutputCount()` are abstract and must be implemented.

**Durations** are expressed in ticks against a `DAY = 24000` constant. Default ale: one in-game day. A config multiplier scales all fermentation durations for players who want it faster or slower.

---

## Registry layout

Registrate-driven, following the conventions of the Create addons already in the pack.

```
createbrewery/
  block/          FermenterBlock, BarleyCropBlock, HopsCropBlock
  block/entity/   FermenterBlockEntity
  fluid/          ModFluids        (wort, hopped_wort, beer)
  item/           ModItems         (malt, green_malt, grist, spent_grain,
                                    hop_cones, yeast, barley, seeds,
                                    empty_can, sealed_can, beer_bottle)
  recipe/         FermentingRecipe, ModRecipeTypes
  data/           datagen entrypoints
  compat/jei/     FermentingCategory
  ponder/         scene definitions
```

Fluids are registered through `CreateRegistrate.virtualFluid(...)` — Create's builder for fluids that exist only in tanks and pipes. They deliberately have **no bucket item and no source block**, matching Create's own tea and potion fluids: you do not want lakes of wort.

*Corrected 2026-09-23.* This section previously claimed each fluid gets a bucket and a source block. That was wrong. Verified by decompiling `FluidBuilder`: `register()` only calls `.bucket()` when `defaultBucket == TRUE`, and `virtualFluid()` builds through a path that never sets it. Phase 1 moves every fluid by Create pipe and Spout, so a bucket is convenience we do not need. Re-adding one later is a single builder call per fluid.

---

## Integration

- **JEI** — one new category for fermenting. Every other step is an existing Create category and appears automatically once the recipes are datagenned.
- **Ponder** — scenes for the Fermenter and for the mash/boil heat distinction. Ponder is already a declared dependency; the pack ships `ponderjs`, so scenes are expected content here, not polish.
- **`selling_bin`** — that mod values `#brewery:beer` through a data map with quality tiers 1–3. If bottles carry a compatible quality data component, economy integration is a single JSON file rather than code. **Phase 1 ships the component and the tag but does not promise the integration works** until tested against the real mod; it is a nice-to-have, not a requirement.
- **Farmer's Delight** — barley and hops get the appropriate crop/compostable tags so existing farming automation handles them.

---

## Config

| Key | Default | Purpose |
|---|---|---|
| `fermentationDurationMultiplier` | `1.0` | Scales all fermentation times |
| `enableCans` | `true` | Cans as an alternative to bottles |

---

## Verification

1. **Compiles:** `./gradlew build`
2. **Loads:** `./gradlew runClient` — game reaches main menu with Create present, no registry errors
3. **Recipes visible:** in-game, JEI shows every step; the fermenting category renders
4. **Chain works end to end:** build the full line in creative and confirm a bottle of beer comes out the far end
5. **Fermentation timing — the one automated check:** a NeoForge `@GameTest` that places a Fermenter, fills it with hopped wort + yeast, advances world time by one day, and asserts the output tank contains beer. This is the assertion that fails if the world-time-delta logic breaks.
6. **Unload safety:** manually verify a batch started, chunk unloaded, and player returned a day later finds it finished — the behaviour the design specifically buys.

---

## Risks

| Risk | Mitigation |
|---|---|
| Gradle plugin choice (ModDevGradle vs NeoGradle) unconfirmed | Resolve as implementation step 1, before any other code |
| Basin mixing emitting item + fluid together has API support but no shipped precedent | Bounded: fallback is splitting lautering into its own Item Drain / Press stage |
| Blaze Burner bulk-smoking a custom vanilla smoking recipe assumed, not tested | Verified on first run; fallback is a Create pressing or drying recipe instead |
| Create 6.0.x is a moving target; `:slim` + `isTransitive = false` means transitive deps are hand-declared | Pin `create_version`; widen the `mods.toml` range only after testing |
| `selling_bin` component format inferred from a data map, not from its source | Treated as optional; Phase 1 does not depend on it |
| Ponder API changed in Create 6 ("ponder dependency path has changed") | Write Ponder scenes last, after the chain works |
