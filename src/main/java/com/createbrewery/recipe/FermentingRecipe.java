package com.createbrewery.recipe;

import com.createbrewery.ModRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;

public class FermentingRecipe extends StandardProcessingRecipe<RecipeInput> {

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
