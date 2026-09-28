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

/**
 * Cutting (Strecken): one Koks and one to three sugar make that many more Koks, each as weak as the
 * one line spread over them. Worth more on paper, less up the nose - and it needs testing again.
 */
public class KoksCutRecipe extends CustomRecipe {
    /** What an untested street line is taken to hold: about the middle of what street Koks is (see Purity#roll). */
    static final float STREET = 0.6f;

    public KoksCutRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return parts(input) != null;
    }

    /** The Koks and how much sugar goes in with it, or null if it is not one Koks and 1..3 sugar. */
    private static Object[] parts(CraftingInput input) {
        ItemStack koks = null;
        int sugar = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModItems.KOKS.get()) && koks == null) koks = s;
            else if (s.is(Items.SUGAR)) sugar++;
            else return null;
        }
        return koks == null || sugar < 1 || sugar > 3 ? null : new Object[] {koks, sugar};
    }

    /** Same on both sides (the client shows it before crafting): nothing rolled, an untested line counts as street. */
    public static float cut(float strength, int sugar) {
        return strength / (1 + sugar);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Object[] p = parts(input);
        if (p == null) return ItemStack.EMPTY;
        ItemStack koks = (ItemStack) p[0];
        int sugar = (Integer) p[1];
        Purity was = koks.get(Purity.PURITY.get());
        float strength = was == null ? STREET : was.strength();
        ItemStack out = new ItemStack(ModItems.KOKS.get(), 1 + sugar);
        out.set(Purity.PURITY.get(), new Purity(cut(strength, sugar), was != null && was.fentanyl(), false));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.koksCut();
    }
}
