package com.createbrewery.block.club;

import com.createbrewery.effect.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A cryo CO2 cannon: fires while powered, like a real one fires while its valve is held open.
 * The jet itself - the plume, the roar - is drawn by {@link Co2JetBlockEntity} on each client;
 * the server only cools whoever stands in it.
 */
public class Co2JetBlock extends Block implements EntityBlock {

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ConsoleLink.onPlaced(level, pos, placer, stack);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (ConsoleLink.holdsLink(stack)) return ConsoleLink.use(level, pos, player, stack);
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 12, 14);

    /** How far the jet shoots in open air. */
    static final double MAX_REACH = 8.0;

    public Co2JetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.UP)
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
            .setValue(FACING, context.getClickedFace())
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), 3);
            if (powered) chill(level, pos, state.getValue(FACING));
        }
    }

    /** Something the gas cannot pass: anything with a collision shape, glass and closed doors included. */
    static boolean blocks(BlockGetter level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** From the nozzle's block centre to where the jet hits something, or {@link #MAX_REACH}. */
    static double reach(BlockGetter level, BlockPos pos, Direction facing) {
        for (int d = 1; d <= MAX_REACH; d++) {
            if (blocks(level, pos.relative(facing, d))) return Math.max(0.5, d - 0.6);
        }
        return MAX_REACH;
    }

    /** Server: puts out burning and overheated players in the plume, and under the spread where it hits a ceiling. */
    static void chill(Level level, BlockPos pos, Direction facing) {
        if (level.isClientSide) return;
        Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
        double reach = reach(level, pos, facing);
        Vec3 impact = Vec3.atCenterOf(pos).add(dir.scale(reach));
        double spread = Math.min(4.0, (MAX_REACH - reach) * 0.6);
        AABB plume = new AABB(pos).expandTowards(dir.scale(reach)).inflate(1.2)
            .minmax(new AABB(impact, impact).inflate(spread + 0.5));
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, plume)) {
            entity.clearFire();
            if (entity.hasEffect(ModEffects.HYPERTHERMIA)) entity.removeEffect(ModEffects.HYPERTHERMIA);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Co2JetBlockEntity(com.createbrewery.ModBlockEntities.CO2_JET.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof Co2JetBlockEntity jet) jet.tick(lvl, pos, st);
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
}
