package com.createbrewery.block;

import com.createbrewery.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * A patch of magic mushrooms, on mycelium, podzol or moss, in the dark (a cave or a cellar). It
 * fruits in flushes: grown, it is picked (by hand or a Create harvester) and comes again, three
 * times; then the patch is spent and gone. The flush is counted when it ripens, so a harvester
 * gets no more than a hand. No bone meal.
 */
public class PsilocybeBlock extends BushBlock {
    public static final MapCodec<PsilocybeBlock> CODEC = simpleCodec(PsilocybeBlock::new);
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int MAX_FLUSHES = 3;
    public static final IntegerProperty FLUSHES = IntegerProperty.create("flushes", 0, MAX_FLUSHES);
    static final float GROW_CHANCE = 1f / 14f;
    /** Mushrooms want it dark: no sun, no bright lamps. */
    public static final int MAX_LIGHT = 7;
    public static final int MIN_PICKED = 2, MAX_PICKED = 4;

    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 7, 13);

    public PsilocybeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(FLUSHES, 0));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, FLUSHES);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL) || state.is(Blocks.MOSS_BLOCK);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < MAX_AGE;
    }

    public static boolean canGrowAt(Level level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) <= MAX_LIGHT;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        // Picked after its last flush: the patch is spent.
        if (age == 0 && state.getValue(FLUSHES) >= MAX_FLUSHES) {
            level.removeBlock(pos, false);
            return;
        }
        if (age >= MAX_AGE || !canGrowAt(level, pos)) return;
        if (CommonHooks.canCropGrow(level, pos, state, random.nextFloat() < GROW_CHANCE)) {
            BlockState grown = state.setValue(AGE, age + 1);
            if (age + 1 == MAX_AGE) grown = grown.setValue(FLUSHES, state.getValue(FLUSHES) + 1);
            level.setBlock(pos, grown, Block.UPDATE_CLIENTS);
            CommonHooks.fireCropGrowPost(level, pos, grown);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) < MAX_AGE) return InteractionResult.PASS;
        if (!level.isClientSide) {
            popResource(level, pos, new ItemStack(ModItems.FRESH_MUSHROOMS.get(), MIN_PICKED + level.random.nextInt(MAX_PICKED - MIN_PICKED + 1)));
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1f, 0.8f + level.random.nextFloat() * 0.4f);
            level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
