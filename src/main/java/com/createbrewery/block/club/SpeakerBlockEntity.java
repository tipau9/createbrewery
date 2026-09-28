package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A club speaker linked to one DJ booth: whatever that booth plays comes out of it (see
 * MusicPulse). Another booth's speakers play another booth's music, so two DJs can share a club.
 * Subwoofers and amp racks are linked the same way.
 */
public class SpeakerBlockEntity extends BlockEntity {

    /** Client: every loaded speaker and the booth it plays. */
    private static final Map<BlockPos, BlockPos> LINKS = new ConcurrentHashMap<>();
    /** Server: the same, per dimension (in singleplayer both sides share these statics, so they are kept apart). */
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Map<BlockPos, BlockPos>> SERVER_LINKS = new ConcurrentHashMap<>();

    private static Map<BlockPos, BlockPos> links(Level level) {
        return level.isClientSide ? LINKS : SERVER_LINKS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
    }

    @Nullable
    private BlockPos booth;

    public SpeakerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public BlockPos getBooth() {
        return booth;
    }

    public void setBooth(@Nullable BlockPos booth) {
        this.booth = booth == null ? null : booth.immutable();
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        register();
    }

    /** Where the sound leaves the speaker: the middle of its front. */
    public Vec3 mouth() {
        Direction facing = getBlockState().getValue(SpeakerBlock.FACING);
        return Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.4));
    }

    /** A plain speaker, not a subwoofer, amp rack or microphone. */
    public boolean isSpeaker() {
        return getClass() == SpeakerBlockEntity.class;
    }

    /** Everything loaded that is linked to the booth at {@code boothPos} - speakers, subwoofers, amp racks, microphones - in position order. */
    public static List<SpeakerBlockEntity> linked(Level level, BlockPos boothPos) {
        List<SpeakerBlockEntity> linked = new ArrayList<>();
        Map<BlockPos, BlockPos> links = links(level);
        for (Map.Entry<BlockPos, BlockPos> link : links.entrySet()) {
            if (!link.getValue().equals(boothPos)) continue;
            // Left behind by another dimension (its chunks are not always unloaded one by one).
            // (Never load a chunk for it: the server asks every few ticks.)
            if (level.isLoaded(link.getKey()) && level.getBlockEntity(link.getKey()) instanceof SpeakerBlockEntity speaker && boothPos.equals(speaker.booth)) {
                linked.add(speaker);
            } else {
                links.remove(link.getKey());
            }
        }
        // The map's order is arbitrary; with two racks on one booth the same one must win every frame.
        linked.sort(java.util.Comparator.comparing(BlockEntity::getBlockPos));
        return linked;
    }

    /** Server: the linked microphones and whatever else of this kind, in {@code level}. */
    public static <T extends SpeakerBlockEntity> List<T> loaded(Level level, Class<T> kind) {
        List<T> found = new ArrayList<>();
        for (BlockPos pos : links(level).keySet()) {
            if (level.isLoaded(pos) && kind.isInstance(level.getBlockEntity(pos))) found.add(kind.cast(level.getBlockEntity(pos)));
        }
        return found;
    }

    private void register() {
        if (level == null) return;
        if (booth == null || isRemoved()) links(level).remove(worldPosition);
        else links(level).put(worldPosition.immutable(), booth);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) links(level).remove(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (booth != null) tag.put("Booth", NbtUtils.writeBlockPos(booth));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        booth = NbtUtils.readBlockPos(tag, "Booth").orElse(null);
        register();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}
