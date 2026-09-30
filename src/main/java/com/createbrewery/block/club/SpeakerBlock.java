package com.createbrewery.block.club;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
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

import java.util.List;
import java.util.Optional;

/**
 * A club speaker. Right-click a DJ booth with speakers in hand to link them to it, then place them
 * around the club; right-click a placed speaker with linked ones to relink it.
 */
public class SpeakerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** How far a speaker may stand from its booth - a long cable run through a club. */
    public static final int MAX_LINK = 64;
    private static final String LINK = "LinkedBooth";

    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 16, 15);

    public SpeakerBlock(Properties properties) {
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

    // ---------------------------------------------------------------- linking

    /** Marks a speaker item (the whole stack) as belonging to the booth at {@code booth}. */
    public static void link(ItemStack stack, BlockPos booth) {
        link(stack, booth, LINK, "createbrewery.speaker.lore");
    }

    /** Marks an item (the whole stack) as belonging to the block at {@code target}, under {@code key}, with a tooltip line. */
    static void link(ItemStack stack, BlockPos target, String key, String lore) {
        CompoundTag tag = new CompoundTag();
        tag.put(key, NbtUtils.writeBlockPos(target));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.translatable(lore, target.getX(), target.getY(), target.getZ()).withStyle(ChatFormatting.GRAY))));
    }

    /** The booth a speaker item was linked to, if any. */
    public static Optional<BlockPos> linkOf(ItemStack stack) {
        return linkOf(stack, LINK);
    }

    static Optional<BlockPos> linkOf(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? Optional.empty() : NbtUtils.readBlockPos(data.copyTag(), key);
    }

    /** Server: links the placed speaker at {@code pos} to the booth {@code stack} names, if that booth is there and close enough. */
    public static boolean applyLink(Level level, BlockPos pos, ItemStack stack) {
        Optional<BlockPos> booth = linkOf(stack);
        if (booth.isEmpty() || !(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) return false;
        if (!booth.get().closerThan(pos, MAX_LINK) || !level.isLoaded(booth.get()) || !(level.getBlockEntity(booth.get()) instanceof DjBoothBlockEntity)) return false;
        speaker.setBooth(booth.get());
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || linkOf(stack).isEmpty()) return;
        if (!applyLink(level, pos, stack) && placer instanceof Player player) {
            player.displayClientMessage(Component.translatable("createbrewery.speaker.too_far", MAX_LINK), true);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(asItem()) || linkOf(stack).isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            boolean linked = applyLink(level, pos, stack);
            player.displayClientMessage(Component.translatable(linked ? "createbrewery.speaker.relinked" : "createbrewery.speaker.too_far", MAX_LINK), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            BlockPos booth = speaker.getBooth();
            player.displayClientMessage(booth == null
                ? Component.translatable("createbrewery.speaker.unlinked")
                : Component.translatable("createbrewery.speaker.status", booth.getX(), booth.getY(), booth.getZ()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Right-clicking a DJ booth with speakers in hand: they now belong to it. Server only. */
    static void linkAtBooth(ItemStack stack, Level level, BlockPos booth, Player player) {
        link(stack, booth);
        level.playSound(null, booth, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
        player.displayClientMessage(Component.translatable("createbrewery.speaker.linked"), true);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(com.createbrewery.ModBlockEntities.SPEAKER.get(), pos, state);
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
