# Create Brewery Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a playable NeoForge 1.21.1 addon providing a complete grain-to-bottle beer chain that a player automates using Create's existing machinery, plus one new block (the Fermenter) for the timed step Create cannot express.

**Architecture:** Every brewing stage except fermentation maps onto a machine Create already ships (Basin, Blaze Burner, Millstone, Spout, Press). The Fermenter is a passive `SmartBlockEntity` that stores a world-time stamp and computes progress from `level.getGameTime() - startedAt`, so batches survive chunk unload without a chunkloader. It exposes standard `IFluidHandler`/`IItemHandler` capabilities, so Create pipes and funnels automate it exactly like a Basin.

**Tech Stack:** Java 21 · NeoForge 21.1.2xx · Create 6.0.10 · Registrate · Ponder · Flywheel · Gradle (Kotlin DSL) · JUnit 5 + NeoForge GameTest

**Spec:** `docs/superpowers/specs/2026-09-22-create-brewery-phase1-design.md`

---

## Global Constraints

Every task's requirements implicitly include these. Values copied verbatim from the spec.

- **Mod id:** `createbrewery`. All registry names are namespaced to it.
- **Versions:** `minecraft_version = 1.21.1`, `create_version = 6.0.10-280`, `ponder_version = 1.0.82`, `flywheel_version = 1.0.6`, `registrate_version = MC1.21-1.3.0+67`
- **Dependency ranges** in `neoforge.mods.toml`: neoforge `[21.1.228,)`, minecraft `[1.21.1,1.22)`, create `[6.0.10,6.1.0)`
- **Repositories:** `https://maven.createmod.net` (Create, Ponder, Flywheel) and `https://maven.ithundxr.dev/snapshots` (Registrate)
- **Recipe JSON format:** Create 6 unified item and fluid outputs into a single `results` array. The pre-6 `fluid_results` key does not exist. Fluid entries carry `amount` in mB; item entries do not.
- **The Fermenter requires no rotational force** and has no stress impact. Fermentation progress is derived from world time, never from a per-tick counter. This is deliberate — see the spec's rationale.
- **No worldgen or structure edits.** Acquisition is grass drop tables, villager trades, and chest loot only.
- **Registrate's builder API could not be verified from disk** (Registrate is not bundled in the Create jar — confirmed, 0 classes). Every Registrate builder chain in this plan is written to the library's documented shape and **must be confirmed against the resolved dependency in the IDE during Task 1**. If a chain differs, fix it there and carry the correction forward; do not fight the compiler by guessing.
- **Java 21** toolchain (`JAVA_HOME` is already `C:\Program Files\Java\jdk-21`).
- Commit after every task. Conventional commit prefixes (`feat:`, `test:`, `chore:`).

---

## Review Focus

Failure modes the spec implies but which no obvious happy-path test exercises. Each has a test assigned to the task that owns the code.

1. **World time moves backwards** (`/time set`, backup restore) — `startedAt` ends up in the future, `elapsed` goes negative. Progress must clamp to 0, never wrap to complete or produce NaN. *Covered in Task 5.*
2. **Saved recipe id no longer resolves** (datapack edited, recipe renamed, addon removed) — loading a Fermenter whose `Recipe` NBT names a missing recipe must not crash the chunk. *Covered in Task 5.*
3. **Output tank full at completion** — a finished batch with nowhere to go must be held, not silently deleted. Losing a day-long batch to a backed-up pipe is the single worst bug this mod could ship. *Covered in Task 5.*
4. **Enormous elapsed time** — a player returning after thousands of in-game days. `long` arithmetic and the float division must saturate at 1.0, not overflow or lose precision into garbage. *Covered in Task 5.*
5. **Input fluid removed mid-fermentation** — player pipes the wort back out halfway through. The batch must invalidate cleanly rather than producing beer from nothing. *Covered in Task 5.*

---

## File Structure

```
createbrewery/
├── build.gradle.kts                    Gradle + Create/Registrate deps
├── gradle.properties                   pinned versions
├── src/main/java/com/createbrewery/
│   ├── CreateBrewery.java              mod entrypoint, Registrate instance
│   ├── ModFluids.java                  wort, hopped_wort, beer
│   ├── ModItems.java                   all items
│   ├── ModBlocks.java                  fermenter, barley crop, hops crop
│   ├── ModBlockEntities.java           fermenter BE type
│   ├── ModRecipeTypes.java             createbrewery:fermenting
│   ├── Config.java                     fermentationDurationMultiplier, enableCans
│   ├── block/
│   │   ├── FermenterBlock.java         block shape, interaction
│   │   ├── BarleyCropBlock.java
│   │   └── HopsCropBlock.java
│   ├── block/entity/
│   │   └── FermenterBlockEntity.java   tanks, timing, goggle tooltip
│   ├── recipe/
│   │   ├── FermentingRecipe.java       extends ProcessingRecipe
│   │   └── FermentationProgress.java   PURE progress math — unit tested
│   ├── data/                           datagen entrypoints
│   └── compat/jei/                     fermenting category
└── src/test/java/com/createbrewery/
    └── FermentationProgressTest.java   plain JUnit, no Minecraft needed
```

**Key decomposition decision:** the progress arithmetic lives in `FermentationProgress`, a pure static class with no Minecraft types. This makes the mod's one genuinely tricky piece of logic testable with plain JUnit in milliseconds, rather than only through a GameTest that must boot a server. All five Review Focus items above except #2 and #3 are unit-testable because of this split.

---

## Task 1: Project scaffold and toolchain verification

Resolves the plan's largest unknown — the Gradle plugin choice — before any other code exists.

