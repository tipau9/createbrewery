# Create Brewery Phase 2 Implementation Plan (Wine & Cider)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add wine and cider on top of Phase 1's finished beer chain, reusing the Fermenter and the `createbrewery:fermenting` recipe type unchanged, and adding exactly one new block — the Cask — for aging.

**Architecture:** Cider is a second Fermenter recipe set fed from the `c:` juice fluid tags. Wine is the same, plus a Cask that converts `wine → vintage_wine → reserve_wine` on a world-time schedule. Quality is **fluid identity**, never a data component, so Create's pipes and tanks need no special handling. The Cask reuses Phase 1's world-time-delta approach and adds a cumulative catch-up loop so offline time is never lost at a stage boundary.

**Tech Stack:** Java 21 · NeoForge 21.1.2xx · Create 6.0.10 · Registrate · Ponder · Flywheel · Gradle (Kotlin DSL) · JUnit 5 + NeoForge GameTest

**Spec:** `docs/superpowers/specs/2026-09-22-create-brewery-phase2-design.md`
**Prerequisite:** Phase 1 complete and merged. This plan modifies Phase 1 files; it does not restructure them.

---

## Global Constraints

Every task's requirements implicitly include these. Phase 1's Global Constraints all still apply — these are the additions and the ones worth repeating.

- **Mod id:** `createbrewery`. Versions unchanged: MC `1.21.1`, NeoForge `[21.1.228,)`, Create `[6.0.10,6.1.0)`.
- **No new hard dependency.** `createfood` is declared `optional` only. **No recipe in this phase may name a `createfood:` fluid id.** Cider inputs are `c:` fluid tags exclusively.
- **Confirmed `createfood` fluid ids** (for reference only, do not hard-code): `createfood:apple_juice`, `berry_juice`, `glow_berry_juice`, `sugar_cane_juice`, `chorus_fruit_juice`, each with a `flowing_` counterpart and a `c:<name>` tag. **There is no melon juice fluid** — the melon lang keys are orphans.
- **Recipe JSON format:** Create 6 unifies item and fluid outputs into a single `results` array. The pre-6 `fluid_results` key does not exist. Fluid entries carry `amount` in mB; item entries do not.
- **Heat is `HeatCondition.NONE` for every Phase 2 recipe.** No step here needs heat.
- **The Cask requires no rotational force** and has no stress impact, exactly as the Fermenter does not. Progress is world time, never a tick counter.
- **Quality never lives in a data component on a fluid.** Three tiers are three fluids. The only component anywhere in this phase is a *static* `minecraft:custom_data` on the bottle items, set at registration.
- **No worldgen or structure edits.** Grape acquisition is villager trades and chest loot only — no grass drops.
- **No status effects on any drink.** Drunkenness is Phase 4.
- **No spirits, no distillation.** That is Phase 3.
- **Copy Phase 1's established patterns rather than inventing API calls.** Each task below names the Phase 1 file to copy from. Registrate, JEI and Ponder signatures were resolved during Phase 1 — read them, do not guess.
- Commit after every task. Conventional commit prefixes (`feat:`, `test:`, `chore:`).

---

## Review Focus

Failure modes the spec implies but which no obvious happy-path test exercises. Each has a test assigned to the task that owns the code.

1. **Extraction mid-aging must not reset the batch.** Phase 1's `FermenterBlockEntity.inputStillValid` resets when the tank amount drops below the recipe's ingredient amount. Copying that into the Cask means drawing one bottle of wine destroys sixteen days of aging. This is the single most likely error in Phase 2 and the worst bug it could ship. *Covered in Tasks 4 and 5.*
2. **Offline time must carry across stage boundaries.** Twenty days away must yield reserve wine in one evaluation, not vintage with the reserve clock just starting. Requires `stageStartedAt += duration`, not `= now`. *Covered in Task 4.*
3. **Insertion while aging must be refused.** Topping a twelve-day cask up with fresh wine must not yield reserve wine for free. *Covered in Task 5.*
4. **Terminal tier must halt cleanly.** `reserve_wine` has no aging recipe. The catch-up loop must terminate, not spin, NaN, or advance past the end. *Covered in Task 4.*
5. **Unresolvable saved aging recipe id.** Same class as Phase 1's Review Focus #2 — a Cask whose NBT names a missing recipe must not crash the chunk. *Covered in Task 5.*
6. **`createfood` absent leaves `c:berry_juice` and friends empty.** The cider chain must still work from our own apple juice, and JEI must not show recipes with blank ingredient slots. *Covered in Task 7 and the Definition of Done.*
7. **World time moves backwards** (`/time set`, backup restore) during aging. Already solved for fermentation by `FermentationProgress`; the aging math must inherit the same clamping. *Covered in Task 4.*
8. **A cyclic aging chain in a datapack** (`wine → vintage → wine`) would hang the server every tick, because the Cask discovers its chain by following recipe results rather than from a fixed list. The walk must be hard-capped. *Covered in Task 5.*
9. **The seed/planting item must actually place the crop.** Phase 1 registers `BARLEY_SEEDS` and `HOP_CONES` as plain `Item::new`, which cannot be planted at all — see Task 2. Grapes must not inherit that bug. *Covered in Task 2.*

---

## File Structure

Additions to Phase 1's tree. Modified files are marked `~`.

```
createbrewery/
├── src/main/java/com/createbrewery/
│   ├── ~ ModFluids.java                   + grape_must, apple_juice, cider,
│   │                                        wine, vintage_wine, reserve_wine
│   ├── ~ ModItems.java                    + grapes, cider_bottle, cider_can,
│   │                                        3 wine bottles
│   ├── ~ ModBlocks.java                   + cask, grape crop
│   ├── ~ ModBlockEntities.java            + cask BE type
│   ├── ~ ModRecipeTypes.java              + AGING
│   ├── ~ Config.java                      + agingDurationMultiplier
│   ├── block/
│   │   ├── + CaskBlock.java
│   │   └── + GrapeCropBlock.java
│   ├── block/entity/
│   │   └── + CaskBlockEntity.java         one tank, stage chain, goggle tooltip
│   ├── recipe/
│   │   ├── + AgingRecipe.java             extends ProcessingRecipe
│   │   └── + AgingStages.java             PURE stage-advance math — unit tested
│   ├── data/                              + Phase 2 recipes, tags, loot, trades
│   ├── compat/jei/                        + AgingCategory.java
│   └── ponder/                            + cask scene
└── src/test/java/com/createbrewery/
    └── + AgingStagesTest.java             plain JUnit, no Minecraft needed
```

