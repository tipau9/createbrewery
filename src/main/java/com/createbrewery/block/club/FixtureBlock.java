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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * A club light run from a DMX console: an LED bar, a moving head, a blinder or a par can. Linked to
 * a console it follows its group there; on its own it runs a beat-synced show from the music near
 * it. Right-click to change its group (1..8), with linked fixtures in hand to link it.
 */
public class FixtureBlock extends Block implements EntityBlock {

    public enum Kind {
        /** Eight pixels in a row: chases and washes along a wall or truss. */
        LED_BAR(box(0, 0, 5, 16, 4, 11)),
        /** A narrow beam that pans and tilts through the haze. */
        MOVING_HEAD(box(3, 0, 3, 13, 14, 13)),
        /** Two warm tungsten lamps that blind the crowd on the drop. */
        BLINDER(box(1, 0, 4, 15, 6, 12)),
        /** A wide wash of colour. */
        PAR(box(4, 0, 4, 12, 12, 12));

        /** The shape lying on the floor, lens up; turned for the other facings. */
        final VoxelShape up;

        Kind(VoxelShape up) {
            this.up = up;
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final int MAX_LINK = 64;
    private static final String LINK = "LinkedConsole";

    public final Kind kind;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public FixtureBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
        var b = kind.up.bounds();
        double x0 = b.minX * 16, y0 = b.minY * 16, z0 = b.minZ * 16, x1 = b.maxX * 16, y1 = b.maxY * 16, z1 = b.maxZ * 16;
        // Mounted on the face opposite the lens; the shapes are symmetric across their width, so no flips needed.
        shapes.put(Direction.UP, kind.up);
        shapes.put(Direction.DOWN, box(x0, 16 - y1, z0, x1, 16 - y0, z1));
        shapes.put(Direction.NORTH, box(x0, z0, 16 - y1, x1, z1, 16 - y0));
        shapes.put(Direction.SOUTH, box(x0, z0, y0, x1, z1, y1));
        shapes.put(Direction.WEST, box(16 - y1, z0, x0, 16 - y0, z1, x1));
        shapes.put(Direction.EAST, box(y0, z0, x0, y1, z1, x1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Shining back where it was placed from: on a ceiling, down onto the floor.
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    // ---------------------------------------------------------------- linking

    static void link(ItemStack stack, BlockPos console) {
        SpeakerBlock.link(stack, console, LINK, "createbrewery.dmx.lore");
    }

    static Optional<BlockPos> linkOf(ItemStack stack) {
        return SpeakerBlock.linkOf(stack, LINK);
    }

    /** Server: links the placed fixture at {@code pos} to the console {@code stack} names, if it is there and close enough. */
    static boolean applyLink(Level level, BlockPos pos, ItemStack stack) {
        Optional<BlockPos> console = linkOf(stack);
        if (console.isEmpty() || !(level.getBlockEntity(pos) instanceof FixtureBlockEntity fixture)) return false;
        if (!console.get().closerThan(pos, MAX_LINK) || !(level.getBlockEntity(console.get()) instanceof DmxConsoleBlockEntity)) return false;
        fixture.setConsole(console.get());
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || linkOf(stack).isEmpty()) return;
        if (!applyLink(level, pos, stack) && placer instanceof Player player) {
            player.displayClientMessage(Component.translatable("createbrewery.dmx.too_far", MAX_LINK), true);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(stack.getItem() instanceof BlockItem item && item.getBlock() instanceof FixtureBlock) || linkOf(stack).isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            boolean linked = applyLink(level, pos, stack);
            player.displayClientMessage(Component.translatable(linked ? "createbrewery.dmx.relinked" : "createbrewery.dmx.too_far", MAX_LINK), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FixtureBlockEntity fixture)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown() && !level.isClientSide) {
            fixture.setGroup(fixture.getGroup() + 1);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.2f);
        }
        if (!level.isClientSide) {
            // Group as it is now (after the change); sneaking only shows it.
            BlockPos console = fixture.getConsole();
            player.displayClientMessage(console == null
                ? Component.translatable("createbrewery.dmx.fixture_standalone", fixture.getGroup() + 1)
                : Component.translatable("createbrewery.dmx.fixture_status", fixture.getGroup() + 1, console.getX(), console.getY(), console.getZ()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FixtureBlockEntity(ModBlockEntities.FIXTURE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // The light show is client-only: it follows what this client hears.
        if (!level.isClientSide) return null;
        return (l, p, s, be) -> { if (be instanceof FixtureBlockEntity f) f.clientTick(); };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
