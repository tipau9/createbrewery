package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The amp rack: linked to a DJ booth like its speakers, it splits the sound at a crossover - the
 * lows to the booth's subwoofers, the rest to its speakers - and sets how loud each side plays.
 * Right-click to open it.
 */
public class AmpRackBlock extends SpeakerBlock {

    public AmpRackBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) AmpRackScreen.open(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmpRackBlockEntity(com.createbrewery.ModBlockEntities.AMP_RACK.get(), pos, state);
    }
}