**Key decomposition decision, inherited from Phase 1:** the stage-advance arithmetic lives in `AgingStages`, a pure static class with no Minecraft types. Review Focus items #2, #4 and #7 are then unit-testable in milliseconds rather than only through a GameTest that must boot a server. `AgingStages` delegates the clamping to Phase 1's existing `FermentationProgress.progress(...)` — do not reimplement that math.

---

## Task 1: Fluids and drink items

**Files:**
- Modify: `src/main/java/com/createbrewery/ModFluids.java`
- Modify: `src/main/java/com/createbrewery/ModItems.java`
- Create: fluid textures under `src/main/resources/assets/createbrewery/textures/fluid/`

**Interfaces:**
- Consumes: `CreateBrewery.REGISTRATE`, `ModFoods.BEER`
- Produces: `ModFluids.GRAPE_MUST`, `APPLE_JUICE`, `CIDER`, `WINE`, `VINTAGE_WINE`, `RESERVE_WINE`; `ModItems.GRAPES`, `CIDER_BOTTLE`, `CIDER_CAN`, `WINE_BOTTLE`, `VINTAGE_WINE_BOTTLE`, `RESERVE_WINE_BOTTLE`

- [ ] **Step 1: Add the six fluids**

Copy the exact `virtualFluid(...)` builder chain Phase 1 established in `ModFluids` for `WORT`/`BEER` — it was confirmed against the resolved Registrate dependency during Phase 1's Task 2, so use it verbatim rather than writing a new chain. Add, in this order:

`grape_must`, `apple_juice`, `cider`, `wine`, `vintage_wine`, `reserve_wine`

- [ ] **Step 2: Add the fluid textures**

`<name>_still.png` (16×16) and `<name>_flow.png` (16×512) for each, matching Phase 1's texture conventions. Suggested palette: grape must deep purple-red and cloudy; wine clear red; vintage wine darker; reserve wine darkest with the most saturation — the three wines must be distinguishable **at a glance in a tank**, because that is how a player reads the cask's state before they have goggles on. Apple juice pale gold, cider hazy gold.

- [ ] **Step 3: Add the six items**

Copy Phase 1's `BEER_BOTTLE` registration as the template for all four drinks. All four use `ModFoods.BEER` — nutrition 1, saturation 0.1f, `alwaysEdible()`, **no effects**.

`GRAPES` doubles as the planting item, so it must be a **block item** that places `ModBlocks.GRAPE_CROP` — **not** a plain `Item::new`. See the warning in Task 2 Step 1; register it the same way Phase 1's corrected `HOP_CONES` is registered, whatever class that turns out to use.

`CIDER_CAN` mirrors Phase 1's `SEALED_CAN` and is governed by the existing `enableCans` config.

- [ ] **Step 4: Attach the static quality component to the wine bottles**

Each wine bottle carries a constant `minecraft:custom_data` of `{"brewery.beer_quality": N}` — `1` for `wine_bottle`, `2` for `vintage_wine_bottle`, `3` for `reserve_wine_bottle`. Set it once in the item's Registrate `.properties(...)` via `DataComponents.CUSTOM_DATA` and `CustomData.of(CompoundTag)`.

This is read by `selling_bin`'s `BeerQualityProcessor`, which was verified by disassembly to read exactly this vanilla component and this literal string key. It is static per item type, so **no runtime component writing anywhere in this mod**. Do not add a dynamic quality component to anything.

Cider bottles and cans get **no** component — cider has no tiers.

- [ ] **Step 5: Verify in game**

