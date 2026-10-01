package com.createbrewery.block;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.item.BeerDrinkItem;
import com.createbrewery.item.DrinkGlassItem;
import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DrinkGlassBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DrinkContent> CONTENT = EnumProperty.create("content", DrinkContent.class);

    private static final VoxelShape SHAPE_MUG = box(5, 0, 5, 11, 10, 11);
    private static final VoxelShape SHAPE_SHOT = box(6, 0, 6, 10, 6, 10);
    private static final VoxelShape SHAPE_COCKTAIL = box(5, 0, 5, 11, 11, 11);

    private final GlassType glassType;

    public DrinkGlassBlock(Properties properties, GlassType glassType) {
        super(properties);
        this.glassType = glassType;
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(CONTENT, DrinkContent.EMPTY));
    }

    public GlassType getGlassType() {
        return glassType;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONTENT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (glassType) {
            case BEER_MUG -> SHAPE_MUG;
            case SHOT_GLASS -> SHAPE_SHOT;
            case COCKTAIL_GLASS -> SHAPE_COCKTAIL;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportRigidBlock(level, pos.below());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        DrinkContent content = DrinkGlassItem.getContent(context.getItemInHand());
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(CONTENT, content);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        DrinkContent current = state.getValue(CONTENT);

        // 1. If glass is EMPTY, can we fill it from held bottle / can / item?
        if (current == DrinkContent.EMPTY) {
            DrinkContent fromItem = DrinkContent.fromItem(stack);
            if (fromItem != DrinkContent.EMPTY) {
                if (!level.isClientSide) {
                    if (stack.getItem() instanceof BeerDrinkItem) {
                        if (!player.getAbilities().instabuild) {
                            if (stack.isDamageableItem()) {
                                int newDamage = stack.getDamageValue() + 1;
                                if (newDamage >= stack.getMaxDamage()) {
                                    stack.shrink(1);
                                    ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
                                    if (!player.addItem(emptyBottle)) {
                                        player.drop(emptyBottle, false);
                                    }
                                } else {
                                    stack.setDamageValue(newDamage);
                                }
                            } else {
                                stack.shrink(1);
                            }
                        }
                    } else if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }

                    level.setBlock(pos, state.setValue(CONTENT, fromItem), 3);
                }

                if (fromItem.isBeer()) {
                    spawnFoam(level, pos);
                    level.playSound(player, pos, ModSounds.BEER_OPEN.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
                } else {
                    level.playSound(player, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.7f, 1.1f);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }

            // Beer Bucket
            if (com.createbrewery.ModFluids.BEER.getBucket().map(stack::is).orElse(false)) {
                if (!level.isClientSide) {
                    if (!player.getAbilities().instabuild) {
                        player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                    }
                    level.setBlock(pos, state.setValue(CONTENT, DrinkContent.BEER), 3);
                }
                spawnFoam(level, pos);
                level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }

            // Transfer from held filled glass
            if (stack.getItem() instanceof DrinkGlassItem) {
                DrinkContent heldContent = DrinkGlassItem.getContent(stack);
                if (heldContent != DrinkContent.EMPTY) {
                    if (!level.isClientSide) {
                        level.setBlock(pos, state.setValue(CONTENT, heldContent), 3);
                        DrinkGlassItem.setContent(stack, DrinkContent.EMPTY);
                    }
                    level.playSound(player, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.7f, 1.0f);
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        DrinkContent content = state.getValue(CONTENT);

        // Sneak: pick up glass into inventory
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack drop = new ItemStack(this);
                if (content != DrinkContent.EMPTY) {
                    DrinkGlassItem.setContent(drop, content);
                }
                if (!player.addItem(drop)) {
                    player.drop(drop, false);
                }
                level.removeBlock(pos, false);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Normal right-click on filled glass: DRINK IT OFF THE COUNTER!
        if (content != DrinkContent.EMPTY) {
            if (player.getCooldowns().isOnCooldown(this.asItem())) {
                return InteractionResult.PASS;
            }

            if (!level.isClientSide) {
                float dose = content.getPerMille() * glassType.getAlcoholMultiplier();
                DrunkServer.drink(player, dose);
                player.getCooldowns().addCooldown(this.asItem(), Intoxication.DRINK_COOLDOWN);

                if (player.hasEffect(ModEffects.PAINKILLER)) {
                    DrunkServer.irritateStomach(player);
                }

                if (content.isBeer()) {
                    spawnFoam(level, pos);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.9f, 1.0f);
                }

                // Cheers mechanic: if another player is within 4 blocks, clink glasses and grant Cheers buff!
                AABB area = new AABB(pos).inflate(4.0);
                List<Player> nearby = level.getEntitiesOfClass(Player.class, area, p -> p != player && p.isAlive());
                if (!nearby.isEmpty()) {
                    level.playSound(null, pos, ModSounds.GLASS_CLINK.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
                    player.addEffect(new MobEffectInstance(ModEffects.CHEERS, 600, 0));
                    for (Player buddy : nearby) {
                        buddy.addEffect(new MobEffectInstance(ModEffects.CHEERS, 600, 0));
                    }
                    player.displayClientMessage(Component.translatable("createbrewery.drink.cheers_toast"), true);
                }

                // Empty the glass
                level.setBlock(pos, state.setValue(CONTENT, DrinkContent.EMPTY), 3);
            }

            level.playSound(player, pos, SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 1.0f);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    private void spawnFoam(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            double px = pos.getX() + 0.5;
            double py = pos.getY() + 0.5;
            double pz = pos.getZ() + 0.5;
            serverLevel.sendParticles(ModParticles.BEER_FOAM.get(), px, py, pz, 6, 0.08, 0.08, 0.08, 0.01);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            ItemStack drop = new ItemStack(this);
            DrinkContent content = state.getValue(CONTENT);
            if (content != DrinkContent.EMPTY) {
                DrinkGlassItem.setContent(drop, content);
            }
            popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
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
