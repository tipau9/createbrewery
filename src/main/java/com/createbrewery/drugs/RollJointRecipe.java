package com.createbrewery.drugs;

import com.createbrewery.ModItems;
import com.createbrewery.ModRecipeTypes;
import com.createbrewery.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Paper and weed rolled into a joint as strong as the weed in it (a street bag's is rolled on first
 * use, see Purity). Mixed with 1..2 tobacco (anything tagged {@code createbrewery:joint_tobacco},
 * e.g. Tobacconery's chopped tobacco) the same weed goes into a longer joint: that many times the
 * hits, each as much weaker - the weed stretched, not more of it.
 */
public class RollJointRecipe extends CustomRecipe {
    /** What an untested street bag is taken to hold once mixed: about the middle of street weed (see Purity#roll). */
    static final float STREET = 1.0f;
    public static final int MAX_TOBACCO = 2;

    public RollJointRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** The weed and how much tobacco goes in, or null unless it is one weed, one paper and 0..2 tobacco. */
    private static Object[] parts(CraftingInput input) {
        ItemStack weed = null;
        boolean paper = false;
        int tobacco = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModItems.WEED.get()) && weed == null) weed = s;
            else if (s.is(Items.PAPER) && !paper) paper = true;
            else if (s.is(ModTags.JOINT_TOBACCO)) tobacco++;
            else return null;
        }
        return weed == null || !paper || tobacco > MAX_TOBACCO ? null : new Object[] {weed, tobacco};
    }

    /** Strength per hit when one weed is mixed with {@code tobacco} tobacco. */
    public static float mixed(float strength, int tobacco) {
        return strength / (1 + tobacco);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return parts(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Object[] p = parts(input);
        if (p == null) return ItemStack.EMPTY;
        ItemStack weed = (ItemStack) p[0];
        int tobacco = (Integer) p[1];
        ItemStack joint = new ItemStack(ModItems.JOINT.get());
        Purity grown = weed.get(Purity.PURITY.get());
        if (tobacco == 0) {
            if (grown != null) joint.set(Purity.PURITY.get(), new Purity(grown.strength(), grown.fentanyl(), grown.tested()));
        } else {
            float strength = grown == null ? STREET : grown.strength();
            joint.set(Purity.PURITY.get(), new Purity(mixed(strength, tobacco), grown != null && grown.fentanyl(), false));
            joint.set(DataComponents.MAX_DAMAGE, joint.getMaxDamage() * (1 + tobacco));
        }
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
