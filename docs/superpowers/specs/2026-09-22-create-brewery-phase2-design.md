# Create Brewery — Phase 2 Design (Wine & Cider)

**Date:** 2026-09-22
**Status:** Awaiting review
**Target:** Minecraft 1.21.1 · NeoForge 21.1.2xx · Create `[6.0.10,6.1.0)`
**Depends on:** Phase 1 (`docs/superpowers/specs/2026-09-22-create-brewery-phase1-design.md`) — shipped and merged before this starts.

---

## Context

Phase 1's roadmap says of this phase: *"Wine / cider — same Fermenter, different feedstock. `createfood` juice fluids already exist."* That is right on both counts and it is also the whole trap: if Phase 2 is only "beer but the fluid is purple", it adds items and no gameplay.

The thing wine has that beer does not is **age**. A bottle of wine is worth more at ten in-game days than at one, and nothing else in the chain works that way. That is the one mechanic Phase 2 exists to add. Everything else — the Fermenter, the `createbrewery:fermenting` recipe type, Spout bottling, Press canning — is reused verbatim from Phase 1 with no modification.

Cider is the cheap counterweight: it uses the same Fermenter and *no* aging, and it exists so the player has a fast, broad, low-value route beside wine's slow, narrow, high-value one.

### Survey results (verified against the shipped jars, not assumed)

| Question | Answer | Evidence |
|---|---|---|
| Does anything in the pack already add wine? | No | Scanned all 165 jars' `en_us.json`. Zero wine items. |
| Grapes or vineyards? | No | Same scan. Zero `grape`/`vineyard` keys anywhere. Safe to add. |
| Cider? | **Yes, one** — `farmersdelight:apple_cider` | Farmer's Delight Cooking Pot drink. It is an *item*, produced by a non-Create machine, with no fluid form. No mechanical collision with our chain; a **name** collision only. Also `displaydelight:apple_cider` (a display block for FD's item, shipped by `createfood`). |
| Grape/wine mod present? | No | `selling_bin` ships a Vinery compat datapack, but **Vinery is not installed**. |

The FD apple cider is worth one line of respect: our cider must be `createbrewery:cider_bottle`, a separate item. Do not attempt to produce `farmersdelight:apple_cider` as our chain's output — it is a differently-balanced consumable owned by another mod, and overriding it would be a surprise.

---

## Scope

**In scope (Phase 2):**

- One crop: grapes
- Six fluids: `grape_must`, `apple_juice`, `cider`, `wine`, `vintage_wine`, `reserve_wine`
- Six items: `grapes`, `cider_bottle`, `cider_can`, `wine_bottle`, `vintage_wine_bottle`, `reserve_wine_bottle`
- One new block: the **Cask** (aging)
- One new recipe type: `createbrewery:aging`
- Cider from five juice feedstocks via `c:` fluid tags
- JEI category for aging, Ponder scene for the Cask, goggle/Jade progress
- Config: one new key
- Unit tests for the aging stage math, GameTests for the Cask

**Explicitly out of scope:** spirits and distillation (Phase 3); drunkenness, BAC and tolerance (Phase 4). No wine or cider in this phase carries a status effect, exactly as Phase 1's beer does not.

Also out of scope: grape trellises or any multiblock vine. Grapes are a plain `CropBlock`, matching barley and hops.

---

## The most consequential decision: dependency posture toward `createfood`

The brief asks whether to hard-depend on `createfood`, soft-depend with fallbacks, or register our own juices. **Recommendation: soft-depend, through the `c:` fluid tags, and register our own `apple_juice` unconditionally.** The reasoning is empirical, not stylistic.

### What the jar actually shows

The fluid registry ids were never confirmed before. They are now, read out of `data/c/tags/fluid/*.json` inside `createfood-neoforge-1.21.1-2.7.1.jar`:

