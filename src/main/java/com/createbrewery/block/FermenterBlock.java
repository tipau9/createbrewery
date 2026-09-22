package com.createbrewery.block;

import com.createbrewery.ModBlockEntities;
import com.createbrewery.block.entity.FermenterBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    @Override
    public Class<FermenterBlockEntity> getBlockEntityClass() {
        return FermenterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FermenterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.FERMENTER.get();
    }
}
