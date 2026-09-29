package com.createbrewery.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * An opium poppy: on watered farmland in full sun, about a Minecraft day a stage, flowering and
 * then setting its seed pod. Cut ripe it gives the pods (and its seed back), young only the seed.
 * No bone meal.
 */
public class OpiumPoppyBlock extends BushBlock {
    public static final MapCodec<OpiumPoppyBlock> CODEC = simpleCodec(OpiumPoppyBlock::new);
    public static final int MAX_AGE = 4;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;
    static final float GROW_CHANCE = 1f / 18f;
    public static final int MIN_LIGHT = 12;

    private static final VoxelShape[] SHAPES = {
        box(5, 0, 5, 11, 4, 11), box(4, 0, 4, 12, 8, 12), box(4, 0, 4, 12, 12, 12),
        box(3, 0, 3, 13, 15, 13), box(3, 0, 3, 13, 15, 13)};

    public OpiumPoppyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getBlock() instanceof FarmBlock;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AGE)];
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < MAX_AGE;
    }

    public static boolean canGrowAt(Level level, BlockPos pos) {
        BlockState soil = level.getBlockState(pos.below());
        return soil.getBlock() instanceof FarmBlock && soil.getValue(FarmBlock.MOISTURE) > 0
            && level.getRawBrightness(pos, 0) >= MIN_LIGHT;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age >= MAX_AGE || !canGrowAt(level, pos)) return;
        if (CommonHooks.canCropGrow(level, pos, state, random.nextFloat() < GROW_CHANCE)) {
            BlockState grown = state.setValue(AGE, age + 1);
            level.setBlock(pos, grown, Block.UPDATE_CLIENTS);
            CommonHooks.fireCropGrowPost(level, pos, grown);
        }
    }
}
