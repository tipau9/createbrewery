package com.createbrewery.data;

import com.createbrewery.CreateBrewery;
import com.createbrewery.ModBlocks;
import com.createbrewery.ModFluids;
import com.createbrewery.ModItems;
import com.createbrewery.ModRecipeTypes;
import com.createbrewery.recipe.FermentingRecipe;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.CompactingRecipeGen;
import com.simibubi.create.api.data.recipe.CrushingRecipeGen;
import com.simibubi.create.api.data.recipe.DeployingRecipeGen;
import com.simibubi.create.api.data.recipe.SequencedAssemblyRecipeGen;
import com.createbrewery.drugs.Purity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import com.simibubi.create.api.data.recipe.FillingRecipeGen;
import com.simibubi.create.api.data.recipe.MillingRecipeGen;
import com.simibubi.create.api.data.recipe.MixingRecipeGen;
import com.simibubi.create.api.data.recipe.PressingRecipeGen;
import com.simibubi.create.api.data.recipe.StandardProcessingRecipeGen;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Datagen for the grain-to-bottle recipe chain. The brief's {@code create.mixing(...)} /
 * {@code create.milling(...)} / {@code create.filling(...)} / {@code create.pressing(...)}
 * receiver does not exist in Create 6.0.10 — there is no such static helper anywhere in the
 * jar. The real idiom, confirmed by decompiling Create's own
 * {@code com.simibubi.create.foundation.data.recipe.CreateMixingRecipeGen} (and its Milling/
 * Pressing/Filling siblings): an addon writes its own concrete subclass of Create's abstract
 * {@code com.simibubi.create.api.data.recipe.<Type>RecipeGen} per processing type. Each such
 * class is itself a {@code RecipeProvider} (via {@code BaseRecipeProvider}); its constructor
 * calls the inherited {@code create(name, builder -> builder....)} method once per recipe,
 * which stores a lazy {@code GeneratedRecipe} and replays it into a {@code RecipeOutput} when
 * the provider runs. Create's own {@code CreateRecipeProvider.registerAllProcessing(...)}
 * shows every one of these subclasses gets constructed and added to the {@code DataGenerator}
 * individually from the mod's {@code GatherDataEvent} handler — {@link #gatherData} below does
 * the same for ours.
 */
public class ModRecipeProvider {

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();
        boolean server = event.includeServer();