**Files:**
- Create: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/wrapper/*`
- Create: `src/main/resources/META-INF/neoforge.mods.toml`
- Create: `src/main/java/com/createbrewery/CreateBrewery.java`

**Interfaces:**
- Consumes: nothing
- Produces: `CreateBrewery.MOD_ID` (String `"createbrewery"`), `CreateBrewery.REGISTRATE` (a `CreateRegistrate`), `CreateBrewery.ID(String path)` returning `ResourceLocation`

- [ ] **Step 1: Start from the NeoForge ModDevGradle MDK for 1.21.1**

Download the MDK from `https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle` into the repo root. Keep `gradlew`, `gradlew.bat`, `gradle/wrapper/`, `settings.gradle.kts`, `build.gradle.kts`.

> If Create 6 turns out to require NeoGradle instead, switch now and note it in the spec's Risks table. ModDevGradle is the current NeoForge standard and is the correct first attempt.

- [ ] **Step 2: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false

minecraft_version=1.21.1
neo_version=21.1.228
mod_id=createbrewery
mod_name=Create Brewery
mod_version=0.1.0
mod_group_id=com.createbrewery

create_version=6.0.10-280
ponder_version=1.0.82
flywheel_version=1.0.6
registrate_version=MC1.21-1.3.0+67
```

- [ ] **Step 3: Add repositories and dependencies to `build.gradle.kts`**

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

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test { useJUnitPlatform() }
```

`:slim` with `isTransitive = false` means Create's own transitive dependencies are NOT pulled in. If a `NoClassDefFoundError` appears at runtime for something Create depends on (Catnip, Ponder, Flywheel), declare it explicitly here rather than removing `:slim`.

- [ ] **Step 4: Write `neoforge.mods.toml`**

```toml
modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"

[[mods]]
modId = "createbrewery"
version = "${mod_version}"
displayName = "Create Brewery"
description = "Realistic, fully automatable beer production built on Create."

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

- [ ] **Step 5: Write the mod entrypoint**

```java
package com.createbrewery;

import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(CreateBrewery.MOD_ID)
public class CreateBrewery {
    public static final String MOD_ID = "createbrewery";
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    public CreateBrewery(IEventBus modEventBus) {
        REGISTRATE.registerEventListeners(modEventBus);
    }

    public static ResourceLocation ID(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
```

Confirm `CreateRegistrate.create(String)` and `registerEventListeners(IEventBus)` against the resolved Create jar in the IDE. This is the Global Constraints check — do it here, once.

- [ ] **Step 6: Verify the build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. Dependency resolution succeeds from both mavens.

- [ ] **Step 7: Verify the game launches with Create present**

Run: `./gradlew runClient`
Expected: reaches the main menu. Mods list shows both "Create" and "Create Brewery". No registry errors in the log.

- [ ] **Step 8: Commit**

```bash
git init
git add .
git commit -m "chore: scaffold NeoForge 1.21.1 mod with Create and Registrate"
```

---

## Task 2: Fluids

**Files:**
- Create: `src/main/java/com/createbrewery/ModFluids.java`
- Modify: `src/main/java/com/createbrewery/CreateBrewery.java` (call `ModFluids.register()`)

**Interfaces:**
- Consumes: `CreateBrewery.REGISTRATE`
- Produces: `ModFluids.WORT`, `ModFluids.HOPPED_WORT`, `ModFluids.BEER` — each a `FluidEntry<VirtualFluid>` exposing `.getSource()` and `.getBucket()`

- [ ] **Step 1: Write the fluid registry**

```java
package com.createbrewery;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.FluidEntry;
import com.simibubi.create.content.fluids.VirtualFluid;

public class ModFluids {
    private static final CreateRegistrate REGISTRATE = CreateBrewery.REGISTRATE;

    public static final FluidEntry<VirtualFluid> WORT =
        REGISTRATE.virtualFluid("wort").register();

    public static final FluidEntry<VirtualFluid> HOPPED_WORT =
        REGISTRATE.virtualFluid("hopped_wort").register();

    public static final FluidEntry<VirtualFluid> BEER =
        REGISTRATE.virtualFluid("beer").register();

    public static void register() {}
}
```

`virtualFluid` is Create's helper for fluids that exist only in tanks and pipes (no world source block), which is correct for all three — you do not want lakes of wort. Confirm the helper name against `CreateRegistrate` in the IDE.

- [ ] **Step 2: Call it from the mod constructor**

In `CreateBrewery`'s constructor, after `registerEventListeners`:

```java
ModFluids.register();
```

- [ ] **Step 3: Add fluid textures**

Create `src/main/resources/assets/createbrewery/textures/fluid/wort_still.png`, `wort_flow.png`, and the same pair for `hopped_wort` and `beer`. 16x16 and 16x512 respectively. Amber for wort, deeper amber for hopped wort, brown for beer.

- [ ] **Step 4: Verify in game**

Run: `./gradlew runClient`
Expected: the three fluids are registered and resolvable, and their textures load without missing-texture magenta.

> **Corrected 2026-09-23.** This step originally said to run `/give @s createbrewery:wort_bucket`. That command can never work: `virtualFluid(...)` registers no bucket item (see the spec). Verify the fluids by placing them in a Create tank or by checking the registry, not by giving yourself a bucket.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/ModFluids.java src/main/java/com/createbrewery/CreateBrewery.java src/main/resources/assets/createbrewery/textures/fluid/
git commit -m "feat: add wort, hopped wort and beer fluids"
```

---

## Task 3: Items and crops

**Files:**
- Create: `src/main/java/com/createbrewery/ModItems.java`
- Create: `src/main/java/com/createbrewery/ModBlocks.java`
- Create: `src/main/java/com/createbrewery/block/BarleyCropBlock.java`
- Create: `src/main/java/com/createbrewery/block/HopsCropBlock.java`

**Interfaces:**
- Consumes: `CreateBrewery.REGISTRATE`
- Produces: `ModItems.BARLEY`, `BARLEY_SEEDS`, `GREEN_MALT`, `MALT`, `GRIST`, `SPENT_GRAIN`, `HOP_CONES`, `YEAST`, `BEER_BOTTLE`, `EMPTY_CAN`, `SEALED_CAN` (each `ItemEntry<Item>`); `ModBlocks.BARLEY_CROP`, `ModBlocks.HOPS_CROP` (each `BlockEntry<? extends CropBlock>`)

- [ ] **Step 1: Write the crop blocks**

Both are ordinary 7-stage crops. `BarleyCropBlock`:

```java
package com.createbrewery.block;

import com.createbrewery.ModItems;
import net.minecraft.world.item.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BarleyCropBlock extends CropBlock {
    public BarleyCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.BARLEY_SEEDS.get();
    }
}
```

`HopsCropBlock` is identical but returns `ModItems.HOP_CONES.get()` — hops are propagated from cones rather than a separate seed item, which keeps the item count down.

```java
package com.createbrewery.block;

import com.createbrewery.ModItems;
import net.minecraft.world.item.ItemLike;
import net.minecraft.world.level.block.CropBlock;

public class HopsCropBlock extends CropBlock {
    public HopsCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.HOP_CONES.get();
    }
}
```

- [ ] **Step 2: Write the item registry**

```java
package com.createbrewery;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

public class ModItems {
    private static final CreateRegistrate REGISTRATE = CreateBrewery.REGISTRATE;

    public static final ItemEntry<Item> BARLEY = REGISTRATE.item("barley", Item::new).register();
    public static final ItemEntry<Item> BARLEY_SEEDS = REGISTRATE.item("barley_seeds", Item::new).register();
    public static final ItemEntry<Item> GREEN_MALT = REGISTRATE.item("green_malt", Item::new).register();
    public static final ItemEntry<Item> MALT = REGISTRATE.item("malt", Item::new).register();
    public static final ItemEntry<Item> GRIST = REGISTRATE.item("grist", Item::new).register();
    public static final ItemEntry<Item> SPENT_GRAIN = REGISTRATE.item("spent_grain", Item::new).register();
    public static final ItemEntry<Item> HOP_CONES = REGISTRATE.item("hop_cones", Item::new).register();
    public static final ItemEntry<Item> YEAST = REGISTRATE.item("yeast", Item::new).register();
    public static final ItemEntry<Item> EMPTY_CAN = REGISTRATE.item("empty_can", Item::new).register();

    public static final ItemEntry<Item> BEER_BOTTLE = REGISTRATE
        .item("beer_bottle", Item::new)
        .properties(p -> p.stacksTo(16).food(ModFoods.BEER))
        .register();

    public static final ItemEntry<Item> SEALED_CAN = REGISTRATE
        .item("sealed_can", Item::new)
        .properties(p -> p.stacksTo(16).food(ModFoods.BEER))
        .register();

    public static void register() {}
}
```

- [ ] **Step 3: Write the food properties**

```java
package com.createbrewery;

import net.minecraft.world.food.FoodProperties;

public class ModFoods {
    public static final FoodProperties BEER = new FoodProperties.Builder()
        .nutrition(1)
        .saturationModifier(0.1f)
        .alwaysEdible()
        .build();
}
```

Phase 1 deliberately gives beer no status effects. Drunkenness is Phase 4 — do not add effects here.

- [ ] **Step 4: Write the block registry**

```java
package com.createbrewery;

import com.createbrewery.block.BarleyCropBlock;
import com.createbrewery.block.HopsCropBlock;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
    private static final CreateRegistrate REGISTRATE = CreateBrewery.REGISTRATE;

    public static final BlockEntry<BarleyCropBlock> BARLEY_CROP = REGISTRATE
        .block("barley_crop", BarleyCropBlock::new)
        .initialProperties(() -> Blocks.WHEAT)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .register();

    public static final BlockEntry<HopsCropBlock> HOPS_CROP = REGISTRATE
        .block("hops_crop", HopsCropBlock::new)
        .initialProperties(() -> Blocks.WHEAT)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .register();

    public static void register() {}
}
```

- [ ] **Step 5: Call both registries from the mod constructor**

```java
ModItems.register();
ModBlocks.register();
```

Order matters: `ModItems` must load before `ModBlocks`, because the crop blocks reference seed items.

- [ ] **Step 6: Verify in game**

Run: `./gradlew runClient`
Expected: every item exists via `/give`, and `/setblock ~ ~ ~ createbrewery:barley_crop` places a crop that grows and drops barley + seeds when mature.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/createbrewery/
git commit -m "feat: add brewing items and barley/hops crops"
```

---

## Task 4: The fermenting recipe type

**Files:**
- Create: `src/main/java/com/createbrewery/recipe/FermentingRecipe.java`
- Create: `src/main/java/com/createbrewery/ModRecipeTypes.java`

**Interfaces:**
- Consumes: `CreateBrewery.ID(String)`
- Produces: `FermentingRecipe` (extends `ProcessingRecipe<RecipeInput, ProcessingRecipeParams>`); `ModRecipeTypes.FERMENTING` implementing `IRecipeTypeInfo` with `getId()`, `getSerializer()`, `getType()`

- [ ] **Step 1: Write the recipe class**

`ProcessingRecipe` is `public abstract` with a public constructor and only two abstract methods — verified via `javap` against the shipped Create 6.0.10 jar.

```java
package com.createbrewery.recipe;

import com.createbrewery.ModRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.minecraft.world.item.crafting.RecipeInput;

public class FermentingRecipe extends ProcessingRecipe<RecipeInput, ProcessingRecipeParams> {

    public FermentingRecipe(ProcessingRecipeParams params) {
        super(ModRecipeTypes.FERMENTING, params);
    }

    @Override
    protected int getMaxInputCount() {
        return 1;   // one yeast item
    }

    @Override
    protected int getMaxOutputCount() {
        return 0;   // output is fluid only
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 1;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 1;
    }

    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }

    @Override
    public boolean matches(RecipeInput input, net.minecraft.world.level.Level level) {
        return false;   // the Fermenter matches explicitly; see Task 5
    }
}
```

`matches` returns false because the Fermenter does its own matching against tank contents plus the yeast slot, which a flat `RecipeInput` cannot express. This is the same approach Create's own basin recipes take.

- [ ] **Step 2: Write the recipe type holder**

```java
package com.createbrewery;

import com.createbrewery.recipe.FermentingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public enum ModRecipeTypes implements IRecipeTypeInfo {
    FERMENTING(FermentingRecipe::new);

    private final ResourceLocation id;
    private final Supplier<RecipeSerializer<?>> serializerSupplier;
    private final Supplier<RecipeType<?>> typeSupplier;

    ModRecipeTypes(com.simibubi.create.content.processing.recipe.ProcessingRecipe.Factory<
            com.simibubi.create.content.processing.recipe.ProcessingRecipeParams, ?> factory) {
        this.id = CreateBrewery.ID(name().toLowerCase(java.util.Locale.ROOT));
        this.serializerSupplier = () -> new ProcessingRecipeSerializer<>(factory);
        this.typeSupplier = () -> RecipeType.simple(this.id);
    }

    @Override public ResourceLocation getId() { return id; }
    @Override @SuppressWarnings("unchecked")
    public <T extends RecipeSerializer<?>> T getSerializer() { return (T) SERIALIZERS.get(this); }
    @Override @SuppressWarnings("unchecked")
    public <I extends net.minecraft.world.item.crafting.RecipeInput,
            R extends net.minecraft.world.item.crafting.Recipe<I>> RecipeType<R> getType() {
        return (RecipeType<R>) TYPES.get(this);
    }

    // Registration wiring is filled in during Step 3.
}
```

- [ ] **Step 3: Register serializer and type**

Create 6 registers processing recipe types through `DeferredRegister`. Wire both registries in the mod event bus, keyed by the enum constant, and store them in `EnumMap`s (`SERIALIZERS`, `TYPES`) referenced above. Confirm the exact `ProcessingRecipeSerializer` constructor signature in the IDE — it takes the `ProcessingRecipe.Factory` verified via `javap` (`public interface ProcessingRecipe$Factory<P, R> { R create(P); }`).

Call a `ModRecipeTypes.register(modEventBus)` from the mod constructor.

- [ ] **Step 4: Verify registration**

Run: `./gradlew runClient`
Expected: no registry errors at startup. `/reload` succeeds. (No recipes exist yet — that is Task 7.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/recipe/ src/main/java/com/createbrewery/ModRecipeTypes.java
git commit -m "feat: add createbrewery:fermenting recipe type"
```

---

## Task 5: The Fermenter — core logic

The heart of the mod, and the only task with non-trivial logic. All five Review Focus items are pinned here.

**Files:**
- Create: `src/main/java/com/createbrewery/recipe/FermentationProgress.java`
- Create: `src/test/java/com/createbrewery/FermentationProgressTest.java`
- Create: `src/main/java/com/createbrewery/block/FermenterBlock.java`
- Create: `src/main/java/com/createbrewery/block/entity/FermenterBlockEntity.java`
- Create: `src/main/java/com/createbrewery/Config.java`
- Modify: `src/main/java/com/createbrewery/ModBlocks.java`, `ModBlockEntities.java`

**Interfaces:**
- Consumes: `ModFluids.*`, `ModItems.YEAST`, `ModRecipeTypes.FERMENTING`, `FermentingRecipe`
- Produces: `FermentationProgress.progress(long startedAt, long now, int duration)` returning `float` in `[0,1]`; `FermenterBlockEntity.getProgress()`; `FermenterBlockEntity.NOT_STARTED` (`long` = `-1`)

- [ ] **Step 1: Write the failing test for the progress math**

This is the pure-logic extraction that makes four of the five Review Focus items testable without booting Minecraft.

```java
package com.createbrewery;

import com.createbrewery.recipe.FermentationProgress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FermentationProgressTest {

    private static final int DAY = 24000;

    @Test
    void notStartedIsZero() {
        assertEquals(0f, FermentationProgress.progress(-1L, 5000L, DAY));
    }

    @Test
    void halfwayThroughIsOneHalf() {
        assertEquals(0.5f, FermentationProgress.progress(1000L, 1000L + DAY / 2, DAY), 0.001f);
    }

    @Test
    void completeAtExactDuration() {
        assertEquals(1f, FermentationProgress.progress(1000L, 1000L + DAY, DAY));
    }

    // Review Focus #1: world time moved backwards (/time set, backup restore)
    @Test
    void negativeElapsedClampsToZero() {
        assertEquals(0f, FermentationProgress.progress(9000L, 1000L, DAY));
    }

    // Review Focus #4: player away for thousands of in-game days
    @Test
    void enormousElapsedSaturatesAtOne() {
        assertEquals(1f, FermentationProgress.progress(0L, Long.MAX_VALUE, DAY));
    }

    @Test
    void zeroOrNegativeDurationIsImmediatelyComplete() {
        assertEquals(1f, FermentationProgress.progress(0L, 10L, 0));
        assertEquals(1f, FermentationProgress.progress(0L, 10L, -5));
    }

    @Test
    void neverReturnsNaN() {
        assertFalse(Float.isNaN(FermentationProgress.progress(0L, 0L, 0)));
        assertFalse(Float.isNaN(FermentationProgress.progress(-1L, -1L, DAY)));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.createbrewery.FermentationProgressTest"`
Expected: FAIL — `FermentationProgress` does not exist (compilation error).

- [ ] **Step 3: Write the minimal implementation**

```java
package com.createbrewery.recipe;

public final class FermentationProgress {

    public static final long NOT_STARTED = -1L;

    private FermentationProgress() {}

    /**
     * Fraction of a fermentation batch completed, always within [0, 1].
     *
     * Derived from world time rather than a tick counter so that batches
     * continue through chunk unload. That means this must tolerate a world
     * clock that jumps, including backwards.
     */
    public static float progress(long startedAt, long now, int duration) {
        if (startedAt == NOT_STARTED) return 0f;
        if (duration <= 0) return 1f;

        long elapsed = now - startedAt;
        if (elapsed <= 0) return 0f;          // clock moved backwards, or same tick
        if (elapsed >= duration) return 1f;   // saturate before the float divide

        return (float) elapsed / (float) duration;
    }
}
```

The `elapsed >= duration` early return is what makes `Long.MAX_VALUE` safe: the division never sees a value that would lose precision.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.createbrewery.FermentationProgressTest"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit the tested logic**

```bash
git add src/main/java/com/createbrewery/recipe/FermentationProgress.java src/test/java/com/createbrewery/FermentationProgressTest.java
git commit -m "feat: add fermentation progress math with world-clock safety"
```

- [ ] **Step 6: Write the config**

```java
package com.createbrewery;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue FERMENTATION_DURATION_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_CANS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        FERMENTATION_DURATION_MULTIPLIER = builder
            .comment("Scales all fermentation times. 1.0 = one in-game day for ale.")
            .defineInRange("fermentationDurationMultiplier", 1.0, 0.01, 100.0);
        ENABLE_CANS = builder
            .comment("Enable metal cans as an alternative to glass bottles.")
            .define("enableCans", true);
        SPEC = builder.build();
    }
}
```

Register the spec in the mod constructor via `ModContainer#registerConfig` with `ModConfig.Type.COMMON`.