Run: `./gradlew runClient`
Expected: `/give @s createbrewery:reserve_wine_bucket` shows the fluid with no magenta; every drink item exists via `/give`. The `brewery.beer_quality` value is a **default** component and may not appear in `F3+H` or `/data get` — assert it in the Task 5 GameTest by reading `stack.get(DataComponents.CUSTOM_DATA)` rather than trusting a tooltip here.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/createbrewery/ModFluids.java src/main/java/com/createbrewery/ModItems.java src/main/resources/assets/createbrewery/textures/fluid/
git commit -m "feat: add wine, cider, must and juice fluids with drink items"
```

---

## Task 2: The grape crop and its acquisition

**Files:**
- Create: `src/main/java/com/createbrewery/block/GrapeCropBlock.java`
- Modify: `src/main/java/com/createbrewery/ModBlocks.java`
- Modify: `src/main/java/com/createbrewery/data/ModLootModifiers.java`, `ModTagsProvider.java`, `ModVillagerTrades.java`

**Interfaces:**
- Consumes: `ModItems.GRAPES`
- Produces: `ModBlocks.GRAPE_CROP`

> ### ⚠ Fix Phase 1 first — its seeds cannot be planted
>
> Phase 1 registers `BARLEY_SEEDS` and `HOP_CONES` as plain `Item::new`. A plain `Item` has **no placement behaviour**: right-clicking farmland with them does nothing, so neither crop can be planted by hand. Vanilla seeds are block items — confirm the exact class for 1.21.1 (`ItemNameBlockItem` is the likely one) against the resolved dependency rather than guessing.
>
> Phase 1's verification missed this because every crop check uses `/setblock`, and Create's Mechanical Harvester replants by resetting the crop's age rather than by consuming the item. Fix barley and hops in Phase 1, then register grapes the same corrected way.
>
> **Init ordering inverts.** Phase 1 notes "`ModItems` must load before `ModBlocks`, because the crop blocks reference seed items." Once seeds are block items the dependency runs the other way. Resolve it with a supplier on whichever side is registered second; do not reorder blindly.

- [ ] **Step 1: Write the crop block**

`GrapeCropBlock extends CropBlock` returning `ModItems.GRAPES.get()` from `getBaseSeedId()`. It is a structural copy of Phase 1's `HopsCropBlock` — hops also propagate from their harvest item rather than a separate seed, so grapes need no new item. Seven stages, plain `CropBlock`. **No trellis, no vine, no multiblock.**

- [ ] **Step 2: Register it**

In `ModBlocks`, copying the `HOPS_CROP` entry exactly (`initialProperties(() -> Blocks.WHEAT)`, `noOcclusion`). The grapes **item** is registered as the block item for this block — see the warning above.

- [ ] **Step 3: Acquisition — trade and loot only**

- Farmer villager trade, level 2: emeralds → grapes. Add to Phase 1's `ModVillagerTrades`.
- Chest loot injection into `minecraft:chests/village/village_plains_house`, alongside Phase 1's hop cones.
- **No grass drop.** Barley seeds falling from tall grass is plausible; grapes are not. Do not add one.
- **No worldgen, no structure edits** — Global Constraints.

- [ ] **Step 4: Tags**

Grapes and the grape crop into the same farmland/crop and Farmer's Delight tags Phase 1 applied to barley and hops, so existing harvesting automation handles them with no extra work.

- [ ] **Step 5: Verify in game**

Run: `./gradlew runClient`
Expected, **in survival, planting by hand** — not `/setblock`, which is exactly what let Phase 1's planting bug through: right-clicking farmland with grapes places `createbrewery:grape_crop`, which grows through seven stages and drops grapes when mature. Then check the same for Phase 1's barley seeds and hop cones. A Farmer villager offers grapes; a Create Mechanical Harvester replants the crop.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/createbrewery/block/GrapeCropBlock.java src/main/java/com/createbrewery/ModBlocks.java src/main/java/com/createbrewery/data/ src/main/java/com/createbrewery/ModVillagerTrades.java src/generated/
git commit -m "feat: add grape crop with trade and loot acquisition"
```

---

## Task 3: The aging recipe type

**Files:**
- Create: `src/main/java/com/createbrewery/recipe/AgingRecipe.java`
- Modify: `src/main/java/com/createbrewery/ModRecipeTypes.java`

**Interfaces:**
- Consumes: `CreateBrewery.ID(String)`
- Produces: `AgingRecipe` (extends `ProcessingRecipe<RecipeInput, ProcessingRecipeParams>`); `ModRecipeTypes.AGING`

- [ ] **Step 1: Write `AgingRecipe`**

A structural copy of Phase 1's `FermentingRecipe`. Same superclass, same `matches` returning `false` (the Cask matches explicitly against its tank, which a flat `RecipeInput` cannot express — the same reason Phase 1 gave). The counts differ:

```java
@Override protected int getMaxInputCount()       { return 0; }   // no items at all
@Override protected int getMaxOutputCount()      { return 0; }
@Override protected int getMaxFluidInputCount()  { return 1; }
@Override protected int getMaxFluidOutputCount() { return 1; }
@Override protected boolean canSpecifyDuration() { return true; }
```

Aging is fluid-in, fluid-out, nothing else. There is no yeast, no cork, no barrel item.

- [ ] **Step 2: Add `AGING` to `ModRecipeTypes`**

`ModRecipeTypes` is already an enum built to hold several constants — add `AGING(AgingRecipe::new)` beside `FERMENTING`. The `DeferredRegister` wiring Phase 1 built in its Task 4 Step 3 handles the new constant with no change; confirm the serializer and type both register.

> Reusing `createbrewery:fermenting` for aging was considered and rejected: it would let the Fermenter run aging recipes and the Cask run fermenting ones, requiring runtime "does this recipe have items" filtering in both block entities. One extra enum constant is smaller and far less surprising, and it gives JEI an honest separate category.

- [ ] **Step 3: Verify registration**

Run: `./gradlew runClient`
Expected: no registry errors at startup; `/reload` succeeds. No aging recipes exist yet — that is Task 7.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/createbrewery/recipe/AgingRecipe.java src/main/java/com/createbrewery/ModRecipeTypes.java
git commit -m "feat: add createbrewery:aging recipe type"
```

---

## Task 4: The aging stage math — pure logic, TDD

The one genuinely tricky piece of Phase 2, extracted so it is testable without booting Minecraft. Review Focus #2, #4 and #7 all land here.

**Files:**
- Create: `src/main/java/com/createbrewery/recipe/AgingStages.java`
- Create: `src/test/java/com/createbrewery/AgingStagesTest.java`

**Interfaces:**
- Consumes: `FermentationProgress.progress(long, long, int)` and `FermentationProgress.NOT_STARTED` from Phase 1
- Produces: `AgingStages.advance(long stageStartedAt, long now, IntUnaryOperator durationOfStage, int startStage, int stageCount)` returning a small result record of `(int stage, long stageStartedAt)`

- [ ] **Step 1: Write the failing test**

The shape below is the contract. Model the stage chain in the test as a plain `int[] durations` so no Minecraft types appear.

```java
package com.createbrewery;

