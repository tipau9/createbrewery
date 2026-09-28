package com.createbrewery.drugs;

import com.createbrewery.ModItems;
import com.createbrewery.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Paper and weed rolled into a joint that is as strong as the weed in it (a street bag's is rolled on first use, see Purity). */
public class RollJointRecipe extends CustomRecipe {
    public RollJointRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** The weed, or null if the grid is not exactly one paper and one weed. */
    private static ItemStack weed(CraftingInput input) {
        ItemStack weed = null;
        boolean paper = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModItems.WEED.get()) && weed == null) weed = s;
            else if (s.is(Items.PAPER) && !paper) paper = true;
            else return null;
        }
        return paper ? weed : null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return weed(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack weed = weed(input);
        if (weed == null) return ItemStack.EMPTY;
        ItemStack joint = new ItemStack(ModItems.JOINT.get());
        Purity grown = weed.get(Purity.PURITY.get());
        if (grown != null) joint.set(Purity.PURITY.get(), new Purity(grown.strength(), grown.fentanyl(), grown.tested()));
        return joint;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.rollJoint();
    }
}
