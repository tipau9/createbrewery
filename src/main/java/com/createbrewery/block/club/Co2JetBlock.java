package com.createbrewery.block.club;

import com.createbrewery.effect.ModEffects;
import com.createbrewery.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Co2JetBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 12, 14);

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
            if (powered) {
                fireJet(level, pos, state.getValue(FACING));
            }
        }
    }

    public static void fireJet(Level level, BlockPos pos, Direction facing) {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.6f, 1.8f);

        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Check physical obstacles along the jet trajectory so it collides realistically with walls/ceilings
            double maxReach = 8.0;
            for (double d = 1.0; d <= 8.0; d += 1.0) {
                BlockPos checkPos = pos.relative(facing, (int) Math.round(d));
                if (level.getBlockState(checkPos).isSolidRender(level, checkPos)) {
                    maxReach = Math.max(0.6, d - 0.2);
                    break;
                }
            }

            // High-pressure cryo gas blast sent from server to all clients
            for (double d = 0.5; d <= maxReach; d += 0.4) {
                double px = pos.getX() + 0.5 + facing.getStepX() * d;
                double py = pos.getY() + 0.5 + facing.getStepY() * d;
                double pz = pos.getZ() + 0.5 + facing.getStepZ() * d;
                serverLevel.sendParticles(ParticleTypes.CLOUD, px, py, pz, 6, 0.18, 0.18, 0.18, 0.05);
                serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, px, py, pz, 4, 0.12, 0.12, 0.12, 0.02);
                serverLevel.sendParticles(ParticleTypes.POOF, px, py, pz, 3, 0.15, 0.15, 0.15, 0.04);
            }

            // Thermal cooling on server: cool down overheated players standing in the plume
            AABB plumeBox = new AABB(pos).expandTowards(facing.getStepX() * maxReach, facing.getStepY() * maxReach, facing.getStepZ() * maxReach).inflate(1.2);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, plumeBox);
            for (LivingEntity entity : targets) {
                entity.clearFire();
                if (entity.hasEffect(ModEffects.HYPERTHERMIA)) {
                    entity.removeEffect(ModEffects.HYPERTHERMIA);
                }
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(POWERED)) {
            Direction facing = state.getValue(FACING);
            BlockPos inFront = pos.relative(facing);
            if (level.getBlockState(inFront).isSolidRender(level, inFront)) {
                return; // Nozzle blocked directly by a wall
            }

            double startX = pos.getX() + 0.5 + facing.getStepX() * 0.7;
            double startY = pos.getY() + 0.5 + facing.getStepY() * 0.7;
            double startZ = pos.getZ() + 0.5 + facing.getStepZ() * 0.7;

            for (int i = 0; i < 8; i++) {
                double speed = 0.45 + random.nextDouble() * 0.35;
                double spread = 0.12;
                double vx = facing.getStepX() * speed + (random.nextDouble() - 0.5) * spread;
                double vy = facing.getStepY() * speed + (random.nextDouble() - 0.5) * spread;
                double vz = facing.getStepZ() * speed + (random.nextDouble() - 0.5) * spread;
                level.addParticle(ParticleTypes.CLOUD, startX, startY, startZ, vx, vy, vz);
                if (random.nextFloat() < 0.45f) {
                    level.addParticle(ParticleTypes.SNOWFLAKE, startX, startY, startZ, vx * 0.8, vy * 0.8, vz * 0.8);
                }
            }
        }
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
