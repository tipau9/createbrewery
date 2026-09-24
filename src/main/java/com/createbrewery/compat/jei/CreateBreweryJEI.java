package com.createbrewery.compat.jei;

import com.createbrewery.CreateBrewery;
import com.createbrewery.ModBlocks;
import com.createbrewery.ModRecipeTypes;
import com.createbrewery.recipe.FermentingRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class CreateBreweryJEI implements IModPlugin {
    private static final ResourceLocation ID = CreateBrewery.ID("jei_plugin");

    private final List<CreateRecipeCategory<?>> allCategories = new ArrayList<>();

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        allCategories.clear();
        allCategories.add(
            new CreateRecipeCategory.Builder<>(FermentingRecipe.class)
                .addTypedRecipes(ModRecipeTypes.FERMENTING)
                .catalyst(ModBlocks.FERMENTER::get)
                .itemIcon(ModBlocks.FERMENTER.get())
                .emptyBackground(177, 60)
                .build(CreateBrewery.ID("fermenting"), FermentingCategory::new)
        );

        registration.addRecipeCategories(allCategories.toArray(IRecipeCategory[]::new));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        allCategories.forEach(c -> c.registerRecipes(registration));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        allCategories.forEach(c -> c.registerCatalysts(registration));
    }
}