- [ ] **Step 7: Write the block entity**

Signatures below are verified against the shipped jar: `SmartBlockEntity.addBehaviours(List<BlockEntityBehaviour>)`, `SmartFluidTankBehaviour(BehaviourType, SmartBlockEntity, int tanks, int capacity, boolean enforceVariety)` with `.allowInsertion()`/`.forbidExtraction()`, and `IHaveGoggleInformation` from `com.simibubi.create.api.equipment.goggles`.

```java
package com.createbrewery.block.entity;

import com.createbrewery.Config;
import com.createbrewery.ModRecipeTypes;
import com.createbrewery.recipe.FermentationProgress;
import com.createbrewery.recipe.FermentingRecipe;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

public class FermenterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public static final int TANK_CAPACITY = 1500;
    public static final long NOT_STARTED = FermentationProgress.NOT_STARTED;

    protected SmartFluidTankBehaviour inputTank;
    protected SmartFluidTankBehaviour outputTank;
    protected final ItemStackHandler yeastSlot = new ItemStackHandler(1);

    private long startedAt = NOT_STARTED;
    private ResourceLocation activeRecipeId = null;

    public FermenterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        inputTank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 1, TANK_CAPACITY, true)
            .allowInsertion().forbidExtraction();
        outputTank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.OUTPUT, this, 1, TANK_CAPACITY, true)
            .forbidInsertion().allowExtraction();
        behaviours.add(inputTank);
        behaviours.add(outputTank);
    }

    public int scaledDuration(FermentingRecipe recipe) {
        double scaled = recipe.getProcessingDuration() * Config.FERMENTATION_DURATION_MULTIPLIER.get();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, scaled));
    }

    public float getProgress() {
        FermentingRecipe recipe = currentRecipe();
        if (recipe == null) return 0f;
        return FermentationProgress.progress(startedAt, level.getGameTime(), scaledDuration(recipe));
    }

    /** Review Focus #2: a saved recipe id that no longer resolves must not crash. */
    private FermentingRecipe currentRecipe() {
        if (activeRecipeId == null || level == null) return null;
        return level.getRecipeManager()
            .byKey(activeRecipeId)
            .map(holder -> holder.value() instanceof FermentingRecipe f ? f : null)
            .orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        if (startedAt == NOT_STARTED) {
            tryStart();
            return;
        }

        FermentingRecipe recipe = currentRecipe();
        if (recipe == null) {          // Review Focus #2
            reset();
            return;
        }
        if (!inputStillValid(recipe)) { // Review Focus #5
            reset();
            return;
        }
        if (getProgress() < 1f) return;

        finish(recipe);
    }

    /** Review Focus #5: player piped the wort back out mid-batch. */
    private boolean inputStillValid(FermentingRecipe recipe) {
        FluidStack held = inputTank.getPrimaryHandler().getFluid();
        return !held.isEmpty()
            && recipe.getFluidIngredients().get(0).test(held)
            && held.getAmount() >= recipe.getFluidIngredients().get(0).amount();
    }

    /** Review Focus #3: never destroy a finished batch because the output is full. */
    private void finish(FermentingRecipe recipe) {
        FluidStack result = recipe.getFluidResults().get(0).copy();
        IFluidHandler out = outputTank.getPrimaryHandler();

        int accepted = out.fill(result, IFluidHandler.FluidAction.SIMULATE);
        if (accepted < result.getAmount()) return;   // hold; retry next tick

        out.fill(result, IFluidHandler.FluidAction.EXECUTE);
        inputTank.getPrimaryHandler().drain(
            recipe.getFluidIngredients().get(0).amount(), IFluidHandler.FluidAction.EXECUTE);
        yeastSlot.extractItem(0, 1, false);
        reset();
    }

    private void reset() {
        startedAt = NOT_STARTED;
        activeRecipeId = null;
        setChanged();
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("StartedAt", startedAt);
        if (activeRecipeId != null) tag.putString("Recipe", activeRecipeId.toString());
        tag.put("Yeast", yeastSlot.serializeNBT(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        startedAt = tag.getLong("StartedAt");
        activeRecipeId = tag.contains("Recipe")
            ? ResourceLocation.tryParse(tag.getString("Recipe"))   // tryParse: malformed id yields null, not a throw
            : null;
        if (tag.contains("Yeast")) yeastSlot.deserializeNBT(registries, tag.getCompound("Yeast"));
    }
}
```

