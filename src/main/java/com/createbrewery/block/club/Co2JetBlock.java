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

        if (level.isClientSide) {
            // Spawn high-velocity cryogenic gas plume
            double startX = pos.getX() + 0.5 + facing.getStepX() * 0.5;
            double startY = pos.getY() + 0.5 + facing.getStepY() * 0.5;
            double startZ = pos.getZ() + 0.5 + facing.getStepZ() * 0.5;

            for (int i = 0; i < 28; i++) {
                double speed = 0.55 + level.random.nextDouble() * 0.45;
                double spread = 0.08;
                double vx = facing.getStepX() * speed + (level.random.nextDouble() - 0.5) * spread;
                double vy = facing.getStepY() * speed + (level.random.nextDouble() - 0.5) * spread;
                double vz = facing.getStepZ() * speed + (level.random.nextDouble() - 0.5) * spread;

                level.addParticle(ModParticles.FOG.get(), startX, startY, startZ, vx, vy, vz);
                if (level.random.nextFloat() < 0.3f) {
                    level.addParticle(ParticleTypes.SNOWFLAKE, startX, startY, startZ, vx * 0.8, vy * 0.8, vz * 0.8);
                }
            }
        } else {
            // Thermal cooling on server: cool down overheated players standing in the plume
            AABB plumeBox = new AABB(pos).expandTowards(facing.getStepX() * 8, facing.getStepY() * 8, facing.getStepZ() * 8).inflate(1.2);
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
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
