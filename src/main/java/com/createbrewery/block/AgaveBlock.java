package com.createbrewery.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A blue agave: grows like peyote (sand, desert or badlands, sun, very slowly), but it is not cut
 * back - the heart is the whole plant. Harvested, it gives its heart and a pup to plant again.
 */
public class AgaveBlock extends PeyoteBlock {
    public static final MapCodec<AgaveBlock> CODEC = simpleCodec(AgaveBlock::new);

    private static final VoxelShape SMALL = box(4, 0, 4, 12, 6, 12), BIG = box(1, 0, 1, 15, 14, 15);

    public AgaveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AGE) < 2 ? SMALL : BIG;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