`tryStart()` scans `level.getRecipeManager()` for a `FermentingRecipe` whose fluid ingredient matches the input tank and whose item ingredient matches the yeast slot; on a hit it sets `startedAt = level.getGameTime()` and `activeRecipeId = <that recipe's id>`, then calls `setChanged()` and `sendData()`. Write it in this step.

> **Do not "unify" the two tank accessors.** Both are real and both are used deliberately (verified via `javap`): `getPrimaryHandler()` returns the concrete `SmartFluidTank` and is used where `fill`/`drain`/`getFluid` are needed; `getCapability()` returns an `IFluidHandler` and is used for `containedFluidTooltip` in Task 6, which takes that interface.

- [ ] **Step 8: Write the block and register both**

`FermenterBlock` extends `Block`, implements `IBE<FermenterBlockEntity>`, is `noOcclusion`, and returns a barrel-ish `VoxelShape`. Register the block in `ModBlocks` and the block entity type in a new `ModBlockEntities`, both through Registrate.

- [ ] **Step 9: Write the GameTest for the integration path**

Unit tests cover the arithmetic; this covers the wiring. It sets `startedAt` into the past rather than ticking 24000 times, so it runs in milliseconds.

```java
package com.createbrewery;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(CreateBrewery.MOD_ID)
public class FermenterGameTests {

    @GameTest(template = "createbrewery:platform")
    public static void producesBeerOnceDurationElapses(GameTestHelper helper) {
        // place fermenter, fill input tank with hopped wort, insert yeast,
        // backdate startedAt past the recipe duration, then:
        helper.succeedWhen(() -> {
            // assert the output tank contains createbrewery:beer
        });
    }

    @GameTest(template = "createbrewery:platform")
    public static void holdsBatchWhenOutputTankIsFull(GameTestHelper helper) {
        // Review Focus #3 at the integration level:
        // pre-fill the output tank, backdate startedAt, tick,
        // assert the input is NOT consumed and no beer is lost
        helper.succeedWhen(() -> {
            // assert input tank still holds its wort
        });
    }

    @GameTest(template = "createbrewery:platform")
    public static void survivesUnresolvableSavedRecipeId(GameTestHelper helper) {
        // Review Focus #2: write NBT naming a recipe that does not exist
        // ("createbrewery:no_such_recipe"), load the block entity, tick.
        // Assert: no exception, the fermenter resets to idle rather than
        // crashing the chunk.
        helper.succeedWhen(() -> {
            // assert getProgress() == 0 and the BE is still alive
        });
    }

    @GameTest(template = "createbrewery:platform")
    public static void invalidatesWhenInputDrainedMidBatch(GameTestHelper helper) {
        // Review Focus #5: start a batch, drain the input tank halfway
        // through, tick. Assert: the batch resets and no beer is produced
        // from nothing.
        helper.succeedWhen(() -> {
            // assert output tank is empty and startedAt == NOT_STARTED
        });
    }
}
```