| Fluid | Flowing counterpart | `c:` tag |
|---|---|---|
| `createfood:apple_juice` | `createfood:flowing_apple_juice` | `c:apple_juice` |
| `createfood:berry_juice` | `createfood:flowing_berry_juice` | `c:berry_juice` |
| `createfood:glow_berry_juice` | `createfood:flowing_glow_berry_juice` | `c:glow_berry_juice` |
| `createfood:sugar_cane_juice` | `createfood:flowing_sugar_cane_juice` | `c:sugar_cane_juice` |
| `createfood:chorus_fruit_juice` | `createfood:flowing_chorus_fruit_juice` | `c:chorus_fruit_juice` |

**Correction to the brief: there are five juice fluids, not six.** `item.createfood.melon_juice_bucket` and `item.createfood.melon_juice_bottle` exist as lang keys, but there is **no** `fluid_type.createfood.melon_juice`, no `melon_juice_block` blockstate, no `melon_juice` fluid texture, and no `c:melon_juice` tag. The melon entries are orphaned lang strings. Do not design against melon juice.

Two further facts change the posture:

1. **`createfood` gates its content per-id at the datapack level.** Every juice recipe it ships carries `"neoforge:conditions":[{"type":"createfood:enabled","id":"apple_juice"}]`. That is a custom recipe condition backed by the mod's own config (`ConfigBootstrap`, `ConfigSchema`, `FluidRegistration.registerConfigFluids`). A player can switch apple juice off. Whether *registration* is also gated is **unverified** — `FluidRegistration` calls `ConfigBootstrap.read("blocks.fluid", ...)`, which strongly suggests it is, but I did not prove it. Either way, a hard reference to the literal id `createfood:apple_juice` is a dependency on another mod's config file. That is not a dependency worth having.

2. **The `c:` tags already exist and are already multi-mod.** `c:berry_juice` lists `hearthandharvest:sweet_berry_juice` alongside `createfood:berry_juice`, all entries `"required": false`. The tag is the community interface. `createfood` itself consumes it — its own bottling recipe takes `{"type":"fluid_tag","amount":250,"fluid_tag":"c:apple_juice"}`, not its own fluid id.

### The recommendation, concretely

- **No `required` dependency on `createfood`.** Add an `optional` entry in `neoforge.mods.toml` (`versionRange = "[2.7.0,)"`) purely as documentation of intent.
- **Register `createbrewery:apple_juice` ourselves, unconditionally,** and tag it into `c:apple_juice`. Unconditional registration matters: conditional registry entries produce client/server mismatches and orphaned world data, and the cost of an always-present fluid is one Registrate line and two textures.
- **Every cider recipe takes `#c:<x>_juice`, never a literal fluid id.** With `createfood` installed, its juice and ours are interchangeable inputs. Without it, ours keeps the chain alive.
- **Ship our own apples→juice recipe with `{"type":"neoforge:mod_loaded","modid":"createfood"}` inverted** — i.e. loaded only when `createfood` is *absent* — so JEI does not show two ways to press an apple in the common case. This is cosmetic; if the `neoforge:not` wrapper proves awkward, ship it unconditionally and accept the duplicate entry.
- **The four non-apple ciders load only when `createfood` is present,** gated by `neoforge:mod_loaded`. `neoforge:mod_loaded` is proven — `createfood`'s own shipped files use it. A `neoforge:not` + `neoforge:tag_empty` gate would be more correct (it would also pick up Hearth & Harvest's juices) but the fluid-registry variant of `tag_empty` is unverified; treat it as a refinement, not the baseline.

**Why not the alternatives.** A hard dependency inherits another mod's per-id config as our failure surface and makes Create Brewery unplayable without an unrelated food mod — for a phase whose only genuinely required new feedstock is *grapes*, which `createfood` does not have. Registering our own five juices and ignoring `createfood` entirely means two apple juices that cannot be piped into each other, which is exactly the fragmentation the `c:` convention exists to prevent. The tag route costs one extra fluid registration and buys both standalone play and full interoperability.

---

## The two chains

Every box except the Fermenter and the Cask is a machine Create already ships.

### Wine — depth

