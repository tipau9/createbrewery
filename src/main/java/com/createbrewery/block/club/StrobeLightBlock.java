package com.createbrewery.block.club;

import com.createbrewery.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class StrobeLightBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<StrobeMode> MODE = EnumProperty.create("mode", StrobeMode.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** How hard it flashes: 1 soft ... 5 blinding. Sneak + empty hand cycles it. */
    public static final IntegerProperty BRIGHTNESS = IntegerProperty.create("brightness", 1, 5);

    private static final VoxelShape SHAPE_NORTH = box(2, 2, 2, 14, 14, 16);
    private static final VoxelShape SHAPE_SOUTH = box(2, 2, 0, 14, 14, 14);
    private static final VoxelShape SHAPE_WEST  = box(2, 2, 2, 16, 14, 14);
    private static final VoxelShape SHAPE_EAST  = box(0, 2, 2, 14, 14, 14);
    private static final VoxelShape SHAPE_UP    = box(2, 0, 2, 14, 14, 14);
    private static final VoxelShape SHAPE_DOWN  = box(2, 2, 2, 14, 16, 14);

    public StrobeLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(MODE, StrobeMode.BEAT)
            .setValue(POWERED, false)
            .setValue(LIT, false)
            .setValue(BRIGHTNESS, 3));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, POWERED, LIT, BRIGHTNESS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction dir = context.getNearestLookingDirection().getOpposite();
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return lit(defaultBlockState()
            .setValue(FACING, dir)
            .setValue(POWERED, powered));
    }

    /** Real block light only in REDSTONE mode: the server cannot hear the music, so beats only flash the lens. */
    private static BlockState lit(BlockState state) {
        return state.setValue(LIT, state.getValue(MODE) == StrobeMode.REDSTONE && state.getValue(POWERED));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                level.setBlock(pos, lit(state.setValue(POWERED, powered)), 3);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Only an empty hand switches modes (sneaking: brightness); anything held is used normally (placing blocks, the wrench).
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            int next = state.getValue(BRIGHTNESS) % 5 + 1;
            level.setBlock(pos, state.setValue(BRIGHTNESS, next), 3);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 0.8f + next * 0.15f);
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("createbrewery.strobe.brightness." + next), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        StrobeMode next = state.getValue(MODE).next();
        level.setBlock(pos, lit(state.setValue(MODE, next)), 3);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.2f);
        if (level.isClientSide) {
            player.displayClientMessage(Component.translatable("createbrewery.strobe.mode." + next.getSerializedName()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StrobeLightBlockEntity(ModBlockEntities.STROBE_LIGHT.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        // The flash is client-only: it follows what this client hears.
        if (!level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof StrobeLightBlockEntity strobe) {
                strobe.tick(st);
            }
        };
    }
}
