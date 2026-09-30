package com.createbrewery.block.entity;

import com.createbrewery.Config;
import com.createbrewery.ModRecipeTypes;
import com.createbrewery.recipe.FermentationProgress;
import com.createbrewery.recipe.FermentingRecipe;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
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
            if (level != null && !level.isClientSide) sendData();
        }
    };

    /** What automation is allowed to see. Declared after yeastSlot, which it wraps. */
    private final IItemHandler yeastInsertionOnly = new InsertOnlyHandler(yeastSlot);

    private long startedAt = NOT_STARTED;
    private ResourceLocation activeRecipeId = null;

    /**
     * The scaled duration the server actually used, as last received over the sync packet.
     * FERMENTATION_DURATION_MULTIPLIER is a COMMON config, which NeoForge does not sync to
     * clients, so the client's own copy can silently disagree with the server's. The goggle
     * overlay runs client-side, so without this it would show a progress bar and countdown
     * computed from the wrong multiplier whenever a server sets one other than 1.0.
     */
    private int clientScaledDuration = FermentationProgress.NO_SYNCED_DURATION;

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

    /** The real slot. Internal use and tests only — never hand this to a capability. */
    public ItemStackHandler getYeastSlot() {
        return yeastSlot;
    }

    /**
     * The view automation gets: a funnel may feed the fermenter, a hopper may not rob it.
     *
     * Extraction failing safe is not good enough here. Pulling the yeast cancels the batch,
     * so a hopper placed under the fermenter silently costs the player a full in-game day —
     * including a finished batch being held back by a full output tank, which is exactly the
     * loss the hold in finish() exists to prevent.
     */
    public IItemHandler getYeastInsertionHandler() {
        return yeastInsertionOnly;
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
        int locallyComputed = (int) Math.max(1, Math.min(Integer.MAX_VALUE, scaled));
        boolean clientSide = level != null && level.isClientSide;
        return FermentationProgress.resolveScaledDuration(clientSide, clientScaledDuration, locallyComputed);
    }

    public float getProgress() {
        FermentingRecipe recipe = currentRecipe();
        if (recipe == null) return 0f;
        return FermentationProgress.progress(startedAt, level.getGameTime(), scaledDuration(recipe));
    }

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

        // One call, not two: containedFluidTooltip always prepends a "Fluid Container"
        // header, even for an empty tank (it only omits the header entirely when every tank
        // in the handler it's given is empty). Calling it once per tank would show that
        // generic header twice with nothing distinguishing input from output. The combined
        // wrapper is the same class ModBlockEntities already uses for the block capability.
        containedFluidTooltip(tooltip, isPlayerSneaking,
            new CombinedTankWrapper(inputTank.getCapability(), outputTank.getCapability()));
        return true;
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
        if (!contentsSatisfy(recipe)) { // Review Focus #5
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
        if (inputTank.getPrimaryHandler().getFluid().isEmpty()) return;
        if (yeastSlot.getStackInSlot(0).isEmpty()) return;

        for (RecipeHolder<FermentingRecipe> holder : allFermentingRecipes()) {
            FermentingRecipe recipe = holder.value();
            if (!contentsSatisfy(recipe)) continue;
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

    /**
     * The condition to START a batch and the condition to KEEP one are deliberately the same
     * predicate, not two similar ones. Review Focus #5 is only half the problem: if this
     * checked the fluid alone, a hopper could pull the yeast out mid-batch, finish() would
     * extract nothing, and that one yeast would go on to run unlimited batches.
     *
     * The arity guards run before any get(0), so a malformed recipe cannot throw inside tick().
     */
    private boolean contentsSatisfy(FermentingRecipe recipe) {
        if (recipe.getFluidIngredients().size() != 1) return false;
        if (recipe.getIngredients().size() != 1) return false;
        if (recipe.getFluidResults().size() != 1) return false;

        FluidStack held = inputTank.getPrimaryHandler().getFluid();
        return !held.isEmpty()
            && recipe.getFluidIngredients().get(0).test(held)
            && held.getAmount() >= recipe.getFluidIngredients().get(0).amount()
            && recipe.getIngredients().get(0).test(yeastSlot.getStackInSlot(0));
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

    /**
     * SmartBlockEntity#destroy only fans out to behaviours, and yeastSlot is a plain
     * ItemStackHandler rather than a behaviour, so without this the yeast is voided when the
     * block is broken. The tanks hold virtual fluids with no bucket item, so there is nothing
     * to drop for them.
     */
    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, yeastSlot);
    }

    /** NeoForge ships no insert-only view, and it is six forwarding methods. */
    private record InsertOnlyHandler(IItemHandler delegate) implements IItemHandler {
        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("StartedAt", startedAt);
        if (activeRecipeId != null) tag.putString("Recipe", activeRecipeId.toString());
        tag.put("Yeast", yeastSlot.serializeNBT(registries));

        // Only meaningful on a sync packet: this is always built server-side from the live
        // (server-authoritative) config, so the client never has to guess at the multiplier.
        if (clientPacket) {
            FermentingRecipe recipe = currentRecipe();
            if (recipe != null) tag.putInt("ScaledDuration", scaledDuration(recipe));
        }
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
        clientScaledDuration = tag.contains("ScaledDuration")
            ? tag.getInt("ScaledDuration")
            : FermentationProgress.NO_SYNCED_DURATION;
    }
}
