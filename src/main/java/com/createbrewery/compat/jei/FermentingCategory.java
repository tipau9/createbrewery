package com.createbrewery.compat.jei;

import com.createbrewery.recipe.FermentingRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class FermentingCategory extends CreateRecipeCategory<FermentingRecipe> {

    public FermentingCategory(Info<FermentingRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FermentingRecipe recipe, IFocusGroup focuses) {
        // Fluid input slot (left)
        if (!recipe.getFluidIngredients().isEmpty()) {
            addFluidSlot(builder, 27, 21, recipe.getFluidIngredients().get(0));
        }

        // Yeast item input slot
        if (!recipe.getIngredients().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 55, 21)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().get(0));
        }

        // Fluid output slot (right)
        if (!recipe.getFluidResults().isEmpty()) {
            addFluidSlot(builder, 131, 21, recipe.getFluidResults().get(0));
        }
    }

    @Override
    public void draw(FermentingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_ARROW.render(graphics, 85, 25);

        // The local config: right in single player; on a server with another multiplier it is the best the client knows.
        int duration = (int) Math.round(recipe.getProcessingDuration() * com.createbrewery.Config.FERMENTATION_DURATION_MULTIPLIER.get());
        Component durationText;
        if (duration == 24000) {
            durationText = Component.translatable("createbrewery.jei.fermenting.one_day");
        } else if (duration % 24000 == 0) {
            durationText = Component.translatable("createbrewery.jei.fermenting.days", duration / 24000);
        } else {
            durationText = Component.translatable("createbrewery.jei.fermenting.days_decimal",
                String.format(Locale.ROOT, "%.1f", duration / 24000f));
        }

        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(durationText);
        int arrowCenterX = 85 + AllGuiTextures.JEI_ARROW.getWidth() / 2;
        int textX = arrowCenterX - textWidth / 2;
        int textY = 12;

        graphics.drawString(font, durationText, textX, textY, 0x555555, false);
    }
}
