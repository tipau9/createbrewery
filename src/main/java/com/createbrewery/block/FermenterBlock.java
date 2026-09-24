package com.createbrewery.block;

import com.createbrewery.ModBlockEntities;
import com.createbrewery.block.entity.FermenterBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

public class FermenterBlock extends Block implements IBE<FermenterBlockEntity> {

    /** Barrel-ish: a full-height block inset by one pixel on every side. */
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 16, 15);

    public FermenterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Without this, IBE.onRemove never runs, SmartBlockEntity#destroy is never called, and
     * breaking the block silently voids the yeast inside it.
     * IBE.onRemove must run before super, which is what clears the block entity.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection())) {
            return ItemInteractionResult.SUCCESS;
        }

        return onBlockEntityUseItemOn(level, pos, be -> {
            ItemStack inSlot = be.getYeastSlot().getStackInSlot(0);
            if (inSlot.isEmpty() && !stack.isEmpty()) {
                if (level.isClientSide) {
                    return ItemInteractionResult.SUCCESS;
                }
                ItemStack toInsert = stack.copyWithCount(1);
                ItemStack remainder = be.getYeastSlot().insertItem(0, toInsert, false);
                if (remainder.isEmpty()) {
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.2f);
                    return ItemInteractionResult.SUCCESS;
                }
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        });
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return onBlockEntityUse(level, pos, be -> {
            ItemStack inSlot = be.getYeastSlot().getStackInSlot(0);
            if (!inSlot.isEmpty()) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                ItemStack extracted = be.getYeastSlot().extractItem(0, 64, false);
                if (!extracted.isEmpty()) {
                    if (!player.addItem(extracted)) {
                        player.drop(extracted, false);
                    }
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }

    @Override
    public Class<FermenterBlockEntity> getBlockEntityClass() {
        return FermenterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FermenterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.FERMENTER.get();
    }
}
