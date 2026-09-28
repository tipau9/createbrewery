package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A microphone on a stand, linked to a DJ booth like its speakers. Switched on, whoever talks into
 * it with Simple Voice Chat is heard out of the booth's speakers (see BreweryVoicePlugin); without
 * Simple Voice Chat it is only a prop. Right-click switches it on and off.
 */
public class MicrophoneBlock extends SpeakerBlock {
    public static final BooleanProperty ON = BooleanProperty.create("on");
    /** How close to the mic you must stand to be picked up. */
    public static final double REACH = 2.0;

    private static final VoxelShape SHAPE = box(5, 0, 5, 11, 15, 11);

    public MicrophoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ON, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ON);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Whether a player is currently standing in front of a switched-on microphone. */
    public static boolean isAtActiveMicrophone(@Nullable Player player) {
        if (player == null || player.level() == null) return false;
        Vec3 eye = player.getEyePosition();
        BlockPos center = BlockPos.containing(eye);
        int r = (int) Math.ceil(REACH);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(com.createbrewery.ModBlocks.MICROPHONE.get()) && state.hasProperty(ON) && state.getValue(ON)) {
                Vec3 head = Vec3.atCenterOf(pos).add(0, 0.4, 0);
                if (eye.distanceToSqr(head) <= REACH * REACH) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean on = !state.getValue(ON);
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(ON, on), 3);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, on ? 1.3f : 0.8f);
        } else {
            boolean linked = level.getBlockEntity(pos) instanceof SpeakerBlockEntity mic && mic.getBooth() != null;
            player.displayClientMessage(Component.translatable(!on ? "createbrewery.mic.off"
                : linked ? "createbrewery.mic.on" : "createbrewery.mic.on_standalone"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MicrophoneBlockEntity(com.createbrewery.ModBlockEntities.MICROPHONE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                                        net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof MicrophoneBlockEntity mic) mic.serverTick();
        };
    }
}
