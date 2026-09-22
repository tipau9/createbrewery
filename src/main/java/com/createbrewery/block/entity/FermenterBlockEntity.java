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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * Fermentation is driven by world time, not by a tick counter, so a batch keeps running
 * while its chunk is unloaded. Everything awkward that follows from that (a clock that
 * jumps, a saved recipe id that no longer resolves, an input that vanished mid-batch) is
 * handled here; the arithmetic itself lives in {@link FermentationProgress} so it can be
 * unit-tested without booting Minecraft.
 */
public class FermenterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public static final int TANK_CAPACITY = 1500;
    public static final long NOT_STARTED = FermentationProgress.NOT_STARTED;

    protected SmartFluidTankBehaviour inputTank;
    protected SmartFluidTankBehaviour outputTank;
    protected final ItemStackHandler yeastSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

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

    public SmartFluidTankBehaviour getInputTank() {
        return inputTank;
    }

    public SmartFluidTankBehaviour getOutputTank() {
        return outputTank;
    }

    public ItemStackHandler getYeastSlot() {
        return yeastSlot;
    }

    public long getStartedAt() {
        return startedAt;
    }

    /** Rewinds the batch clock. The GameTests use this to skip a day of ticks. */
    public void setStartedAt(long tick) {
        startedAt = tick;
        setChanged();
        sendData();
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
    protected FermentingRecipe currentRecipe() {
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
        if (recipe == null) {           // Review Focus #2
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

    /**
     * FermentingRecipe#matches always returns false, because a vanilla RecipeInput cannot
     * describe "one fluid tank plus one item slot". Matching is therefore done explicitly
     * against every loaded fermenting recipe, the same way Create's Basin does it.
     */
    private void tryStart() {
        FluidStack held = inputTank.getPrimaryHandler().getFluid();
        if (held.isEmpty()) return;
        ItemStack yeast = yeastSlot.getStackInSlot(0);
        if (yeast.isEmpty()) return;

        for (RecipeHolder<FermentingRecipe> holder : allFermentingRecipes()) {
            FermentingRecipe recipe = holder.value();
            if (!matchesContents(recipe, held, yeast)) continue;
            startedAt = level.getGameTime();
            activeRecipeId = holder.id();
            setChanged();
            sendData();
            return;
        }
    }

    private List<RecipeHolder<FermentingRecipe>> allFermentingRecipes() {
        return level.getRecipeManager()
            .getAllRecipesFor(ModRecipeTypes.FERMENTING.<RecipeInput, FermentingRecipe>getType());
    }

    private boolean matchesContents(FermentingRecipe recipe, FluidStack held, ItemStack yeast) {
        if (recipe.getFluidIngredients().size() != 1) return false;
        if (recipe.getIngredients().size() != 1) return false;
        if (recipe.getFluidResults().size() != 1) return false;
        return recipe.getIngredients().get(0).test(yeast) && fluidSufficient(recipe, held);
    }

    /** Review Focus #5: the player piped the wort back out mid-batch. */
    private boolean inputStillValid(FermentingRecipe recipe) {
        if (recipe.getFluidIngredients().size() != 1 || recipe.getFluidResults().size() != 1) return false;
        return fluidSufficient(recipe, inputTank.getPrimaryHandler().getFluid());
    }

    private boolean fluidSufficient(FermentingRecipe recipe, FluidStack held) {
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
        // Guarded deliberately: an absent tag must mean NOT_STARTED, not "started at world
        // time 0", which would report every freshly placed fermenter as instantly complete.
        startedAt = tag.contains("StartedAt") ? tag.getLong("StartedAt") : NOT_STARTED;
        activeRecipeId = tag.contains("Recipe")
            ? ResourceLocation.tryParse(tag.getString("Recipe"))   // malformed id yields null, not a throw
            : null;
        if (tag.contains("Yeast")) yeastSlot.deserializeNBT(registries, tag.getCompound("Yeast"));
    }
}
