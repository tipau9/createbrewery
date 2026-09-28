package com.createbrewery.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * A cannabis plant, grown like a real one: on watered farmland, under strong light (the sun, or
 * lamps in a grow room), about a Minecraft day a stage. From stage 3 it shows what it is - half of
 * all seeds come up male. A flowering male sheds pollen over every female in reach that is still
 * flowering, and seeded buds are fewer and weaker, so a grower pulls the males early. Cut at the
 * end, a female gives its buds, a male only fibre. No bone meal.
 */
public class CannabisPlantBlock extends BushBlock {
    public static final MapCodec<CannabisPlantBlock> CODEC = simpleCodec(CannabisPlantBlock::new);
    public static final int MAX_AGE = 5;
    /** From here on the sex shows. */
    public static final int SEXED = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
    public static final BooleanProperty FEMALE = BooleanProperty.create("female");
    public static final BooleanProperty SEEDED = BooleanProperty.create("seeded");
    static final float GROW_CHANCE = 1f / 18f;
    /** Strong light only: a sunny field or a lit grow room, not a torch in a cave. */
    public static final int MIN_LIGHT = 12;
    /** How far a flowering male's pollen carries, in blocks. */
    public static final int POLLEN_REACH = 8;

    private static final VoxelShape[] SHAPES = {
        box(5, 0, 5, 11, 5, 11), box(4, 0, 4, 12, 8, 12), box(3, 0, 3, 13, 11, 13),
        box(2, 0, 2, 14, 14, 14), box(2, 0, 2, 14, 16, 14), box(2, 0, 2, 14, 16, 14)};

    public CannabisPlantBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(FEMALE, true).setValue(SEEDED, false));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, FEMALE, SEEDED);
    }

    /** Regular seeds: a coin toss, decided as it goes in (and only seen once it shows). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FEMALE, context.getLevel().random.nextBoolean());
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
        return true;
    }

    /** Watered soil and strong light. */
    public static boolean canGrowAt(Level level, BlockPos pos) {
        BlockState soil = level.getBlockState(pos.below());
        return soil.getBlock() instanceof FarmBlock && soil.getValue(FarmBlock.MOISTURE) > 0
            && level.getRawBrightness(pos, 0) >= MIN_LIGHT;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        // Cut back and replanted (a Create harvester keeps the plant as a clone): a fresh start.
        if (age == 0 && state.getValue(SEEDED)) {
            state = state.setValue(SEEDED, false);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        if (!state.getValue(FEMALE) && age >= SEXED + 1) pollinate(level, pos);
        if (age >= MAX_AGE || !canGrowAt(level, pos)) return;
        if (CommonHooks.canCropGrow(level, pos, state, random.nextFloat() < GROW_CHANCE)) {
            BlockState grown = state.setValue(AGE, age + 1);
            level.setBlock(pos, grown, Block.UPDATE_CLIENTS);
            CommonHooks.fireCropGrowPost(level, pos, grown);
        }
    }

    /** A flowering male at {@code pos} seeds every female in reach that is flowering and not seeded yet. */
    public static void pollinate(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-POLLEN_REACH, -2, -POLLEN_REACH), pos.offset(POLLEN_REACH, 2, POLLEN_REACH))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof CannabisPlantBlock && s.getValue(FEMALE) && !s.getValue(SEEDED)
                && s.getValue(AGE) >= SEXED && s.getValue(AGE) < MAX_AGE) {
                level.setBlock(p, s.setValue(SEEDED, true), Block.UPDATE_CLIENTS);
            }
        }
    }
}
