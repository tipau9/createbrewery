package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LaserProjectorBlock extends Block implements EntityBlock {

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ConsoleLink.onPlaced(level, pos, placer, stack);
    }

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE_NORTH = box(2, 2, 2, 14, 14, 16);
    private static final VoxelShape SHAPE_SOUTH = box(2, 2, 0, 14, 14, 14);
    private static final VoxelShape SHAPE_WEST  = box(2, 2, 2, 16, 14, 14);
    private static final VoxelShape SHAPE_EAST  = box(0, 2, 2, 14, 14, 14);
    private static final VoxelShape SHAPE_UP    = box(2, 0, 2, 14, 14, 14);
    private static final VoxelShape SHAPE_DOWN  = box(2, 2, 2, 14, 16, 14);

    public LaserProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(ACTIVE, true)
            .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, POWERED);
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
        return defaultBlockState()
            .setValue(FACING, dir)
            .setValue(POWERED, powered)
            .setValue(ACTIVE, true);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                // Like the fog machine: a redstone edge sets it (on with signal, off without),
                // sneak + empty hand toggles it; whichever came last wins.
                level.setBlock(pos, state.setValue(POWERED, powered).setValue(ACTIVE, powered), 3);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (ConsoleLink.holdsLink(stack)) return ConsoleLink.use(level, pos, player, stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof LaserProjectorBlockEntity projector)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Dyeing laser color
        if (stack.getItem() instanceof DyeItem dye) {
            int color = dye.getDyeColor().getTextureDiffuseColor();
            projector.setColor(color);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 0.8f, 1.1f);
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("createbrewery.laser.color_changed"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // Rainbow / Prism mode
        if (stack.is(Items.GLOW_INK_SAC)) {
            projector.setColor(-1); // -1 = Rainbow
            level.playSound(null, pos, SoundEvents.GLOW_INK_SAC_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("createbrewery.laser.rainbow_mode"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // Anything else held is used normally (placing blocks, the wrench).
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            boolean on = !state.getValue(ACTIVE);
            level.setBlock(pos, state.setValue(ACTIVE, on), 3);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, on ? 1.4f : 0.9f);
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable(on ? "createbrewery.laser.on" : "createbrewery.laser.off"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof LaserProjectorBlockEntity projector) {
            LaserPattern next = projector.getPattern().next();
            projector.setPattern(next);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.4f);
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("createbrewery.laser.pattern." + next.getSerializedName()), true);
            }
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
        return new LaserProjectorBlockEntity(com.createbrewery.ModBlockEntities.LASER_PROJECTOR.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        // Only drives the sweep and rainbow animation.
        if (!level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof LaserProjectorBlockEntity projector) {
                projector.tick();
            }
        };
    }
}
