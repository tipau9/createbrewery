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
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DjBoothBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 14, 16);

    public DjBoothBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(POWERED, false);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    public static boolean isMusicDisc(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (com.createbrewery.compat.EtchedCompat.isEtchedDisc(stack)) return true;
        if (stack.has(DataComponents.JUKEBOX_PLAYABLE)) return true;
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String p = id.getPath();
            if (p.contains("music_disc") || p.contains("record") || p.contains("disc")) return true;
        }
        return stack.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.withDefaultNamespace("music_discs")));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DjBoothBlockEntity dj)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Inserting Music Disc
        if (isMusicDisc(stack)) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }

            Direction facing = state.getValue(FACING);
            double hitX = hitResult.getLocation().x - pos.getX();
            double hitZ = hitResult.getLocation().z - pos.getZ();
            double localX = getLocalX(facing, hitX, hitZ);

            boolean preferDeckB = (localX > 0.05);
            boolean inserted = dj.insertDisc(stack, preferDeckB, player);
            if (inserted) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.8f, 1.2f);
                return ItemInteractionResult.SUCCESS;
            } else {
                player.displayClientMessage(Component.translatable("createbrewery.dj.decks_full"), true);
                return ItemInteractionResult.SUCCESS;
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DjBoothBlockEntity dj)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            // Shift click: Eject discs
            dj.ejectDiscs(player);
            return InteractionResult.SUCCESS;
        }

        // Relative coordinates on block top face (-0.5 .. 0.5)
        Direction facing = state.getValue(FACING);
        double hitX = hitResult.getLocation().x - pos.getX();
        double hitZ = hitResult.getLocation().z - pos.getZ();
        double localX = getLocalX(facing, hitX, hitZ);

        if (localX < -0.15) {
            // Left platter: Deck A
            dj.playDeck(true, player);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6f, 1.2f);
            return InteractionResult.SUCCESS;
        } else if (localX > 0.15) {
            // Right platter: Deck B
            dj.playDeck(false, player);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6f, 1.2f);
            return InteractionResult.SUCCESS;
        } else {
            // Center button: Trigger BEAT DROP!
            dj.triggerDrop(player);
            return InteractionResult.SUCCESS;
        }
    }

    private static double getLocalX(Direction facing, double hitX, double hitZ) {
        return switch (facing) {
            case NORTH -> 0.5 - hitX;
            case SOUTH -> hitX - 0.5;
            case WEST  -> hitZ - 0.5;
            case EAST  -> 0.5 - hitZ;
            default    -> hitX - 0.5;
        };
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
        return new DjBoothBlockEntity(com.createbrewery.ModBlockEntities.DJ_BOOTH.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return (lvl, pos, st, be) -> {
            if (be instanceof DjBoothBlockEntity dj) {
                dj.tick(lvl, pos, st);
            }
        };
    }
}
