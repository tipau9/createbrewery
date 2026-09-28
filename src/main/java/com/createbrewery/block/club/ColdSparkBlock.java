package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A cold spark fountain (a "sparkular"): a fan blows granules through a heater and they leave as a
 * fountain of glowing sparks - cold ones, which set nothing alight. Fires while powered, and on its
 * own at the drops of the music near it while AUTO is on. Right-click switches AUTO, shift-right-
 * click the fountain's height.
 */
public class ColdSparkBlock extends Co2JetBlock {
    public static final BooleanProperty AUTO = BooleanProperty.create("auto");
    /** How high the fountain shoots, in blocks. */
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 1, 5);

    public ColdSparkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AUTO, true).setValue(HEIGHT, 3));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AUTO, HEIGHT);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) level.setBlock(pos, state.setValue(POWERED, powered), 3);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockState next = player.isShiftKeyDown()
            ? state.setValue(HEIGHT, state.getValue(HEIGHT) % 5 + 1)
            : state.setValue(AUTO, !state.getValue(AUTO));
        if (!level.isClientSide) {
            level.setBlock(pos, next, 3);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.2f);
        } else {
            player.displayClientMessage(player.isShiftKeyDown()
                ? Component.translatable("createbrewery.cold_spark.height", next.getValue(HEIGHT))
                : Component.translatable(next.getValue(AUTO) ? "createbrewery.cold_spark.auto_on" : "createbrewery.cold_spark.auto_off"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColdSparkBlockEntity(com.createbrewery.ModBlockEntities.COLD_SPARK.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Sparks and sound are drawn on each client; the server has nothing to do.
        return level.isClientSide ? (lvl, pos, st, be) -> {
            if (be instanceof ColdSparkBlockEntity sparks) sparks.clientTick(lvl, pos, st);
        } : null;
    }
}
