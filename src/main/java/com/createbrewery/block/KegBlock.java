package com.createbrewery.block;

import com.createbrewery.ModBlockEntities;
import com.createbrewery.item.DrinkGlassItem;
import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class KegBlock extends Block implements IBE<KegBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 16, 14);

    public KegBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KegBlockEntity keg) {
            // Direct tapping into DrinkGlassItem
            if (stack.getItem() instanceof DrinkGlassItem glassItem && DrinkGlassItem.getContent(stack) == DrinkContent.EMPTY) {
                var handler = keg.getTank().getPrimaryHandler();
                FluidStack available = handler.getFluid();
                DrinkContent content = DrinkContent.fromFluid(available.getFluid());

                if (content != DrinkContent.EMPTY && handler.getFluidAmount() >= 250) {
                    if (!level.isClientSide) {
                        handler.drain(250, IFluidHandler.FluidAction.EXECUTE);
                        keg.notifyUpdate();

                        ItemStack filled = glassItem.withContent(content);
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                            if (!player.addItem(filled)) {
                                player.drop(filled, false);
                            }
                        } else if (!player.getInventory().contains(filled)) {
                            player.addItem(filled);
                        }
                    }

                    if (content.isBeer()) {
                        level.playSound(player, pos, ModSounds.BEER_OPEN.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
                        for (int i = 0; i < 6; i++) {
                            level.addParticle(ModParticles.BEER_FOAM.get(), pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                                (level.random.nextDouble() - 0.5) * 0.04, 0.03, (level.random.nextDouble() - 0.5) * 0.04);
                        }
                    } else {
                        level.playSound(player, pos, net.minecraft.sounds.SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
                    }

                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KegBlockEntity keg) {
            if (level.isClientSide) {
                FluidStack fluid = keg.getTank().getPrimaryHandler().getFluid();
                if (fluid.isEmpty()) {
                    player.displayClientMessage(Component.translatable("createbrewery.keg.empty"), true);
                } else {
                    player.displayClientMessage(Component.translatable("createbrewery.keg.status",
                        fluid.getHoverName(), fluid.getAmount(), KegBlockEntity.TANK_CAPACITY), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public Class<KegBlockEntity> getBlockEntityClass() {
        return KegBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KegBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BEER_KEG.get();
    }
}