        generator.addProvider(server, new Basin(output, registries));
        generator.addProvider(server, new Milling(output, registries));
        generator.addProvider(server, new Pressing(output, registries));
        generator.addProvider(server, new Crushing(output, registries));
        generator.addProvider(server, new Compacting(output, registries));
        generator.addProvider(server, new Deploying(output, registries));
        generator.addProvider(server, new KetaLab(output, registries));
        generator.addProvider(server, new Filling(output, registries));
        generator.addProvider(server, new Fermenting(output, registries));
        generator.addProvider(server, new Vanilla(output, registries));
    }

    /** Basin (Mixer) recipes: steeping, mashing, the boil, yeast culture. */
    /** A made batch: its strength in normal doses is known, not rolled like a street deal (see Purity). */
    static ItemStack made(ItemLike item, int count, float strength) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(Purity.PURITY.get(), new Purity(strength, false, false));
        return stack;
    }

    public static class Basin extends MixingRecipeGen {
        public Basin(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            // barley x3 + 250mB water -> green malt x3, no heat (Basin mixing). Three
            // separate .require(BARLEY) entries, not one: each Ingredient entry only ever
            // consumes a single item (confirmed reading BasinRecipe.apply(), decompiled —
            // it extracts exactly 1 item per ingredient entry), so matching the brief's
            // stated 3-in/3-out ratio needs three entries, not an output count trick.
            create("steeping", b -> b
                .require(ModItems.BARLEY.get())
                .require(ModItems.BARLEY.get())
                .require(ModItems.BARLEY.get())
                .require(Fluids.WATER, 250)
                .output(ModItems.GREEN_MALT.get(), 3)
                .requiresHeat(HeatCondition.NONE)
                .duration(200));

            // grist x2 + 500mB water -> 500mB wort + spent grain, heated (Basin mixing).
            // Combined item+fluid output from one recipe: BasinRecipe.getMaxOutputCount() = 4
            // and getMaxFluidOutputCount() = 2 (both confirmed by decompiling BasinRecipe),
            // and BasinRecipe.apply() populates recipeOutputItems AND recipeOutputFluids from
            // the same recipe before a single basin.acceptOutputs(items, fluids, simulate)
            // call — so the combined output is natively supported; no lautering fallback needed.
            create("mashing", b -> b
                .require(ModItems.GRIST.get())
                .require(ModItems.GRIST.get())
                .require(Fluids.WATER, 500)
                .output(ModFluids.WORT.get(), 500)
                .output(ModItems.SPENT_GRAIN.get())
                .requiresHeat(HeatCondition.HEATED)
                .duration(400));

            // 500mB wort + hop cones -> 500mB hopped wort, superheated (Basin mixing)
            create("boiling", b -> b
                .require(ModFluids.WORT.get(), 500)
                .require(ModItems.HOP_CONES.get())
                .output(ModFluids.HOPPED_WORT.get(), 500)
                .requiresHeat(HeatCondition.SUPERHEATED)
                .duration(600));

            // sugar + wheat -> yeast x2, no heat (Basin mixing)
            create("yeast_culture", b -> b
                .require(Items.SUGAR)
                .require(Items.WHEAT)
                .output(ModItems.YEAST.get(), 2)
                .requiresHeat(HeatCondition.NONE)
                .duration(200));

            // The Apotheke: made-up game recipes, nothing like a real process.
            // Koks: twelve portions of milled leaf leached out in a heated basin for one paste - a
            // stack of leaves comes to only a handful of lines.
            create("coca_paste", b -> {
                for (int i = 0; i < 12; i++) b.require(ModItems.COCA_MEAL.get());
                return b.require(Fluids.WATER, 1000)
                    .output(ModItems.COCA_PASTE.get())
                    .requiresHeat(HeatCondition.HEATED)
                    .duration(600);
            });
            // Keta: the lab's raw batches crystallised over a superheated burner.
            create("keta_crystals", b -> b
                .require(ModItems.RAW_KETA.get()).require(ModItems.RAW_KETA.get())
                .output(ModItems.KETA_CRYSTALS.get(), 3)
                .requiresHeat(HeatCondition.SUPERHEATED)
                .duration(800));
            // Heroin: the lab's raw mass refined over a superheated burner; some comes out weak.
            create("heroin_refining", b -> b
                .require(ModItems.RAW_HEROIN.get())
                .output(made(ModItems.HEROIN.get(), 2, 1.0f))
                .output(0.35f, made(ModItems.HEROIN.get(), 1, 0.5f))
                .requiresHeat(HeatCondition.SUPERHEATED)
                .duration(600));
            // Crystal: the raw mass crystallised, superheated.
            create("meth_crystals", b -> b
                .require(ModItems.RAW_METH.get()).require(ModItems.RAW_METH.get())
                .output(ModItems.METH_CRYSTALS.get(), 3)
                .requiresHeat(HeatCondition.SUPERHEATED)
                .duration(800));
            // DMT: a stack of root bark for a pipe or two.
            create("dmt", b -> {
                for (int i = 0; i < 8; i++) b.require(ModItems.ROOT_BARK.get());
                return b.require(Items.GLASS_BOTTLE)
                    .require(Fluids.WATER, 1000)
                    .output(made(ModItems.DMT.get(), 1, 1.0f))
                    .output(0.3f, made(ModItems.DMT.get(), 1, 1.0f))
                    .requiresHeat(HeatCondition.SUPERHEATED)
                    .duration(800);
            });
            // Xanax: pharmacy powder, pressed into pills (Pressing).
            create("xanax_powder", b -> b
                .require(Items.BONE_MEAL).require(Items.SUGAR).require(Items.LAPIS_LAZULI)
                .output(ModItems.XANAX_POWDER.get(), 2)
                .requiresHeat(HeatCondition.HEATED)
                .duration(300));
            // Wine and spirits. Distilling is a heated basin: a lot of wash in, a little spirit out.
            create("grain_mash", b -> {
                for (int i = 0; i < 6; i++) b.require(AllItems.WHEAT_FLOUR.get());
                return b.require(Fluids.WATER, 1000).output(ModFluids.GRAIN_MASH.get(), 1000)
                    .requiresHeat(HeatCondition.HEATED).duration(400);
            });
            create("potato_mash", b -> {
                for (int i = 0; i < 8; i++) b.require(Items.POTATO);
                return b.require(Fluids.WATER, 1000).output(ModFluids.POTATO_MASH.get(), 1000)
                    .requiresHeat(HeatCondition.HEATED).duration(400);
            });
            create("molasses", b -> {
                for (int i = 0; i < 8; i++) b.require(Items.SUGAR_CANE);
                return b.require(Fluids.WATER, 1000).output(ModFluids.MOLASSES.get(), 1000)
                    .requiresHeat(HeatCondition.HEATED).duration(400);
            });
            create("agave_juice", b -> {
                for (int i = 0; i < 6; i++) b.require(ModItems.AGAVE_PULP.get());
                return b.require(Fluids.WATER, 1000).output(ModFluids.AGAVE_JUICE.get(), 1000).duration(300);
            });
            create("distilling_brouillis", b -> b
                .require(ModFluids.WINE.get(), 1000)
                .output(ModFluids.BROUILLIS.get(), 300)
                .requiresHeat(HeatCondition.HEATED)
                .duration(600));
            create("distilling_eau_de_vie", b -> b
                .require(ModFluids.BROUILLIS.get(), 600)
                .output(ModFluids.EAU_DE_VIE.get(), 250)
                .requiresHeat(HeatCondition.HEATED)
                .duration(600));
            create("distilling_low_wines", b -> b
                .require(ModFluids.WASH.get(), 1000)
                .output(ModFluids.LOW_WINES.get(), 300)
                .requiresHeat(HeatCondition.HEATED)
                .duration(600));
            create("distilling_new_make", b -> b
                .require(ModFluids.LOW_WINES.get(), 600)
                .output(ModFluids.NEW_MAKE.get(), 250)
                .requiresHeat(HeatCondition.HEATED)
                .duration(600));
            create("distilling_raw_korn", b -> b
                .require(ModFluids.GRAIN_WASH.get(), 1000)
                .output(ModFluids.RAW_KORN.get(), 300)
                .requiresHeat(HeatCondition.HEATED)
                .duration(600));
            create("distilling_doppelkorn", b -> b
                .require(ModFluids.RAW_KORN.get(), 600)
                .output(ModFluids.DOPPELKORN.get(), 250)
                .requiresHeat(HeatCondition.HEATED)
                .duration(600));
            create("rectifying_neutral_spirit", b -> b
                .require(ModFluids.POTATO_WASH.get(), 1000)
                .output(ModFluids.NEUTRAL_SPIRIT.get(), 250)
                .requiresHeat(HeatCondition.SUPERHEATED)
                .duration(600));
            create("distilling_raw_rum", b -> b
                .require(ModFluids.RUM_WASH.get(), 1000)
                .output(ModFluids.RAW_RUM.get(), 250)
                .requiresHeat(HeatCondition.HEATED)
                .duration(600));
            create("distilling_ordinario", b -> b
                .require(ModFluids.AGAVE_WASH.get(), 1000)
                .output(ModFluids.ORDINARIO.get(), 300)
                .requiresHeat(HeatCondition.HEATED)
                .duration(600));
            create("distilling_tequila", b -> b
                .require(ModFluids.ORDINARIO.get(), 600)
                .output(ModFluids.TEQUILA.get(), 250)
                .requiresHeat(HeatCondition.HEATED)
                .duration(600));
            // Vodka: neutral spirit filtered through charcoal. Gin: redistilled with juniper.
            create("vodka", b -> b
                .require(ModFluids.NEUTRAL_SPIRIT.get(), 250).require(Items.CHARCOAL)
                .output(ModFluids.VODKA.get(), 250)
                .duration(200));
            create("gin", b -> b
                .require(ModFluids.NEUTRAL_SPIRIT.get(), 250)
                .require(ModItems.JUNIPER_BERRIES.get()).require(ModItems.JUNIPER_BERRIES.get())
                .output(ModFluids.GIN.get(), 250)
                .requiresHeat(HeatCondition.HEATED)
                .duration(400));
            create("naloxon", b -> b
                .require(Items.GHAST_TEAR).require(Items.GLASS_BOTTLE).require(Items.SUGAR)
                .output(ModItems.NALOXON.get(), 2));
            // Water with sea salt (kelp) and sugar.
            create("electrolyte_drink", b -> b
                .require(Items.GLASS_BOTTLE).require(Items.DRIED_KELP).require(Items.SUGAR)
                .require(Fluids.WATER, 250)
                .output(ModItems.ELECTROLYTE.get()));
            create("lachgas", b -> b
                .require(Items.GUNPOWDER).require(Items.SUGAR)
                .require(Fluids.WATER, 250)
                .output(ModFluids.LACHGAS.get(), 250)
                .requiresHeat(HeatCondition.HEATED));
        }
    }

    /** Millstone: malt -> grist. */
    public static class Milling extends MillingRecipeGen {
        public Milling(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            // Weed: trimmed and dried buds ground at a full dose; seeded ones weaker, and their seeds
            // come out in the grinder; trim makes a weak bag.
            create("weed_from_buds", b -> b
                .require(ModItems.DRIED_BUDS.get())
                .output(made(ModItems.WEED.get(), 2, 1.0f))
                .duration(100));
            create("weed_from_seeded_buds", b -> b
                .require(ModItems.SEEDED_BUDS.get())
                .output(made(ModItems.WEED.get(), 1, 0.6f))
                .output(ModItems.HEMP_SEEDS.get(), 2)
                .output(0.5f, ModItems.HEMP_SEEDS.get())
                .duration(100));
            create("weed_from_trim", b -> b
                .require(ModItems.WEED_TRIM.get())
                .output(made(ModItems.WEED.get(), 1, 0.35f))
                .duration(100));
            create("root_bark", b -> b
                .require(Items.HANGING_ROOTS)
                .output(ModItems.ROOT_BARK.get())
                .duration(120));
            create("agave_pulp", b -> b
                .require(ModItems.ROASTED_AGAVE.get())
                .output(ModItems.AGAVE_PULP.get(), 3)
                .duration(150));
            create("coca_meal", b -> b
                .require(ModItems.DRIED_COCA_LEAF.get())
                .output(ModItems.COCA_MEAL.get())
                .duration(120));
            // Lab Keta is pharmacy-steady: a full dose each.
            create("keta_milling", b -> b
                .require(ModItems.KETA_CRYSTALS.get())
                .output(made(ModItems.KETA.get(), 2, 1.0f))
                .duration(100));
            create("milling_malt", b -> b
                .require(ModItems.MALT.get())
                .output(ModItems.GRIST.get(), 2)
                .duration(150));
        }
    }

    /**
     * Mechanical Press: iron sheet -> empty can. The brief's undefined
     * {@code create_iron_sheet_tag} resolves to Create's own registered
     * {@code create:iron_sheet} item, {@code com.simibubi.create.AllItems.IRON_SHEET}
     * (confirmed present and public in the decompiled jar). Create does tag it with an
     * internal common "plates/iron" tag via a package-private {@code CommonMetal} helper
     * that isn't meant as external API, so the concrete item is the simpler, stabler choice
     * here (ponytail: skip reconstructing Create's internal tag plumbing for a single-item
     * ingredient — add tag support later only if another mod's iron sheet needs to work too).
     */
    public static class Pressing extends PressingRecipeGen {
        public Pressing(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            create("empty_can", b -> b
                .require(AllItems.IRON_SHEET.get())
                .output(ModItems.EMPTY_CAN.get(), 2));
            // A pill press: pharmacy-steady, a full dose each.
            create("xanax_pills", b -> b
                .require(ModItems.XANAX_POWDER.get())
                .output(made(ModItems.XANAX.get(), 4, 1.0f)));
        }
    }

    /** Spout: bottles and cans get filled with beer. */
    public static class Crushing extends CrushingRecipeGen {
        public Crushing(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            // The grade split: clean lines at a full dose, and now and then crumbs with residue in them.
            create("koks_from_brick", b -> b
                .require(ModItems.KOKS_BRICK.get())
                .output(made(ModItems.KOKS.get(), 2, 1.0f))
                .output(0.5f, made(ModItems.KOKS.get(), 1, 1.0f))
                .output(0.35f, made(ModItems.KOKS.get(), 1, 0.55f))
                .duration(200));
            create("meth_from_crystals", b -> b
                .require(ModItems.METH_CRYSTALS.get())
                .output(made(ModItems.METH.get(), 3, 1.0f))
                .output(0.4f, made(ModItems.METH.get(), 1, 0.6f))
                .duration(200));
            create("amethyst_grit", b -> b
                .require(Items.AMETHYST_SHARD)
                .output(ModItems.AMETHYST_GRIT.get(), 2)
                .output(0.25f, ModItems.AMETHYST_GRIT.get())
                .duration(150));
        }
    }

    public static class Deploying extends DeployingRecipeGen {
        public Deploying(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            // Trimming: a deployer with shears takes the leaf off the fresh buds.
            create("trimming_buds", b -> b
                .require(ModItems.WET_BUDS.get())
                .require(Items.SHEARS)
                .toolNotConsumed()
                .output(ModItems.TRIMMED_BUDS.get())
                .output(0.5f, ModItems.WEED_TRIM.get()));
            // Scoring the ripe pods: a deployer with shears; the pod gives up its seeds too.
            create("scoring_poppy_pods", b -> b
                .require(ModItems.POPPY_POD.get())
                .require(Items.SHEARS)
                .toolNotConsumed()
                .output(ModItems.OPIUM_LATEX.get())
                .output(ModItems.OPIUM_POPPY_SEEDS.get())
                .output(0.5f, ModItems.OPIUM_POPPY_SEEDS.get()));
            // LSD: the solution dripped onto paper, then the sheet cut into tabs.
            create("soaking_blotter", b -> b
                .require(Items.PAPER)
                .require(ModItems.LSD_SOLUTION.get())
                .output(ModItems.BLOTTER_SHEET.get())
                .output(Items.GLASS_BOTTLE));
            create("cutting_blotter", b -> b
                .require(ModItems.BLOTTER_SHEET.get())
                .require(Items.SHEARS)
                .toolNotConsumed()
                .output(made(ModItems.LSD.get(), 9, 1.0f)));
        }
    }

    public static class Compacting extends CompactingRecipeGen {
        public Compacting(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            // Two pastes pressed hard into one wrapped brick (press over a basin).
            create("koks_brick", b -> b
                .require(ModItems.COCA_PASTE.get()).require(ModItems.COCA_PASTE.get())
                .output(ModItems.KOKS_BRICK.get()));
            // Grapes pressed in a basin: must for the fermenter.
            create("grape_must", b -> {
                for (int i = 0; i < 8; i++) b.require(ModItems.GRAPES.get());
                return b.output(ModFluids.GRAPE_MUST.get(), 500);
            });
            create("raw_opium", b -> b
                .require(ModItems.OPIUM_LATEX.get()).require(ModItems.OPIUM_LATEX.get())
                .require(ModItems.OPIUM_LATEX.get()).require(ModItems.OPIUM_LATEX.get())
                .output(ModItems.RAW_OPIUM.get()));
            // Pressing pills: mostly a normal dose, now and then a dangerous "super pill".
            create("mdma_pills", b -> b
                .require(ModItems.MDMA_CRYSTALS.get()).require(Items.SUGAR)
                .output(made(ModItems.MDMA.get(), 3, 1.0f))
                .output(0.25f, made(ModItems.MDMA.get(), 1, 1.8f)));
        }
    }

    /**
     * The precision labs: a glass flask through rounds of ingredients, water, the precision
     * mechanism's fine work and the press; a share of batches comes out spoiled. Made-up steps.
     */
    public static class KetaLab extends SequencedAssemblyRecipeGen {
        public KetaLab(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            create("keta_batch", b -> b
                .require(Items.GLASS_BOTTLE)
                .transitionTo(ModItems.KETA_BATCH.get())
                .loops(5)
                .addStep(com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe::new,
                    s -> s.require(ModItems.AMETHYST_GRIT.get()))
                .addStep(com.simibubi.create.content.fluids.transfer.FillingRecipe::new,
                    s -> s.require(Fluids.WATER, 250))
                .addStep(com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe::new,
                    s -> s.require(AllItems.PRECISION_MECHANISM.get()).toolNotConsumed())
                .addStep(com.simibubi.create.content.kinetics.press.PressingRecipe::new, s -> s)
                .addOutput(ModItems.RAW_KETA.get(), 70)
                .addOutput(ModItems.RUINED_BATCH.get(), 30));
            create("heroin_batch", b -> b
                .require(Items.GLASS_BOTTLE)
                .transitionTo(ModItems.HEROIN_BATCH.get())
                .loops(4)
                .addStep(DeployerApplicationRecipe::new, s -> s.require(ModItems.RAW_OPIUM.get()))
                .addStep(DeployerApplicationRecipe::new, s -> s.require(Items.BLAZE_POWDER))
                .addStep(FillingRecipe::new, s -> s.require(Fluids.WATER, 250))
                .addStep(PressingRecipe::new, s -> s)
                .addOutput(ModItems.RAW_HEROIN.get(), 70)
                .addOutput(ModItems.RUINED_BATCH.get(), 30));
            create("meth_batch", b -> b
                .require(Items.GLASS_BOTTLE)
                .transitionTo(ModItems.METH_BATCH.get())
                .loops(6)
                .addStep(DeployerApplicationRecipe::new, s -> s.require(Items.GLOWSTONE_DUST))
                .addStep(FillingRecipe::new, s -> s.require(Fluids.LAVA, 50))
                .addStep(DeployerApplicationRecipe::new, s -> s.require(AllItems.PRECISION_MECHANISM.get()).toolNotConsumed())
                .addStep(PressingRecipe::new, s -> s)
                .addOutput(ModItems.RAW_METH.get(), 65)
                .addOutput(ModItems.RUINED_BATCH.get(), 35));
            create("mdma_batch", b -> b
                .require(Items.GLASS_BOTTLE)
                .transitionTo(ModItems.MDMA_BATCH.get())
                .loops(5)
                .addStep(DeployerApplicationRecipe::new, s -> s.require(Items.PRISMARINE_CRYSTALS))
                .addStep(FillingRecipe::new, s -> s.require(Fluids.WATER, 250))
                .addStep(DeployerApplicationRecipe::new, s -> s.require(AllItems.PRECISION_MECHANISM.get()).toolNotConsumed())
                .addStep(PressingRecipe::new, s -> s)
                .addOutput(ModItems.MDMA_CRYSTALS.get(), 70)
                .addOutput(ModItems.RUINED_BATCH.get(), 30));
            // The hardest line: ergot is rare, and four batches in ten are lost.
            create("lsd_batch", b -> b
                .require(Items.GLASS_BOTTLE)
                .transitionTo(ModItems.LSD_BATCH.get())
                .loops(3)
                .addStep(DeployerApplicationRecipe::new, s -> s.require(ModItems.ERGOT.get()))
                .addStep(DeployerApplicationRecipe::new, s -> s.require(Items.GLOW_INK_SAC))
                .addStep(FillingRecipe::new, s -> s.require(Fluids.WATER, 250))
                .addStep(DeployerApplicationRecipe::new, s -> s.require(AllItems.PRECISION_MECHANISM.get()).toolNotConsumed())
                .addOutput(ModItems.LSD_SOLUTION.get(), 60)
                .addOutput(ModItems.RUINED_BATCH.get(), 40));
        }
    }

    public static class Filling extends FillingRecipeGen {
        public Filling(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            create("bottling_beer", b -> b
                .require(Items.GLASS_BOTTLE)
                .require(ModFluids.BEER.get(), 250)
                .output(ModItems.BEER_BOTTLE.get()));

            create("canning_beer", b -> b
                .require(ModItems.EMPTY_CAN.get())
                .require(ModFluids.BEER.get(), 250)
                .output(ModItems.SEALED_CAN.get()));

            create("bottling_wine", b -> b
                .require(Items.GLASS_BOTTLE)
                .require(ModFluids.WINE.get(), 250)
                .output(ModItems.WINE_BOTTLE.get()));
            create("bottling_cognac", b -> b
                .require(Items.GLASS_BOTTLE)
                .require(ModFluids.COGNAC.get(), 250)
                .output(ModItems.COGNAC_BOTTLE.get()));
            create("bottling_whiskey", b -> b
                .require(Items.GLASS_BOTTLE)
                .require(ModFluids.WHISKEY.get(), 250)
                .output(ModItems.WHISKEY_BOTTLE.get()));
            create("bottling_doppelkorn", b -> b
                .require(Items.GLASS_BOTTLE)
                .require(ModFluids.DOPPELKORN.get(), 250)
                .output(ModItems.DOPPELKORN_BOTTLE.get()));
            create("bottling_vodka", b -> b
                .require(Items.GLASS_BOTTLE)
                .require(ModFluids.VODKA.get(), 250)
                .output(ModItems.VODKA_BOTTLE.get()));
            create("bottling_gin", b -> b
                .require(Items.GLASS_BOTTLE)
                .require(ModFluids.GIN.get(), 250)
                .output(ModItems.GIN_BOTTLE.get()));
            create("bottling_rum", b -> b
                .require(Items.GLASS_BOTTLE)
                .require(ModFluids.RUM.get(), 250)
                .output(ModItems.RUM_BOTTLE.get()));
            create("bottling_tequila", b -> b
                .require(Items.GLASS_BOTTLE)
                .require(ModFluids.TEQUILA.get(), 250)
                .output(ModItems.TEQUILA_BOTTLE.get()));
            create("filling_lachgas_balloon", b -> b
                .require(ModItems.BALLOON.get())
                .require(ModFluids.LACHGAS.get(), 250)
                .output(ModItems.LACHGAS_BALLOON.get()));
        }
    }

    /** Fermenter: hopped wort + yeast -> beer, one in-game day. */
    public static class Fermenting extends StandardProcessingRecipeGen<FermentingRecipe> {
        public Fermenting(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            create("fermenting_ale", b -> b
                .require(ModItems.YEAST.get())
                .require(ModFluids.HOPPED_WORT.get(), 500)
                .output(ModFluids.BEER.get(), 500)
                .duration(24000));
            create("fermenting_wine", b -> b
                .require(ModItems.YEAST.get())
                .require(ModFluids.GRAPE_MUST.get(), 1000)
                .output(ModFluids.WINE.get(), 1000)
                .duration(48000));
            create("fermenting_wash", b -> b
                .require(ModItems.YEAST.get())
                .require(ModFluids.WORT.get(), 1000)
                .output(ModFluids.WASH.get(), 1000)
                .duration(24000));
            create("fermenting_grain_wash", b -> b
                .require(ModItems.YEAST.get())
                .require(ModFluids.GRAIN_MASH.get(), 1000)
                .output(ModFluids.GRAIN_WASH.get(), 1000)
                .duration(24000));
            create("fermenting_potato_wash", b -> b
                .require(ModItems.YEAST.get())
                .require(ModFluids.POTATO_MASH.get(), 1000)
                .output(ModFluids.POTATO_WASH.get(), 1000)
                .duration(24000));
            create("fermenting_rum_wash", b -> b
                .require(ModItems.YEAST.get())
                .require(ModFluids.MOLASSES.get(), 1000)
                .output(ModFluids.RUM_WASH.get(), 1000)
                .duration(24000));
            create("fermenting_agave_wash", b -> b
                .require(ModItems.YEAST.get())
                .require(ModFluids.AGAVE_JUICE.get(), 1000)
                .output(ModFluids.AGAVE_WASH.get(), 1000)
                .duration(36000));
            create("aging_whiskey", b -> b
                .require(Items.OAK_PLANKS)
                .require(ModFluids.NEW_MAKE.get(), 1000)
                .output(ModFluids.WHISKEY.get(), 900)
                .duration(72000));
            create("aging_cognac", b -> b
                .require(Items.OAK_PLANKS)
                .require(ModFluids.EAU_DE_VIE.get(), 1000)
                .output(ModFluids.COGNAC.get(), 900)
                .duration(72000));
            create("aging_rum", b -> b
                .require(Items.OAK_PLANKS)
                .require(ModFluids.RAW_RUM.get(), 1000)
                .output(ModFluids.RUM.get(), 900)
                .duration(48000));
        }

        @Override
        protected IRecipeTypeInfo getRecipeType() {
            return ModRecipeTypes.FERMENTING;
        }
    }

    /** Kilning is a vanilla smoking recipe so the Blaze Burner bulk-processes it for free. */
    public static class Vanilla extends RecipeProvider {
        public Vanilla(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput output) {
            SimpleCookingRecipeBuilder.smoking(
                    Ingredient.of(ModItems.GREEN_MALT.get()),
                    RecipeCategory.FOOD,
                    ModItems.MALT.get(),
                    0.1f,
                    100)
                .unlockedBy("has_green_malt", has(ModItems.GREEN_MALT.get()))
                .save(output, CreateBrewery.ID("kilning"));

            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.IBUPROFEN.get(), 4)
                .requires(Items.PAPER)
                .requires(Items.SUGAR)
                .requires(Items.BONE_MEAL)
                .requires(Items.IRON_NUGGET)
                .unlockedBy("has_sugar", has(Items.SUGAR))
                .save(output, CreateBrewery.ID("ibuprofen"));

            // Drying the coca leaves: smoking, so an encased fan through a campfire does it in bulk.
            SimpleCookingRecipeBuilder.smoking(Ingredient.of(ModItems.COCA_LEAF.get()), RecipeCategory.MISC,
                    ModItems.DRIED_COCA_LEAF.get(), 0.1f, 100)
                .unlockedBy("has_coca_leaf", has(ModItems.COCA_LEAF.get()))
                .save(output, CreateBrewery.ID("drying_coca_leaf"));
            // Strecken: one Koks and 1..3 sugar, weaker by as much (KoksCutRecipe).
            net.minecraft.data.recipes.SpecialRecipeBuilder.special(com.createbrewery.drugs.KoksCutRecipe::new)
                .save(output, CreateBrewery.ID("koks_cut"));
            // Drying the trimmed buds: low heat and moving air, an encased fan through a campfire.
            SimpleCookingRecipeBuilder.smoking(Ingredient.of(ModItems.TRIMMED_BUDS.get()), RecipeCategory.MISC,
                    ModItems.DRIED_BUDS.get(), 0.1f, 200)
                .unlockedBy("has_trimmed_buds", has(ModItems.TRIMMED_BUDS.get()))
                .save(output, CreateBrewery.ID("drying_buds"));
            // The agave heart cooked slowly: an encased fan through a campfire, or a smoker.
            SimpleCookingRecipeBuilder.smoking(Ingredient.of(ModItems.AGAVE_HEART.get()), RecipeCategory.MISC,
                    ModItems.ROASTED_AGAVE.get(), 0.1f, 400)
                .unlockedBy("has_agave_heart", has(ModItems.AGAVE_HEART.get()))
                .save(output, CreateBrewery.ID("roasting_agave"));
            // Mushrooms and peyote are only dried: their strength is nature's, rolled on first use.
            SimpleCookingRecipeBuilder.smoking(Ingredient.of(ModItems.FRESH_MUSHROOMS.get()), RecipeCategory.MISC,
                    ModItems.MAGIC_MUSHROOM.get(), 0.1f, 200)
                .unlockedBy("has_fresh_mushrooms", has(ModItems.FRESH_MUSHROOMS.get()))
                .save(output, CreateBrewery.ID("drying_mushrooms"));
            SimpleCookingRecipeBuilder.smoking(Ingredient.of(ModItems.PEYOTE_BUTTON.get()), RecipeCategory.MISC,
                    ModItems.PEYOTE.get(), 0.1f, 200)
                .unlockedBy("has_peyote_button", has(ModItems.PEYOTE_BUTTON.get()))
                .save(output, CreateBrewery.ID("drying_peyote"));
            // A joint as strong as the weed rolled into it (RollJointRecipe).
            net.minecraft.data.recipes.SpecialRecipeBuilder.special(com.createbrewery.drugs.RollJointRecipe::new)
                .save(output, CreateBrewery.ID("roll_joint"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.STRING, 1)
                .requires(ModItems.HEMP_FIBER.get(), 3)
                .unlockedBy("has_hemp_fiber", has(ModItems.HEMP_FIBER.get()))
                .save(output, CreateBrewery.ID("string_from_hemp"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.SPACE_BROWNIE.get(), 2)
                .requires(Items.WHEAT)
                .requires(Items.COCOA_BEANS)
                .requires(Items.SUGAR)
                .requires(ModItems.WEED.get())
                .unlockedBy("has_weed", has(ModItems.WEED.get()))
                .save(output, CreateBrewery.ID("space_brownie"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BALLOON.get(), 4)
                .requires(Items.SLIME_BALL)
                .requires(Items.RED_DYE)
                .unlockedBy("has_slime_ball", has(Items.SLIME_BALL))
                .save(output, CreateBrewery.ID("balloon"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.NALOXON.get(), 1)
                .requires(Items.GHAST_TEAR)
                .requires(Items.GLASS_BOTTLE)
                .requires(Items.SUGAR)
                .unlockedBy("has_ghast_tear", has(Items.GHAST_TEAR))
                .save(output, CreateBrewery.ID("naloxon_crafting"));
            // Club gadgets
            ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.STROBE_LIGHT.get())
                .pattern("IGI").pattern("ILI").pattern("IRI")
                .define('I', AllItems.IRON_SHEET.get())
                .define('G', Items.GLASS)
                .define('L', Items.GLOWSTONE)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_glowstone", has(Items.GLOWSTONE))
                .save(output, CreateBrewery.ID("strobe_light"));
            ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.LASER_PROJECTOR.get())
                .pattern(" G ").pattern("AEA").pattern("III")
                .define('I', AllItems.IRON_SHEET.get())
                .define('G', Items.GLASS)
                .define('A', Items.AMETHYST_SHARD)
                .define('E', AllItems.ELECTRON_TUBE.get())
                .unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get()))
                .save(output, CreateBrewery.ID("laser_projector"));
            ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.FOG_MACHINE.get())
                .pattern("IPI").pattern("IBI").pattern("ICI")
                .define('I', AllItems.IRON_SHEET.get())
                .define('P', AllBlocks.FLUID_PIPE.get())
                .define('B', Items.BUCKET)
                .define('C', Items.CAMPFIRE)
                .unlockedBy("has_bucket", has(Items.BUCKET))
                .save(output, CreateBrewery.ID("fog_machine"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.TEST_KIT.get(), 1)
                .requires(Items.GLASS_BOTTLE)
                .requires(Items.PAPER)
                .requires(Items.REDSTONE)
                .requires(Items.LAPIS_LAZULI)
                .unlockedBy("has_glass_bottle", has(Items.GLASS_BOTTLE))
                .save(output, CreateBrewery.ID("drug_test_kit"));
        }
    }
}
