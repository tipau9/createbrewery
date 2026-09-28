package com.createbrewery.block;

import com.createbrewery.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * A coca bush. It only grows in a jungle, and slowly: about one stage a Minecraft day, so four days
 * from a cut-back bush to a crop of leaves. Picking the leaves (by hand, or a Create harvester) cuts
 * it back to the start. No bone meal. The property is named {@code age} and it has no collision, so a
 * harvester only takes grown bushes and replants them (see HarvesterMovementBehaviour).
 */
public class CocaBushBlock extends BushBlock {
    public static final MapCodec<CocaBushBlock> CODEC = simpleCodec(CocaBushBlock::new);
    public static final int MAX_AGE = 4;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;
    /** One stage per ~18 random ticks: about a Minecraft day at the default random tick speed. */
    static final float GROW_CHANCE = 1f / 18f;
    /** Leaves picked off a grown bush; the loot table drops the same. */
    public static final int MIN_LEAVES = 3, MAX_LEAVES = 6;

    private static final VoxelShape SMALL = box(4, 0, 4, 12, 8, 12), BIG = box(1, 0, 1, 15, 16, 15);

    public CocaBushBlock(Properties properties) {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AGE) < 2 ? SMALL : BIG;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < MAX_AGE;
    }

    /** Warm, wet and bright enough: a jungle, with light on it. */
    public static boolean canGrowAt(Level level, BlockPos pos) {
        return level.getBiome(pos).is(BiomeTags.IS_JUNGLE) && level.getRawBrightness(pos.above(), 0) >= 9;
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) < MAX_AGE) return InteractionResult.PASS;
        if (!level.isClientSide) {
            int leaves = MIN_LEAVES + level.random.nextInt(MAX_LEAVES - MIN_LEAVES + 1);
            popResource(level, pos, new ItemStack(ModItems.COCA_LEAF.get(), leaves));
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1f, 0.8f + level.random.nextFloat() * 0.4f);
            // Cut back to the start, as a harvester leaves it.
            level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
