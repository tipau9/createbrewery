package com.createbrewery.block;

import com.createbrewery.ModBlockEntities;
import com.createbrewery.ModItems;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BeerTapBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = box(4, 0, 4, 12, 14, 12);

    public BeerTapBlock(Properties properties) {
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BeerTapBlockEntity tap)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Bucket interaction (Beer Bucket / Empty Bucket)
        if (stack.is(Items.BUCKET) || com.createbrewery.ModFluids.BEER.getBucket().map(stack::is).orElse(false)) {
            // FluidUtil plays the bucket sound itself.
            if (net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, tap.getTank().getCapability())) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // Draft into Glass Bottle -> Beer Bottle
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (tap.dispenseBeer(250)) {
                if (!level.isClientSide) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    ItemStack beer = new ItemStack(ModItems.BEER_BOTTLE.get());
                    if (!player.addItem(beer)) {
                        player.drop(beer, false);
                    }
                }
                tap.spawnFoamParticles(level, pos, state.getValue(FACING));
                level.playSound(player, pos, ModSounds.BEER_OPEN.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
                if (level.isClientSide) {
                    player.displayClientMessage(Component.translatable("createbrewery.beer_tap.poured"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            } else if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("createbrewery.beer_tap.empty"), true);
                return ItemInteractionResult.sidedSuccess(true);
            }
        }

        // Draft into Empty Can -> Sealed Beer Can
        if (stack.is(ModItems.EMPTY_CAN.get())) {
            if (tap.dispenseBeer(250)) {
                if (!level.isClientSide) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    ItemStack can = new ItemStack(ModItems.SEALED_CAN.get());
                    if (!player.addItem(can)) {
                        player.drop(can, false);
                    }
                }
                tap.spawnFoamParticles(level, pos, state.getValue(FACING));
                level.playSound(player, pos, ModSounds.BEER_OPEN.get(), SoundSource.BLOCKS, 0.8f, 1.1f);
                if (level.isClientSide) {
                    player.displayClientMessage(Component.translatable("createbrewery.beer_tap.poured"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            } else if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("createbrewery.beer_tap.empty"), true);
                return ItemInteractionResult.sidedSuccess(true);
            }
        }

        // Draft into Drink Glass -> Filled Drink Glass (Beer)
        if (stack.getItem() instanceof com.createbrewery.item.DrinkGlassItem glassItem
            && com.createbrewery.item.DrinkGlassItem.getContent(stack) == com.createbrewery.block.DrinkContent.EMPTY) {
            if (tap.dispenseBeer(250)) {
                if (!level.isClientSide) {
                    ItemStack filled = glassItem.withContent(com.createbrewery.block.DrinkContent.BEER);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                        if (!player.addItem(filled)) {
                            player.drop(filled, false);
                        }
                    } else if (!player.getInventory().contains(filled)) {
                        player.addItem(filled);
                    }
                }
                tap.spawnFoamParticles(level, pos, state.getValue(FACING));
                level.playSound(player, pos, ModSounds.BEER_OPEN.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
                if (level.isClientSide) {
                    player.displayClientMessage(Component.translatable("createbrewery.beer_tap.poured"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            } else if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("createbrewery.beer_tap.empty"), true);
                return ItemInteractionResult.sidedSuccess(true);
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BeerTapBlockEntity tap) {
            if (level.isClientSide) {
                int amount = tap.getTank().getPrimaryHandler().getFluidAmount();
                player.displayClientMessage(Component.translatable("createbrewery.beer_tap.status", amount, BeerTapBlockEntity.TANK_CAPACITY), true);
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeerTapBlockEntity(ModBlockEntities.BEER_TAP.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> blockEntityType) {
        return (lvl, p, st, be) -> {
            if (be instanceof BeerTapBlockEntity tap) {
                tap.tick();
            }
        };
    }
}