Fill in the bodies using `helper.setBlock`, `helper.getBlockEntity`, and direct tank access. Add a `platform` structure NBT under `src/main/resources/data/createbrewery/structure/`.

- [ ] **Step 10: Run the GameTests**

Run: `./gradlew runGameTestServer`
Expected: all four tests pass (happy path, plus Review Focus #3, #2 and #5).

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/createbrewery/ src/test/java/com/createbrewery/ src/main/resources/data/createbrewery/structure/
git commit -m "feat: add Fermenter block with world-time-driven fermentation"
```

---

## Task 6: Goggle and Jade progress display

**Files:**
- Modify: `src/main/java/com/createbrewery/block/entity/FermenterBlockEntity.java`

**Interfaces:**
- Consumes: `FermenterBlockEntity.getProgress()`
- Produces: nothing new

- [ ] **Step 1: Implement the goggle tooltip**

`IHaveGoggleInformation` supplies a `containedFluidTooltip(List<Component>, boolean, IFluidHandler)` default — verified via `javap` — so tank contents come free.

```java
@Override
public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
    FermentingRecipe recipe = currentRecipe();

    if (recipe == null) {
        tooltip.add(Component.translatable("createbrewery.goggles.fermenter.idle")
            .withStyle(ChatFormatting.GRAY));
    } else {
        int percent = Math.round(getProgress() * 100f);
        tooltip.add(Component.translatable("createbrewery.goggles.fermenter.progress", percent)
            .withStyle(ChatFormatting.GOLD));

        long remaining = Math.max(0, scaledDuration(recipe) - (level.getGameTime() - startedAt));
        tooltip.add(Component.translatable("createbrewery.goggles.fermenter.remaining",
                String.format("%.1f", remaining / 24000f))
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    containedFluidTooltip(tooltip, isPlayerSneaking, inputTank.getCapability());
    containedFluidTooltip(tooltip, isPlayerSneaking, outputTank.getCapability());
    return true;
}
```

- [ ] **Step 2: Add the lang entries**

In `src/main/resources/assets/createbrewery/lang/en_us.json`:

```json
{
  "createbrewery.goggles.fermenter.idle": "Idle",
  "createbrewery.goggles.fermenter.progress": "Fermenting: %s%%",
  "createbrewery.goggles.fermenter.remaining": "%s days remaining"
}
```

- [ ] **Step 3: Verify in game**

Run: `./gradlew runClient`
Expected: wearing Engineer's Goggles and looking at an active Fermenter shows a percentage that climbs, a day countdown, and both tanks' contents. Jade picks the same data up automatically.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/createbrewery/block/entity/FermenterBlockEntity.java src/main/resources/assets/createbrewery/lang/en_us.json
git commit -m "feat: show fermentation progress in goggle overlay"
```

---

## Task 7: The chain recipes

**Files:**
- Create: `src/main/java/com/createbrewery/data/ModRecipeProvider.java`
- Modify: the datagen entrypoint

**Interfaces:**
- Consumes: `ModItems.*`, `ModFluids.*`, `ModRecipeTypes.FERMENTING`
- Produces: generated JSON under `src/generated/resources/data/createbrewery/recipe/`

Builder methods verified via `javap`: `withItemIngredients`, `withFluidIngredients`, `withItemOutputs`, `withFluidOutputs`, `duration(int)`, `requiresHeat(HeatCondition)`, `build(RecipeOutput)`. Heat values are `HeatCondition.NONE`, `HEATED`, `SUPERHEATED`.

- [ ] **Step 1: Steeping — barley + water to green malt (Basin mixing, no heat)**

```java
// barley x3 + 250mB water -> green malt x3
create.mixing(CreateBrewery.ID("steeping"))
    .withItemIngredients(Ingredient.of(ModItems.BARLEY.get()))
    .withFluidIngredients(SizedFluidIngredient.of(Fluids.WATER, 250))
    .withItemOutputs(new ProcessingOutput(new ItemStack(ModItems.GREEN_MALT.get(), 3), 1f))
    .requiresHeat(HeatCondition.NONE)
    .duration(200)
    .build(output);
```

- [ ] **Step 2: Kilning — green malt to malt (vanilla smoking)**

```java
SimpleCookingRecipeBuilder.smoking(
        Ingredient.of(ModItems.GREEN_MALT.get()),
        RecipeCategory.FOOD,
        ModItems.MALT.get(),
        0.1f,
        100)
    .unlockedBy("has_green_malt", has(ModItems.GREEN_MALT.get()))
    .save(output, CreateBrewery.ID("kilning"));
```

This is what makes Create's Blaze Burner bulk-process the step for free. **Verify on first run** that the burner picks it up (Risks table).

- [ ] **Step 3: Milling — malt to grist (Millstone)**

```java
create.milling(CreateBrewery.ID("milling_malt"))
    .withItemIngredients(Ingredient.of(ModItems.MALT.get()))
    .withItemOutputs(new ProcessingOutput(new ItemStack(ModItems.GRIST.get(), 2), 1f))
    .duration(150)
    .build(output);
```

- [ ] **Step 4: Mashing — grist + water to wort + spent grain (heated Basin)**

The combined item+fluid output. `BasinBlockEntity.acceptOutputs(List<ItemStack>, List<FluidStack>, boolean)` supports it; if the basin refuses it in practice, fall back to the spec's split-lauter design.

```java
create.mixing(CreateBrewery.ID("mashing"))
    .withItemIngredients(Ingredient.of(ModItems.GRIST.get()), Ingredient.of(ModItems.GRIST.get()))
    .withFluidIngredients(SizedFluidIngredient.of(Fluids.WATER, 500))
    .withFluidOutputs(new FluidStack(ModFluids.WORT.get(), 500))
    .withItemOutputs(new ProcessingOutput(new ItemStack(ModItems.SPENT_GRAIN.get()), 1f))
    .requiresHeat(HeatCondition.HEATED)
    .duration(400)
    .build(output);
```

- [ ] **Step 5: The boil — wort + hops to hopped wort (superheated Basin)**

```java
create.mixing(CreateBrewery.ID("boiling"))
    .withItemIngredients(Ingredient.of(ModItems.HOP_CONES.get()))
    .withFluidIngredients(SizedFluidIngredient.of(ModFluids.WORT.get(), 500))
    .withFluidOutputs(new FluidStack(ModFluids.HOPPED_WORT.get(), 500))
    .requiresHeat(HeatCondition.SUPERHEATED)
    .duration(600)
    .build(output);
```

- [ ] **Step 6: Yeast — sugar + wheat starter (Basin mixing)**

```java
create.mixing(CreateBrewery.ID("yeast_culture"))
    .withItemIngredients(Ingredient.of(Items.SUGAR), Ingredient.of(Items.WHEAT))
    .withItemOutputs(new ProcessingOutput(new ItemStack(ModItems.YEAST.get(), 2), 1f))
    .requiresHeat(HeatCondition.NONE)
    .duration(200)
    .build(output);
```

- [ ] **Step 7: Fermentation — hopped wort + yeast to beer (one in-game day)**

```java
// 24000 ticks = one Minecraft day
new ProcessingRecipeBuilder<>(FermentingRecipe::new, CreateBrewery.ID("fermenting_ale"))
    .withItemIngredients(Ingredient.of(ModItems.YEAST.get()))
    .withFluidIngredients(SizedFluidIngredient.of(ModFluids.HOPPED_WORT.get(), 500))
    .withFluidOutputs(new FluidStack(ModFluids.BEER.get(), 500))
    .duration(24000)
    .build(output);
```

- [ ] **Step 8: Packaging — bottles via Spout, cans via Press**

```java
// Spout: glass bottle + 250mB beer -> beer bottle
create.filling(CreateBrewery.ID("bottling_beer"))
    .withItemIngredients(Ingredient.of(Items.GLASS_BOTTLE))
    .withFluidIngredients(SizedFluidIngredient.of(ModFluids.BEER.get(), 250))
    .withSingleItemOutput(new ItemStack(ModItems.BEER_BOTTLE.get()))
    .build(output);

// Press: iron sheet -> empty can
create.pressing(CreateBrewery.ID("empty_can"))
    .withItemIngredients(Ingredient.of(create_iron_sheet_tag))
    .withItemOutputs(new ProcessingOutput(new ItemStack(ModItems.EMPTY_CAN.get(), 2), 1f))
    .build(output);

// Spout: empty can + 250mB beer -> sealed can
create.filling(CreateBrewery.ID("canning_beer"))
    .withItemIngredients(Ingredient.of(ModItems.EMPTY_CAN.get()))
    .withFluidIngredients(SizedFluidIngredient.of(ModFluids.BEER.get(), 250))
    .withSingleItemOutput(new ItemStack(ModItems.SEALED_CAN.get()))
    .build(output);
```

- [ ] **Step 9: Run datagen and verify the output format**

Run: `./gradlew runData`
Expected: JSON appears under `src/generated/resources/data/createbrewery/recipe/`. **Open `mashing.json` and confirm** the item and fluid outputs both sit in a single `results` array, with the fluid carrying `amount` — the Create 6 format. If a `fluid_results` key appears, the builder is writing the old schema and the datagen must be corrected.

- [ ] **Step 10: Verify the whole chain in game**

Run: `./gradlew runClient`
Expected: JEI shows every step. Building the full line in creative produces a beer bottle from barley.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/createbrewery/data/ src/generated/
git commit -m "feat: add the full grain-to-bottle recipe chain"
```

---

## Task 8: Acquisition — loot, trades, tags

**Files:**
- Create: `src/main/java/com/createbrewery/data/ModLootModifiers.java`, `ModTagsProvider.java`
- Create: `src/main/java/com/createbrewery/ModVillagerTrades.java`

**Interfaces:**
- Consumes: `ModItems.BARLEY_SEEDS`, `ModItems.HOP_CONES`
- Produces: nothing other tasks depend on

- [ ] **Step 1: Barley seeds from tall grass**

Add a global loot modifier injecting `createbrewery:barley_seeds` into `minecraft:blocks/short_grass` and `tall_grass` at roughly half the weight of wheat seeds.

- [ ] **Step 2: Hop cones from Farmer villagers and village chests**

Register a Farmer villager trade (level 2: emeralds → hop cones) via `VillagerTradesEvent`, and inject hop cones into the `minecraft:chests/village/village_plains_house` loot table.

No structure edits — Global Constraints.

- [ ] **Step 3: Tags**

Barley and hops crops into the relevant farmland/crop tags; `spent_grain`, `green_malt`, `hop_cones` into `minecraft:compostables` via the composter data map. Beer bottle and sealed can into a `createbrewery:beer` item tag, plus the `#brewery:beer` tag for the optional `selling_bin` hook (spec: ships the tag, does not promise the integration).

- [ ] **Step 4: Verify**

Run: `./gradlew runClient`
Expected: breaking grass eventually drops barley seeds; a Farmer villager offers hop cones; spent grain composts.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/data/ src/main/java/com/createbrewery/ModVillagerTrades.java src/generated/
git commit -m "feat: add barley and hops acquisition via drops, trades and loot"
```

---

## Task 9: JEI category for fermenting

**Files:**
- Create: `src/main/java/com/createbrewery/compat/jei/FermentingCategory.java`, `CreateBreweryJEI.java`
- Modify: `build.gradle.kts` (JEI compile dependency)

**Interfaces:**
- Consumes: `FermentingRecipe`, `ModRecipeTypes.FERMENTING`
- Produces: nothing other tasks depend on

Every other step in the chain is an existing Create category and appears in JEI automatically once Task 7's recipes exist. Only fermenting needs a category.

- [ ] **Step 1: Add the JEI dependency**

```kotlin
compileOnly("mezz.jei:jei-${property("minecraft_version")}-neoforge-api:${property("jei_version")}")
runtimeOnly("mezz.jei:jei-${property("minecraft_version")}-neoforge:${property("jei_version")}")
```

Add `jei_version` to `gradle.properties` matching the JEI in the target pack (`19.57.0.444`).

- [ ] **Step 2: Write the category**

Render the input fluid, the yeast slot, an arrow, and the output fluid. Show the duration in in-game days rather than ticks — `24000` ticks means nothing to a player, "1 day" does.

- [ ] **Step 3: Register the plugin**

`@JeiPlugin` class registering the category and feeding it all `FermentingRecipe` instances from the recipe manager.

- [ ] **Step 4: Verify**

Run: `./gradlew runClient`
Expected: JEI shows a Fermenting category; looking up beer shows the fermenting recipe with "1 day".

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/compat/ build.gradle.kts gradle.properties
git commit -m "feat: add JEI category for fermenting"
```

---

## Task 10: Ponder scenes

**Files:**
- Create: `src/main/java/com/createbrewery/ponder/ModPonderScenes.java`, `BrewerySceneBuilder.java`

**Interfaces:**
- Consumes: `ModBlocks.FERMENTER`, the full recipe chain
- Produces: nothing

Ponder is a declared dependency and the target pack ships `ponderjs` — in a Create addon these are expected content, not polish.

- [ ] **Step 1: Scene — the Fermenter**

Show wort piped in, yeast funnelled in, the goggle overlay counting up, beer piped out. Explicitly show that it needs **no shaft** — players will expect one, and the scene is the right place to correct that.

- [ ] **Step 2: Scene — mash versus boil heat**

Show a Basin over a normal Blaze Burner mashing, then over a superheated one boiling. The heat distinction is the most likely thing for a player to get wrong.

- [ ] **Step 3: Register scenes**

Register via Ponder's scene registration. Note the spec's risk: "ponder dependency path has changed" in Create 6 — confirm the current API before writing much.

- [ ] **Step 4: Verify**

Run: `./gradlew runClient`
Expected: `W` on the Fermenter in JEI opens the Ponder scene; it plays through without errors.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/createbrewery/ponder/
git commit -m "feat: add Ponder scenes for the fermenter and heat tiers"
```

---

## Definition of done

- [ ] `./gradlew build` succeeds
- [ ] `./gradlew test` — all `FermentationProgressTest` cases pass
- [ ] `./gradlew runGameTestServer` — all four Fermenter GameTests pass
- [ ] A creative-mode line takes barley to a bottle of beer with no manual intervention
- [ ] A batch started, chunk unloaded, and revisited an in-game day later is complete — the behaviour the passive design exists to buy
- [ ] JEI shows every stage; Ponder scenes play
- [ ] The mod loads in the target pack (`Create Vanilla`) without conflicts