import com.createbrewery.recipe.AgingStages;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgingStagesTest {

    private static final int DAY = 24000;
    // wine -> vintage (4 days) -> reserve (12 days) -> terminal
    private static final int[] DURATIONS = { 4 * DAY, 12 * DAY };
    private static final int STAGES = 3;   // wine, vintage, reserve

    private static AgingStages.Result advance(long startedAt, long now, int stage) {
        return AgingStages.advance(startedAt, now, i -> DURATIONS[i], stage, STAGES);
    }

    @Test
    void beforeFirstBoundaryStaysPut() {
        AgingStages.Result r = advance(0L, 3L * DAY, 0);
        assertEquals(0, r.stage());
        assertEquals(0L, r.stageStartedAt());
    }

    @Test
    void crossesOneBoundary() {
        AgingStages.Result r = advance(0L, 5L * DAY, 0);
        assertEquals(1, r.stage());
        assertEquals(4L * DAY, r.stageStartedAt());   // remainder carried, NOT reset to now
    }

    // Review Focus #2: twenty in-game days offline must reach the terminal tier in ONE call
    @Test
    void crossesEveryBoundaryInOneEvaluation() {
        AgingStages.Result r = advance(0L, 20L * DAY, 0);
        assertEquals(2, r.stage());
        assertEquals(16L * DAY, r.stageStartedAt());
    }

    // Review Focus #4: the terminal tier halts
    @Test
    void terminalStageNeverAdvances() {
        AgingStages.Result r = advance(0L, Long.MAX_VALUE / 2, 2);
        assertEquals(2, r.stage());
        assertEquals(0L, r.stageStartedAt());
    }

    @Test
    void enormousElapsedDoesNotSpinOrOverflow() {
        AgingStages.Result r = advance(0L, Long.MAX_VALUE, 0);
        assertEquals(2, r.stage());
    }

    // Review Focus #7: world clock moved backwards
    @Test
    void negativeElapsedStaysPut() {
        AgingStages.Result r = advance(9L * DAY, 1L * DAY, 0);
        assertEquals(0, r.stage());
        assertEquals(9L * DAY, r.stageStartedAt());
    }

    @Test
    void notStartedStaysPut() {
        AgingStages.Result r = advance(-1L, 5L * DAY, 0);
        assertEquals(0, r.stage());
        assertEquals(-1L, r.stageStartedAt());
    }

    @Test
    void exactBoundaryAdvancesExactlyOnce() {
        AgingStages.Result r = advance(0L, 4L * DAY, 0);
        assertEquals(1, r.stage());
        assertEquals(4L * DAY, r.stageStartedAt());
    }

    @Test
    void zeroDurationStageDoesNotHang() {
        AgingStages.Result r = AgingStages.advance(0L, 10L, i -> 0, 0, STAGES);
        assertEquals(2, r.stage());   // falls straight through to terminal, no infinite loop
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.createbrewery.AgingStagesTest"`
Expected: FAIL — `AgingStages` does not exist (compilation error).

- [ ] **Step 3: Write the minimal implementation**

```java
package com.createbrewery.recipe;

import java.util.function.IntUnaryOperator;

/**
 * Advances a cask through as many aging stages as the elapsed world time covers.
 *
 * The carry is the whole point: stageStartedAt += duration, never = now. A player
 * away for twenty in-game days must come back to reserve wine, not to vintage with
 * the reserve clock just starting. That is the property the world-time-delta design
 * exists to buy, and resetting the stamp to "now" would silently throw it away.
 */
public final class AgingStages {

    public record Result(int stage, long stageStartedAt) {}

    private AgingStages() {}

    public static Result advance(long stageStartedAt, long now,
                                 IntUnaryOperator durationOfStage,
                                 int startStage, int stageCount) {
        if (stageStartedAt == FermentationProgress.NOT_STARTED) {
            return new Result(startStage, stageStartedAt);
        }

        int stage = startStage;
        long start = stageStartedAt;

        // bounded by stageCount, so a zero or negative duration can never spin
        while (stage < stageCount - 1) {
            int duration = durationOfStage.applyAsInt(stage);
            if (FermentationProgress.progress(start, now, duration) < 1f) break;
            if (duration > 0) start += duration;   // carry the remainder forward
            stage++;
        }

        return new Result(stage, start);
    }
}
```

Delegating the "is this stage done" question to Phase 1's `FermentationProgress.progress(...) >= 1f` means the backwards-clock clamping, the `NOT_STARTED` guard and the `Long.MAX_VALUE` saturation are all inherited rather than rewritten. Do not duplicate that arithmetic.

The `stage < stageCount - 1` bound is what makes a zero-duration stage terminate instead of looping forever.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.createbrewery.AgingStagesTest"`
Expected: PASS, 9 tests. Also re-run Phase 1's suite — `./gradlew test` — to confirm nothing regressed.

- [ ] **Step 5: Add the config key**

In Phase 1's `Config`, beside `FERMENTATION_DURATION_MULTIPLIER`:

```java
AGING_DURATION_MULTIPLIER = builder
    .comment("Scales all cask aging times. 1.0 = 4 in-game days to vintage, 16 to reserve.")
    .defineInRange("agingDurationMultiplier", 1.0, 0.01, 100.0);
```

Separate from the fermentation key because sixteen days is the number players will actually want to tune.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/createbrewery/recipe/AgingStages.java src/test/java/com/createbrewery/AgingStagesTest.java src/main/java/com/createbrewery/Config.java
git commit -m "feat: add cumulative aging stage math with offline catch-up"
```

---

## Task 5: The Cask

**Files:**
- Create: `src/main/java/com/createbrewery/block/CaskBlock.java`
- Create: `src/main/java/com/createbrewery/block/entity/CaskBlockEntity.java`
- Create: `src/test/java/com/createbrewery/CaskGameTests.java`
- Modify: `src/main/java/com/createbrewery/ModBlocks.java`, `ModBlockEntities.java`

**Interfaces:**
- Consumes: `AgingStages`, `FermentationProgress`, `ModRecipeTypes.AGING`, `AgingRecipe`, `Config.AGING_DURATION_MULTIPLIER`
- Produces: `ModBlocks.CASK`, `CaskBlockEntity.getProgress()` (progress within the *current* stage, `[0,1]`), `CaskBlockEntity.getStageFluid()`

### ⚠ Read this before writing the block entity

Copy Phase 1's `FermenterBlockEntity` for its **structure** — `SmartBlockEntity`, `addBehaviours`, `SmartFluidTankBehaviour`, `IHaveGoggleInformation`, the `write`/`read` NBT pattern, the `ResourceLocation.tryParse` guard. Those signatures were all verified during Phase 1.

**Do not copy its tank semantics.** Three differences, each deliberate:

| | Fermenter | Cask |
|---|---|---|
| Tanks | separate input + output | **one** tank, converted in place |
| Matching | fluid **and** amount | **fluid identity only** — the amount is never compared |
| `inputStillValid` on partial drain | resets the batch | **must not reset** — Review Focus #1 |
| Insertion | always allowed | forbidden while aging — Review Focus #3 |

Phase 1's `inputStillValid` resets when the tank amount drops below the recipe's ingredient amount, which is correct there because the Fermenter consumes that fluid. In a Cask it would mean **drawing one bottle of wine destroys sixteen days of aging**.

Treating the amount as a *minimum to match on* is the tempting half-fix and it is also wrong: a cask drained to 100 mB would stop matching and freeze, and since insertion is forbidden mid-aging the player could not top it back up — the cask would be stuck until emptied. **Match on fluid identity and ignore the amount entirely.** An aging recipe's `amount` exists only so JEI has something to render.

- [ ] **Step 1: Write `CaskBlockEntity`**

Behaviour contract:

- **One** `SmartFluidTankBehaviour`, 1500 mB, `allowInsertion()` and `allowExtraction()` both — the insertion *rule* is enforced in code, not by the behaviour flag, because it depends on state.
- Persisted state: `stageStartedAt` (long, `FermentationProgress.NOT_STARTED` when idle) and the active aging recipe id (`ResourceLocation`, nullable).
- `tick()` (server side only):
  1. If the tank is empty → reset `stageStartedAt` to `NOT_STARTED`, clear the recipe id, return.
  2. If `stageStartedAt == NOT_STARTED` and the tank holds a fluid with a matching `AgingRecipe` → stamp `stageStartedAt = level.getGameTime()`, store the recipe id, `setChanged()`, `sendData()`.
  3. Otherwise resolve the current recipe. **If it no longer resolves, drop to idle without crashing** — Review Focus #5, same `tryParse` + `byKey` guard Phase 1 uses.
  4. Run the catch-up: repeatedly, while the current stage's scaled duration has elapsed and a matching `AgingRecipe` exists for the *current* fluid, replace the tank's fluid with the recipe result **preserving the existing amount**, advance `stageStartedAt += scaledDuration`, and look up the next recipe. Terminate when no aging recipe matches the new fluid — that is the terminal tier, expressed by data rather than a flag.

     **Cap the walk at 16 iterations** (Review Focus #8). `AgingStages` is bounded by its `stageCount` argument, but the block entity discovers its chain by following recipe results, so a datapack cycle (`wine → vintage → wine`) would otherwise spin the server on every tick. One line.

     **On reaching the terminal tier, set `stageStartedAt = NOT_STARTED`.** This is what re-opens insertion on a finished cask (see the insertion rule below) and it matches the spec's tank table. Without it, a cask of reserve wine refuses fills forever.
  5. When a stage is crossed, `setChanged()` and `sendData()`.

  Implement step 4 using `AgingStages.advance(...)` where the stage chain is discovered by following recipe results; the pure class owns the arithmetic and the block entity owns the recipe lookups.

- **Scaled duration:** `recipe.getProcessingDuration() * Config.AGING_DURATION_MULTIPLIER.get()`, clamped to at least 1, exactly as Phase 1's `scaledDuration` does for fermentation.
- **Extraction:** never touches `stageStartedAt`, and never compares amounts. A partial drain is a no-op as far as timing is concerned, down to the last millibucket.
- **Insertion rule: refuse all insertion while `stageStartedAt != NOT_STARTED`.** Because step 4 clears the stamp at the terminal tier, this permits filling an empty cask and topping up a finished one, and forbids only the case that matters — diluting a twelve-day cask with fresh wine to get reserve for free. That is all the automation needs.

> `ponytail:` one tank, one batch, no parallel aging slots. If throughput becomes the bottleneck, the answer is more casks — that is the intended gameplay shape of a cellar — not a multiblock.

- [ ] **Step 2: Write `CaskBlock` and register both**

`CaskBlock extends Block`, implements `IBE<CaskBlockEntity>`, `noOcclusion`, a barrel-on-its-side `VoxelShape`. Register the block in `ModBlocks` and the block entity type in `ModBlockEntities`, copying Phase 1's `FERMENTER` entries.

**No kinetic block entity, no stress impact, no shaft connectivity.** The Cask takes no rotational force, for the same three reasons Phase 1 gave for the Fermenter.

- [ ] **Step 3: Write the GameTests**

As in Phase 1, backdate `stageStartedAt` rather than ticking 24000 times, so these run in milliseconds. Reuse the `createbrewery:platform` structure Phase 1 added.

**Put this file wherever Phase 1's `FermenterGameTests` actually lives.** `src/test/java` may not be on `runGameTestServer`'s classpath, and a run that discovers zero tests still exits green — so the expected result below is a *test count*, not "BUILD SUCCESSFUL".

```java
package com.createbrewery;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(CreateBrewery.MOD_ID)
public class CaskGameTests {

    @GameTest(template = "createbrewery:platform")
    public static void wineBecomesVintageAfterFourDays(GameTestHelper helper) {
        // fill with wine, backdate stageStartedAt by 4 days, tick,
        // assert the tank holds createbrewery:vintage_wine at the SAME amount
    }

    // Review Focus #2 at the integration level
    @GameTest(template = "createbrewery:platform")
    public static void twentyDaysOfflineReachesReserve(GameTestHelper helper) {
        // fill with wine, backdate stageStartedAt by 20 days, tick ONCE,
        // assert the tank holds createbrewery:reserve_wine
    }

    // Review Focus #1 — the worst bug this phase could ship
    @GameTest(template = "createbrewery:platform")
    public static void partialDrainDoesNotResetAging(GameTestHelper helper) {
        // fill with 1500mB wine, backdate by 3 days, drain down to ~100mB --
        // BELOW the aging recipe's 250mB, which is the case a "minimum to match on"
        // implementation would freeze on. Tick.
        // Assert stageStartedAt is UNCHANGED and the remaining fluid is still wine;
        // then backdate the remaining day and assert those 100mB still become vintage.
    }

    @GameTest(template = "createbrewery:platform")
    public static void qualityComponentIsOnTheBottle(GameTestHelper helper) {
        // read stack.get(DataComponents.CUSTOM_DATA) on each of the three wine bottles
        // and assert brewery.beer_quality is 1, 2, 3. A *default* component may not
        // show in F3+H or /data get, so assert it here rather than eyeballing a tooltip.
    }

    @GameTest(template = "createbrewery:platform")
    public static void cyclicAgingChainDoesNotHang(GameTestHelper helper) {
        // Review Focus #8: with a datapack-style cycle the walk must stop at the cap
        // rather than spinning. If a cycle cannot be injected in a GameTest, assert
        // instead that the walk never exceeds 16 iterations in one tick.
    }

    // Review Focus #3
    @GameTest(template = "createbrewery:platform")
    public static void insertionRefusedWhileAging(GameTestHelper helper) {
        // fill with wine, backdate by 3 days, attempt to fill with more wine,
        // assert the fill returns 0 and the tank amount is unchanged
    }

    // Review Focus #4
    @GameTest(template = "createbrewery:platform")
    public static void reserveWineIsTerminal(GameTestHelper helper) {
        // fill with reserve_wine, backdate by 100 days, tick,
        // assert the fluid is still reserve_wine and nothing threw
    }

    // Review Focus #5
    @GameTest(template = "createbrewery:platform")
    public static void survivesUnresolvableSavedRecipeId(GameTestHelper helper) {
        // write NBT naming "createbrewery:no_such_aging", load the BE, tick,
        // assert no exception and the cask is idle rather than crashing the chunk
    }
}
```

- [ ] **Step 4: Run the GameTests**

Run: `./gradlew runGameTestServer`
Expected: **12 tests reported** — eight Cask tests plus Phase 1's four Fermenter tests — and all passing. Check the count, not just the exit code: a run that discovers nothing also exits green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/block/ src/main/java/com/createbrewery/block/entity/CaskBlockEntity.java src/main/java/com/createbrewery/ModBlocks.java src/main/java/com/createbrewery/ModBlockEntities.java src/test/java/com/createbrewery/CaskGameTests.java
git commit -m "feat: add Cask block with world-time-driven wine aging"
```

---

## Task 6: Cask progress display

**Files:**
- Modify: `src/main/java/com/createbrewery/block/entity/CaskBlockEntity.java`
- Modify: `src/main/resources/assets/createbrewery/lang/en_us.json`

**Interfaces:**
- Consumes: `CaskBlockEntity.getProgress()`
- Produces: nothing new

- [ ] **Step 1: Implement `addToGoggleTooltip`**

Copy Phase 1's `FermenterBlockEntity.addToGoggleTooltip` structure, including the `containedFluidTooltip(...)` default from `IHaveGoggleInformation` (verified in Phase 1). Show:

- the current tier by name, and **"Fully aged"** at the terminal tier rather than a 100% bar, because a player staring at a permanent "100%" will assume it is stuck
- percentage through the *current* stage, and days remaining in it
- the tank contents

Only **one** `containedFluidTooltip` call — the Cask has one tank.

- [ ] **Step 2: Add the lang entries**

```json
{
  "createbrewery.goggles.cask.idle": "Empty",
  "createbrewery.goggles.cask.aging": "Aging: %s%%",
  "createbrewery.goggles.cask.remaining": "%s days to next stage",
  "createbrewery.goggles.cask.mature": "Fully aged"
}
```

Plus the block, item and fluid lang keys for everything registered in Tasks 1, 2 and 5.

- [ ] **Step 3: Verify in game**

Run: `./gradlew runClient`
Expected: goggles on an aging Cask show a climbing percentage and a day countdown; a cask of reserve wine shows "Fully aged". Jade picks the same data up automatically.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/createbrewery/block/entity/CaskBlockEntity.java src/main/resources/assets/createbrewery/lang/en_us.json
git commit -m "feat: show aging progress and tier in the goggle overlay"
```

---

## Task 7: The wine and cider recipes

**Files:**
- Modify: `src/main/java/com/createbrewery/data/ModRecipeProvider.java`

**Interfaces:**
- Consumes: `ModItems.*`, `ModFluids.*`, `ModRecipeTypes.FERMENTING`, `ModRecipeTypes.AGING`
- Produces: generated JSON under `src/generated/resources/data/createbrewery/recipe/`

Builder methods are the ones Phase 1 verified: `withItemIngredients`, `withFluidIngredients`, `withItemOutputs`, `withFluidOutputs`, `duration(int)`, `requiresHeat(HeatCondition)`, `build(RecipeOutput)`. `DAY = 24000`.

- [ ] **Step 1: Pressing grapes — `create:compacting`, Mechanical Press over a Basin**

```java
// grapes x4 -> 250mB grape must
create.compacting(CreateBrewery.ID("pressing_grapes"))
    .withItemIngredients(Ingredient.of(ModItems.GRAPES.get()), ..., /* x4 */)
    .withFluidOutputs(new FluidStack(ModFluids.GRAPE_MUST.get(), 250))
    .requiresHeat(HeatCondition.NONE)
    .duration(200)
    .build(output);
```

This is the wine press, and it is the first use of `create:compacting` in the mod. Items-only ingredients are confirmed against Create's shipped `compacting/ice.json`; fluid results from compacting are confirmed against `createfood`'s shipped compacting recipes. **The combination has no shipped precedent** — if the Basin refuses it in practice, add a small water ingredient (50 mB) to match `createfood`'s shape exactly. That fallback costs one line; note it in the spec's Risks table if used.

- [ ] **Step 2: Pressing apples — the standalone fallback only**

Same `create:compacting` shape, apples → `createbrewery:apple_juice`. Emit it with a NeoForge condition so it loads **only when `createfood` is absent**, so JEI does not show two ways to press an apple in the common case:

```json
"neoforge:conditions": [
  { "type": "neoforge:not",
    "value": { "type": "neoforge:mod_loaded", "modid": "createfood" } }
]
```

If the `neoforge:not` wrapper proves awkward in datagen, ship it unconditionally and accept the duplicate JEI entry — this is cosmetic, not functional.

- [ ] **Step 3: Fermenting — wine**

```java
// grape must + yeast -> wine, one in-game day
new ProcessingRecipeBuilder<>(FermentingRecipe::new, CreateBrewery.ID("fermenting_wine"))
    .withItemIngredients(Ingredient.of(ModItems.YEAST.get()))
    .withFluidIngredients(SizedFluidIngredient.of(ModFluids.GRAPE_MUST.get(), 500))
    .withFluidOutputs(new FluidStack(ModFluids.WINE.get(), 500))
    .duration(DAY)
    .build(output);
```

Same Fermenter, same recipe type, same yeast as Phase 1. No new machinery.

- [ ] **Step 4: Fermenting — cider, five feedstocks, one output**

One recipe per `c:` fluid tag, **all producing `createbrewery:cider`**:

`#c:apple_juice`, `#c:berry_juice`, `#c:glow_berry_juice`, `#c:sugar_cane_juice`, `#c:chorus_fruit_juice` → 500 mB `cider`, `duration(DAY / 2)`.

- **Ingredients are fluid tags, never fluid ids.** `SizedFluidIngredient.of(<TagKey<Fluid>>, 500)`. No recipe in this phase may name a `createfood:` fluid.
- The **apple** one loads unconditionally — our own `createbrewery:apple_juice` is tagged into `c:apple_juice` (Task 8), so the cider chain works standalone.
- The other **four** carry `{"type":"neoforge:mod_loaded","modid":"createfood"}` so JEI does not show recipes with an empty ingredient slot when the tag is empty. `neoforge:mod_loaded` is proven — `createfood`'s own shipped recipes use it. A `neoforge:not` + `neoforge:tag_empty` gate on the fluid registry would be more correct (it would also catch Hearth & Harvest's juices) but that variant is unverified; treat it as an optional refinement.
- Cider ferments in **half** the time beer and wine do. That, plus feedstock breadth and the absence of tiers, is the whole mechanical distinction from wine.

- [ ] **Step 5: Aging — the two cask stages**

```java
// wine -> vintage wine, 4 in-game days
new ProcessingRecipeBuilder<>(AgingRecipe::new, CreateBrewery.ID("aging_vintage"))
    .withFluidIngredients(SizedFluidIngredient.of(ModFluids.WINE.get(), 250))
    .withFluidOutputs(new FluidStack(ModFluids.VINTAGE_WINE.get(), 250))
    .duration(4 * DAY)
    .build(output);

// vintage wine -> reserve wine, 12 more in-game days
new ProcessingRecipeBuilder<>(AgingRecipe::new, CreateBrewery.ID("aging_reserve"))
    .withFluidIngredients(SizedFluidIngredient.of(ModFluids.VINTAGE_WINE.get(), 250))
    .withFluidOutputs(new FluidStack(ModFluids.RESERVE_WINE.get(), 250))
    .duration(12 * DAY)
    .build(output);
```

The `250` amounts are **display metadata only** — the Cask matches on fluid identity, never on amount, and converts whatever it holds while preserving that amount (Task 5). They exist so JEI has a number to render. **`reserve_wine` gets no aging recipe**; that absence is how the terminal tier is expressed. Do not add a sentinel recipe.

- [ ] **Step 6: Bottling and canning**

`create:filling` via the Spout, copying Phase 1's `bottling_beer`:

- glass bottle + 250 mB `wine` → `wine_bottle`
- glass bottle + 250 mB `vintage_wine` → `vintage_wine_bottle`
- glass bottle + 250 mB `reserve_wine` → `reserve_wine_bottle`
- glass bottle + 250 mB `cider` → `cider_bottle`
- Phase 1's `empty_can` + 250 mB `cider` → `cider_can`

No new Press recipe — Phase 1 already presses iron sheets into `empty_can`.

- [ ] **Step 7: Run datagen and check the emitted JSON**

Run: `./gradlew runData`
Expected: JSON under `src/generated/resources/data/createbrewery/recipe/`. **Open `pressing_grapes.json` and one cider recipe and confirm:**
- item and fluid outputs both sit in a single `results` array, fluid carrying `amount` (Create 6 format — no `fluid_results` key anywhere)
- the fluid **tag** ingredient serialized in a form the game accepts. The only shape observed in a shipped jar is `createfood`'s `{"type":"fluid_tag","amount":250,"fluid_tag":"c:water"}`; Create's own jar contains no fluid-tag ingredient, so whatever `SizedFluidIngredient.of(tag, amount)` emits must be checked here rather than assumed.

- [ ] **Step 8: Verify both chains in game**

Run: `./gradlew runClient`
Expected: JEI shows pressing, fermenting, aging and bottling for both chains; a creative line takes grapes to a wine bottle and apples to a cider bottle.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/createbrewery/data/ src/generated/
git commit -m "feat: add the wine and cider recipe chains"
```

---

## Task 8: Tags and the selling_bin data map

**Files:**
- Modify: `src/main/java/com/createbrewery/data/ModTagsProvider.java`
- Create: `src/main/resources/data/selling_bin/data_maps/item/selling_bin_value.json`

**Interfaces:**
- Consumes: `ModFluids.APPLE_JUICE`, the drink items
- Produces: nothing other tasks depend on

- [ ] **Step 1: Tag our apple juice into `c:apple_juice`**

Both the source and flowing fluids, matching how `createfood` tags its own. **This is what makes the cider chain work standalone and interoperate when `createfood` is present** — it is the load-bearing half of the whole dependency posture, not a nicety.

Do **not** add `grape_must`, `cider`, or any wine to a `c:` tag — no community convention exists for them, and inventing one unilaterally helps nobody.

- [ ] **Step 2: Item tags**

- `createbrewery:wine` — the three wine bottles
- `createbrewery:cider` — cider bottle and can
- Grapes into the crop/compostable tags (already done in Task 2 — confirm, do not duplicate)

- [ ] **Step 3: The optional `selling_bin` data map**

```json
{
  "values": {
    "#createbrewery:wine": {
      "base_value": 200,
      "processors": [
        { "type": "selling_bin:beer_quality_processor",
          "quality_multipliers": { "1": 1.5, "2": 3.0, "3": 5.0 } }
      ]
    },
    "#createbrewery:cider": { "base_value": 60 }
  }
}
```

No `neoforge:conditions` block: a data map file for a type no loaded mod registers is simply never read, so the gate is unnecessary. (A top-level condition on a data map file is also unverified — if you add one and it errors, this is why.)

**Use `beer_quality_processor`, not `wine_age_processor`.** Disassembly of `selling_bin-1.6` shows `WineAgeProcessor.addValue` reads `net.satisfy.vinery.core.registry.DataComponentRegistry.WINE_YEAR` and calls `net.satisfy.vinery.core.util.WineYears.getWineAgeYears(...)` — it is hard-linked to Vinery, which is not installed, and there is no generic key we could set. `BeerQualityProcessor` by contrast reads vanilla `DataComponents.CUSTOM_DATA` and looks up the plain string key `"brewery.beer_quality"` as an int, which is exactly what Task 1 Step 4 writes onto the bottles.

Matching Phase 1's posture: **ship it, do not promise it.** Nothing in Phase 2 depends on this file working, and it is not tested against the real mod.

- [ ] **Step 4: Verify**

Run: `./gradlew runClient`
Expected: `createbrewery:apple_juice` appears in the `/tag` output for `c:apple_juice`. The quality component is asserted by the Task 5 GameTest, not here.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/data/ src/main/resources/data/selling_bin/ src/generated/
git commit -m "feat: tag apple juice into c:apple_juice and add optional selling_bin values"
```

---

## Task 9: JEI category for aging

**Files:**
- Create: `src/main/java/com/createbrewery/compat/jei/AgingCategory.java`
- Modify: `src/main/java/com/createbrewery/compat/jei/CreateBreweryJEI.java`

**Interfaces:**
- Consumes: `AgingRecipe`, `ModRecipeTypes.AGING`
- Produces: nothing other tasks depend on

Pressing, filling and fermenting are already categories — compacting and filling are Create's own and appear automatically once Task 7's recipes exist, and cider reuses Phase 1's fermenting category with no change. Only aging needs a new one.

- [ ] **Step 1: Write the category**

Copy Phase 1's `FermentingCategory` and strip the item slot — aging has no items at all. Input fluid, arrow, output fluid.

- [ ] **Step 2: Show days, not ticks**

`96000` ticks means nothing; "12 days" does. Phase 1's fermenting category already does this conversion — reuse it.

- [ ] **Step 3: Register the category**

Add it to Phase 1's existing `@JeiPlugin` class, feeding it every `AgingRecipe` from the recipe manager.

- [ ] **Step 4: Verify**

Run: `./gradlew runClient`
Expected: JEI shows an Aging category; looking up reserve wine walks back through aging → aging → fermenting → compacting → grapes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/compat/jei/
git commit -m "feat: add JEI category for cask aging"
```

---

## Task 10: Ponder scene for the Cask

**Files:**
- Modify: `src/main/java/com/createbrewery/ponder/ModPonderScenes.java`
- Create: the cask scene

**Interfaces:**
- Consumes: `ModBlocks.CASK`
- Produces: nothing

Use whatever Ponder API Phase 1's Task 10 settled on — it was resolved there against the real Create 6 dependency. Do not re-derive it.

- [ ] **Step 1: Scene — the Cask**

Three things a player will get wrong, in this order:

1. **It needs no shaft.** Same expectation the Fermenter scene corrects.
2. **The fluid inside changes name as it ages.** Show wine becoming vintage becoming reserve in the same tank, because "why is my tank full of a different fluid" is otherwise a bug report.
3. **Drawing some off does not reset the rest.** Show a pipe pulling a portion out mid-aging while the remainder keeps its countdown. This is the interaction that makes tier selection automatable and it is not guessable.

- [ ] **Step 2: Verify**

Run: `./gradlew runClient`
Expected: `W` on the Cask in JEI opens the scene; it plays through with no errors.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/createbrewery/ponder/
git commit -m "feat: add Ponder scene for the cask"
```

---

## Definition of done

- [ ] `./gradlew build` succeeds
- [ ] `./gradlew test` — `AgingStagesTest` (9 cases) and Phase 1's `FermentationProgressTest` all pass
- [ ] `./gradlew runGameTestServer` — **12 tests reported and passing** (eight Cask, four Fermenter). Check the count, not the exit code.
- [ ] Grapes, barley seeds and hop cones can all be planted on farmland **by hand in survival**
- [ ] A creative line takes grapes → press → fermenter → cask → Spout and yields a reserve wine bottle with no manual intervention
- [ ] A creative line takes apples (or any `c:` juice) → fermenter → Spout and yields a cider bottle
- [ ] A cask filled, chunk unloaded, and revisited **twenty** in-game days later holds reserve wine — not vintage. This is the assertion the whole aging design exists to buy.
- [ ] Drawing 250 mB off a mid-aging cask leaves the remainder's countdown untouched
- [ ] **The mod loads and the cider chain works with `createfood` absent** — the assertion that proves the dependency posture
- [ ] With `createfood` present, `createfood:apple_juice` pipes straight into the Fermenter and makes cider, with no recipe change
- [ ] JEI shows pressing, fermenting, aging and bottling for both chains; the Cask Ponder scene plays
- [ ] No recipe anywhere in the mod names a `createfood:` fluid id — `grep -r "createfood:" src/generated/` returns nothing
- [ ] The mod loads in the target pack (`Create Vanilla`) without conflicts, and `farmersdelight:apple_cider` is untouched