```
grapes  ──[ Mechanical Press over Basin: create:compacting ]──▶  grape must
        ──[ ✦ FERMENTER, + yeast, 1 day ]──────────────────────▶  wine            (tier 1)
        ──[ ✦ CASK, +4 days ]───────────────────────────────────▶  vintage wine    (tier 2)
        ──[ ✦ CASK, +12 more days ]─────────────────────────────▶  reserve wine    (tier 3)
        ──[ Spout ]─────────────────────────────────────────────▶  bottle of the tier drawn off
```

### Cider — breadth

```
apples          ──[ Press over Basin: compacting ]──▶  apple juice   (only when createfood absent)
#c:apple_juice  ──[ ✦ FERMENTER, + yeast, ½ day ]───▶  cider
#c:berry_juice          ──▶ cider     ┐
#c:glow_berry_juice     ──▶ cider     │ same Fermenter, same output fluid,
#c:sugar_cane_juice     ──▶ cider     │ gated on createfood being present
#c:chorus_fruit_juice   ──▶ cider     ┘
cider ──[ Spout ]──▶ cider bottle    ·    cider ──[ Press → empty can, Spout ]──▶ cider can
```

### Design notes per step

- **The press step is a Mechanical Press over a Basin — `create:compacting`.** This is the literal wine press, and it deliberately uses a Create machine Phase 1 never touched (Phase 1's Basin work is all `create:mixing`). Verified against the shipped Create 6.0.10 jar: `create:compacting` accepts an items-only ingredient list (`data/create/recipe/compacting/ice.json` — nine snow blocks, no fluid). Fluid results from compacting are verified by `createfood`'s shipped `apple_juice_fluid_from_compacting_water.json`. The *combination* — items only in, fluid only out — has no shipped precedent. See Risks; the fallback is free.
- **Grape must is ours and only ours.** No mod in the pack has a grape fluid, so there is no tag to join and none to invent. It is tagged `createbrewery:grape_must` and nothing else.
- **All five juices ferment to the same `cider` fluid.** Five recipes, one output. This is the breadth mechanic without an item explosion, and it means the cider bottle's identity never depends on which fruit the player automated.
- **Recipe JSON format.** Create 6 unifies item and fluid outputs into one `results` array; the pre-6 `fluid_results` key does not exist. Fluid entries carry `amount` in mB. Verified forms from the shipped jars: item result `{"id":"minecraft:diorite"}`, fluid result `{"amount":250,"id":"createfood:apple_juice"}`, fluid ingredient `{"type":"neoforge:single","amount":100,"fluid":"minecraft:lava"}` (Create's own), fluid **tag** ingredient `{"type":"fluid_tag","amount":250,"fluid_tag":"c:water"}` (observed in `createfood`, not in Create's jar, and the pack has never launched — so confirm what `SizedFluidIngredient.of(tag, amount)` emits during datagen, the same way Phase 1's Task 7 Step 9 confirms the `results` array).
- **Heat: `HeatCondition.NONE` throughout.** No step in Phase 2 needs heat. Pressing grapes is cold, fermentation is cool, and a cellar is the coldest part of the building. The `HEATED`/`SUPERHEATED` distinction stays a beer-only flourish.
- **Yeast is Phase 1's yeast.** No wine yeast, no cider yeast, no new starter cultures. One yeast item for the whole mod.

### Acquisition

Grape seeds are not a thing; grapes propagate from grapes, like Phase 1's hops from hop cones.

> **Bug found in Phase 1 while designing this, worth fixing there immediately.** Phase 1 registers `BARLEY_SEEDS` and `HOP_CONES` as plain `Item::new`. A plain `Item` has no placement behaviour, so right-clicking farmland with them does nothing — the crops exist but cannot be planted. Vanilla seeds are block items (confirm the exact class for 1.21.1; `ItemNameBlockItem` is the likely one). Phase 1's verification never caught it because every crop check uses `/setblock` and Create's Mechanical Harvester replants by resetting the crop's age rather than by using the item. Grapes must be registered the same corrected way, and once seeds become block items the `ModItems`-before-`ModBlocks` init ordering Phase 1 relies on inverts.

Entry into the chain:

- Farmer villager trade (level 2, emeralds → grapes)
- Village and plains chest loot entry

**No grass drops** — barley seeds falling out of tall grass is plausible, grapes are not — and, matching Phase 1's constraint verbatim, **no worldgen and no structure edits**.

---

## The Cask — the one new machine

### Why a second block rather than extending the Fermenter

Recommendation: **a new block**, sharing the Fermenter's world-time-delta approach but not its code path or its recipe type.

The reason is a gameplay deadlock, not aesthetics. The Fermenter is a single-batch vessel. If aging happened in it, a sixteen-day reserve wine would occupy the player's fermenter for sixteen days, during which no beer, no cider and no new wine can be started. Two blocks means the slow process parks in a cheap vessel and the busy one keeps cycling. That is the shape of every real cellar and it is also what makes the aging times affordable to set honestly long.

Two secondary reasons:

- **The interaction contract genuinely differs.** The Fermenter's contract is *consume fluid + item, emit fluid, once*. The Cask's is *hold fluid; its identity improves on a schedule; the player decides when to draw it off*. The player choosing the endpoint is precisely what produces quality tiers — a recipe cannot express "stop whenever you like".
- **World time matters even more here.** Phase 1 chose the world-time delta so a one-day batch survives chunk unload without a `create_power_loader`. A sixteen-day cask makes that property load-bearing rather than merely nice. A tick-counting cask would be unusable.

### Key decision: quality is fluid identity, not a data component

The obvious design is a `quality` data component on the bottle. **Do not do that.** In 1.21 a `FluidStack` can carry components, but Create's fluid network merges stacks as it pumps them, and two `wine` stacks with different quality components will not merge — pipes, tanks and the Spout would each have to be right about that, and none of it is verified. Losing a sixteen-day batch to a pipe that refused to merge is the worst bug this phase could ship, and Phase 1 already named that class of failure as its number-one concern.

So: **aging changes the fluid, not its metadata.**

```
wine  ──(4 days)──▶  vintage_wine  ──(12 days)──▶  reserve_wine  ──(terminal)
```

Consequences, all good:

- Zero component plumbing. Every existing Create machine handles the tiers because they are ordinary distinct fluids.
- The player automates tier selection with a Smart Fluid Pipe or a filter — pull only `reserve_wine` and the cask simply keeps everything else.
- JEI renders the whole ladder for free.
- Three bottle items instead of one bottle with three states, which is *more* items but zero runtime logic.

### Key decision: cumulative catch-up, not per-stage restart

This is the subtle part and the place the mechanic can quietly break.

Naïvely, when a stage completes you swap the fluid and set `stageStartedAt = level.getGameTime()`. A player who fills a cask and goes away for twenty days returns to find **vintage** wine with the reserve stage having just begun — every hour of offline time past the first boundary is thrown away. That defeats the entire reason the world-time delta was chosen.

The Cask must therefore advance through as many stages as the elapsed time covers, in one evaluation:

```java
// pure logic, no Minecraft types — see AgingStages below
while (nextStageExists && now - stageStartedAt >= scaledDuration) {
    stageStartedAt += scaledDuration;   // carry the remainder forward
    advanceToNextStage();
}
```

`stageStartedAt += duration` rather than `= now` is the whole trick: the leftover time rolls into the next stage. Twenty days in one visit must yield reserve wine, and the assertion for that belongs in a plain JUnit test, not only a GameTest.

### Key decision: extraction must not reset the batch

Phase 1's `FermenterBlockEntity.inputStillValid` resets the batch when the tank's amount drops below the recipe's ingredient amount — correct there, because the Fermenter is consuming that fluid. **Copying it into the Cask would mean drawing one bottle of wine destroys sixteen days of aging.** State this loudly in the plan; it is the single most likely implementation error in Phase 2.

Nor may the Cask treat the recipe's amount as a *minimum to match on*, which is the obvious half-fix. With a 250 mB minimum, a cask drained to 100 mB stops matching and freezes — and because insertion is forbidden mid-aging, the player cannot top it back up. The cask would be stuck until emptied, which directly contradicts "extract any amount, any time". **The Cask matches on fluid identity and never looks at the amount at all.**

The Cask's tank semantics:

| Property | Value |
|---|---|
| Tanks | **One**, 1500 mB. Aging converts in place; there is no separate output tank. |
| Matching | On **fluid identity only**. The Cask never compares the tank's amount against the recipe's. An aging recipe's `amount` is display metadata. |
| Extraction | Allowed at **any** time, at any tier, in any amount. Draining does not disturb `stageStartedAt`. |
| Insertion | Allowed only while the tank is **empty or idle at the terminal tier**. Forbidden mid-aging, so a player cannot dilute a twelve-day cask with fresh wine and get reserve for free. |
| Emptying the tank | Resets `stageStartedAt` to `NOT_STARTED`. Refilling starts the clock over. |
| Reaching the terminal tier | Also resets `stageStartedAt` to `NOT_STARTED`, which is what re-opens insertion. |
| Power | None |
| Heat | None |
| Progress display | Create goggle overlay via `IHaveGoggleInformation`, plus Jade — same as the Fermenter |
| Persistence | `stageStartedAt` (long) + current aging recipe id, saved to NBT |

Note what this removes: because there is no output tank, **Phase 1's "output tank full at completion" failure mode does not exist here.** A stage boundary cannot fail for lack of room.

> `ponytail:` one tank, one batch, no parallel aging slots. If throughput becomes the bottleneck, the answer is more casks — that is the intended gameplay shape of a cellar — not a multiblock.

### Recipe type: `createbrewery:aging`

`AgingRecipe extends ProcessingRecipe<RecipeInput, ProcessingRecipeParams>`, structurally a twenty-line copy of Phase 1's `FermentingRecipe`:

- `NonNullList<SizedFluidIngredient> fluidIngredients` — the fluid to age, e.g. `wine`
- `NonNullList<FluidStack> fluidResults` — the next tier
- `int processingDuration` — aging time in ticks
- item ingredients and item results: **zero** (`getMaxInputCount()` and `getMaxOutputCount()` both return 0)

**Why a second recipe type rather than reusing `createbrewery:fermenting`.** Reusing it does technically work — a fermenting recipe with no item ingredient is a valid aging recipe. But then the Fermenter can run aging recipes and the Cask can run fermenting ones, and preventing that needs runtime filtering on "does this recipe have items" in both block entities. That filter is fiddlier and far more surprising to a reader than one extra enum constant in the `ModRecipeTypes` enum Phase 1 already built to hold several. It also keeps JEI honest: aging gets its own category with days on the arrow, instead of appearing as a fermenting recipe with an empty item slot.

**Durations** are ticks against Phase 1's `DAY = 24000` constant: `wine → vintage_wine` = `4 * DAY`, `vintage_wine → reserve_wine` = `12 * DAY`. `reserve_wine` has no aging recipe, which is how the terminal tier is expressed — no sentinel value, no flag. Cumulative: sixteen in-game days from fermenter to reserve.

---

## Registry additions

Everything below is *added to* Phase 1's existing modules. No Phase 1 file is restructured.

```
createbrewery/
  block/          + CaskBlock, GrapeCropBlock
  block/entity/   + CaskBlockEntity
  fluid/          ModFluids        + grape_must, apple_juice, cider,
                                     wine, vintage_wine, reserve_wine
  item/           ModItems         + grapes, cider_bottle, cider_can,
                                     wine_bottle, vintage_wine_bottle,
                                     reserve_wine_bottle
  recipe/         + AgingRecipe, AgingStages   (ModRecipeTypes gains AGING)
  data/           + Phase 2 recipes, tags, loot, trades
  compat/jei/     + AgingCategory
  ponder/         + cask scene
```

Fluids use the same Registrate `virtualFluid` path Phase 1 established in `ModFluids` — copy that pattern exactly; do not invent a new builder chain. Same for `ModItems` (`beer_bottle` is the template for every drink item) and `ModBlocks` (`HopsCropBlock`'s registration is the template for grapes, since hops also propagate from their own harvest item rather than a seed).

Food properties: all four drink items reuse Phase 1's `ModFoods.BEER` shape — nutrition 1, saturation 0.1, `alwaysEdible()`, **no status effects**. Effects are Phase 4.

---

## Integration

- **JEI** — one new category, aging. The compacting, filling and pressing steps are existing Create categories and appear automatically. Show the aging arrow's duration in in-game **days**, as Phase 1 does for fermenting; `96000` ticks means nothing to a player.
- **Ponder** — one scene for the Cask. It must show three things a player will get wrong: that it needs no shaft, that the fluid inside *changes name* as it ages, and that drawing some off does not reset the rest.
- **Farmer's Delight** — grapes get the crop and compostable tags, same as barley and hops.
- **`selling_bin`** — see below. Recommendation differs from the brief's expectation, because the jar says something the lang file does not.

### The `selling_bin` wine hook — investigated, and the answer is "not the wine one"

The brief asks what a wine-age data map entry looks like and whether our aged wine could carry a compatible component. Both parts have concrete answers, and they point in opposite directions.

**The entry shape** (from `built_in_datapacks/selling_bin_vinery_compat/.../selling_bin_value.json`, verbatim):

```json
{ "values": { "#vinery:red_wine": { "base_value": 200,
    "processors": [ { "type": "selling_bin:wine_age_processor",
      "age_multipliers": { "1": 1.5, "2": 3.0, "3": 5.0 } } ] } } }
```

**But `WineAgeProcessor` is not reachable from our mod.** Disassembling it (`javap -p -c`) shows `addValue` reads
`net.satisfy.vinery.core.registry.DataComponentRegistry.WINE_YEAR`, casts to `net.satisfy.vinery.core.components.WineYearComponent`, and calls `net.satisfy.vinery.core.util.WineYears.getWineAgeYears(stack, level)`. It is hard-linked to Vinery's classes. There is no generic key we could set. Vinery is not installed, so the processor would `NoClassDefFoundError` or, at best, return 0 for every item. **Do not target `selling_bin:wine_age_processor`.**

**`BeerQualityProcessor`, by contrast, is fully generic.** Its `addValue` reads vanilla `DataComponents.CUSTOM_DATA`, copies the tag, and looks up the plain string key `"brewery.beer_quality"` as an int. Any mod's item can set that. Phase 1's optimism about the beer hook was therefore correct, and the same door is open to us.

**Recommendation, consistent with Phase 1's "ship the tag, don't promise the integration":**

- Each wine bottle carries a **static default** `minecraft:custom_data` component of `{"brewery.beer_quality": 1 | 2 | 3}`, set once in the item's Registrate properties. Because the tiers are separate items, this is a constant per item type — no runtime component writing, no NBT travelling through pipes.
- Ship an item tag `createbrewery:wine` covering all three bottles.
- Ship an **optional** data map at `data/selling_bin/data_maps/item/selling_bin_value.json` keyed on `#createbrewery:wine`, using `selling_bin:beer_quality_processor` with the same `1: 1.5, 2: 3.0, 3: 5.0` multipliers the shipped packs use. Gate it on `neoforge:mod_loaded` for `selling_bin`.
- Cider gets a `createbrewery:cider` tag and a flat `base_value` with no processor — it has no tiers by design.
- As in Phase 1: this is a nice-to-have. It is not tested against the real mod and nothing in Phase 2 depends on it.

One observation to carry back into Phase 1's note: `selling_bin`'s beer compat is a **built-in datapack** (`built_in_datapacks/selling_bin_brewery_compat`) whose pack.mcmeta describes it as compatibility for *[Let's Do] Brewery*, which is not installed. Whether that pack is enabled by default was not determined. If it is not, Phase 1's `#brewery:beer` tag is inert on its own and our own data map entry is the only working path — which is exactly what the recommendation above does for wine. Worth a one-line amendment to Phase 1's Integration section; it does not change Phase 1's decision.

---

## Config

Added to Phase 1's `Config`. One key.

| Key | Default | Purpose |
|---|---|---|
| `agingDurationMultiplier` | `1.0` | Scales all Cask aging times. Separate from `fermentationDurationMultiplier` because sixteen days is the number players will actually want to tune. |

Phase 1's `enableCans` is reused as-is and governs the cider can.

---

## Verification

1. **Compiles:** `./gradlew build`
2. **Unit:** `./gradlew test` — the aging stage math, including the twenty-days-offline catch-up
3. **GameTests:** `./gradlew runGameTestServer` — Cask wiring, draw-off-does-not-reset, insertion-forbidden-mid-aging, terminal tier
4. **Chain works end to end:** in creative, grapes through press, fermenter and cask produce a reserve wine bottle; apples (or any `c:` juice) produce a cider bottle
5. **Standalone:** the mod loads and the cider chain works with `createfood` **absent** — this is the assertion that proves the dependency posture
6. **Interop:** with `createfood` present, its `createfood:apple_juice` pipes straight into our Fermenter and produces cider, with no recipe change
7. **Unload safety:** a cask filled, chunk unloaded, and revisited many in-game days later shows the correct tier — not the tier it would have reached had only the first boundary counted

---

## Risks

| Risk | Mitigation |
|---|---|
| `create:compacting` with items-only in and fluid-only out has API support and adjacent precedent, but no shipped recipe of exactly that shape | Free fallback: add a small water ingredient (e.g. 50 mB), mirroring `createfood`'s shipped compacting recipes exactly. Costs one line and is arguably more honest about diluting must. |
| Copying Phase 1's `inputStillValid` into the Cask would destroy an aging batch on any extraction | Called out in the plan as Review Focus #1 with a dedicated GameTest. The Cask's tank rules are written out above precisely so this is not inferred. |
| **Phase 1's seed items are plain `Item`s and cannot be planted** — affects barley and hops *now*, not just grapes | Fix in Phase 1 as a block item; Phase 2 copies the corrected registration. Verification must plant by hand in survival, not `/setblock`. |
| A datapack aging cycle (`wine → vintage → wine`) would hang the server, since the Cask discovers its chain by following recipe results | Cap the chain walk at a fixed number of stages (16). Same datapack-robustness class as the unresolvable-recipe guard. |
| `brewery.beer_quality` is a *default* component, so `F3+H` and `/data get` may not display it | Assert it in a GameTest by reading `stack.get(CUSTOM_DATA)` instead of eyeballing a tooltip |
| A top-level `neoforge:conditions` block on a data map file is unverified | Probably unnecessary — a data map file for an unregistered type is never loaded. Drop the condition if it errors. |
| Offline time lost at stage boundaries | Cumulative `stageStartedAt += duration` catch-up loop, extracted to pure logic and unit-tested at twenty days |
| `createfood` may gate juice *registration*, not just recipes — unverified | Irrelevant to us by construction: we never name its fluid ids, only `c:` tags, and we register our own apple juice |
| `{"type":"fluid_tag",...}` ingredient shape is observed in `createfood` but not in Create's own jar, and the pack has never launched | Confirm what `SizedFluidIngredient.of(tag, amount)` emits at `runData`, same check Phase 1 does for the `results` array |
| `neoforge:not` + `tag_empty` for fluid tags unverified | Baseline gate is `neoforge:mod_loaded`, which `createfood`'s own files prove works |
| `selling_bin:wine_age_processor` is hard-linked to Vinery and unusable | Use `beer_quality_processor` with a static `custom_data` int instead; optional either way |
| Sixteen in-game days may read as too long in playtesting | `agingDurationMultiplier` exists for exactly this, and the stage durations are datagen constants |
| Three wine fluids plus three bottle items is more content than Phase 1 added | Accepted deliberately: it is the price of having zero data-component plumbing, and fluids are one Registrate line and two textures each |
