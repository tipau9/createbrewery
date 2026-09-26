package com.createbrewery.data;

import com.createbrewery.CreateBrewery;
import com.createbrewery.ModFluids;
import com.createbrewery.ModItems;
import com.createbrewery.ModRecipeTypes;
import com.createbrewery.recipe.FermentingRecipe;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.FillingRecipeGen;
import com.simibubi.create.api.data.recipe.HauntingRecipeGen;
import com.simibubi.create.api.data.recipe.MillingRecipeGen;
import com.simibubi.create.api.data.recipe.MixingRecipeGen;
import com.simibubi.create.api.data.recipe.PressingRecipeGen;
import com.simibubi.create.api.data.recipe.StandardProcessingRecipeGen;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
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
        generator.addProvider(server, new Filling(output, registries));
        generator.addProvider(server, new Fermenting(output, registries));
        generator.addProvider(server, new Haunting(output, registries));
        generator.addProvider(server, new Vanilla(output, registries));
    }

    /** Basin (Mixer) recipes: steeping, mashing, the boil, yeast culture. */
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
            create("heroin", b -> b
                .require(Items.POPPY).require(Items.POPPY).require(Items.POPPY)
                .require(Items.GLASS_BOTTLE)
                .output(ModItems.HEROIN.get())
                .requiresHeat(HeatCondition.HEATED));
            create("lsd", b -> b
                .require(Items.PAPER).require(Items.PURPLE_DYE).require(Items.ENDER_EYE)
                .output(ModItems.LSD.get(), 9));
            create("mdma", b -> b
                .require(Items.AMETHYST_SHARD).require(Items.SUGAR).require(Items.PINK_DYE)
                .output(ModItems.MDMA.get(), 3));
            create("dmt", b -> b
                .require(Items.CHORUS_FRUIT).require(Items.GLOW_BERRIES).require(Items.GLASS_BOTTLE)
                .output(ModItems.DMT.get())
                .requiresHeat(HeatCondition.SUPERHEATED));
            create("meth", b -> b
                .require(Items.SUGAR).require(Items.REDSTONE).require(Items.BLUE_DYE)
                .output(ModItems.METH.get(), 2)
                .requiresHeat(HeatCondition.SUPERHEATED));
            create("xanax", b -> b
                .require(Items.BONE_MEAL).require(Items.SUGAR).require(Items.LAPIS_LAZULI)
                .output(ModItems.XANAX.get(), 4));
            create("naloxon", b -> b
                .require(Items.GHAST_TEAR).require(Items.GLASS_BOTTLE).require(Items.SUGAR)
                .output(ModItems.NALOXON.get(), 2));
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
        }
    }

    /** Spout: bottles and cans get filled with beer. */
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
        }

        @Override
        protected IRecipeTypeInfo getRecipeType() {
            return ModRecipeTypes.FERMENTING;
        }
    }

    /** Encased Fan through soul fire: ordinary plants turn into something else. */
    public static class Haunting extends HauntingRecipeGen {
        public Haunting(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, CreateBrewery.MOD_ID);

            create("magic_mushroom", b -> b
                .require(Items.BROWN_MUSHROOM)
                .output(ModItems.MAGIC_MUSHROOM.get()));
            create("peyote", b -> b
                .require(Items.CACTUS)
                .output(ModItems.PEYOTE.get()));
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

            // Made-up game recipes, nothing like a real process.
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.KOKS.get(), 2)
                .requires(Items.SUGAR)
                .requires(Items.BONE_MEAL)
                .requires(Items.GLOWSTONE_DUST)
                .unlockedBy("has_glowstone_dust", has(Items.GLOWSTONE_DUST))
                .save(output, CreateBrewery.ID("koks"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.KETA.get(), 2)
                .requires(Items.AMETHYST_SHARD)
                .requires(Items.SUGAR)
                .requires(Items.SLIME_BALL)
                .unlockedBy("has_amethyst_shard", has(Items.AMETHYST_SHARD))
                .save(output, CreateBrewery.ID("keta"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.JOINT.get(), 1)
                .requires(Items.PAPER)
                .requires(ModItems.HOP_CONES.get())
                .requires(Items.DRIED_KELP)
                .unlockedBy("has_hop_cones", has(ModItems.HOP_CONES.get()))
                .save(output, CreateBrewery.ID("joint"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BALLOON.get(), 4)
                .requires(Items.SLIME_BALL)
                .requires(Items.RED_DYE)
                .unlockedBy("has_slime_ball", has(Items.SLIME_BALL))
                .save(output, CreateBrewery.ID("balloon"));
        }
    }
}
