package com.createbrewery.block.club;

import com.createbrewery.compat.EtchedCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The booth's record library, read off the containers around it each time: every container next to
 * the booth is a playlist, every shulker box inside one of them a playlist of its own. Only records
 * count. Nothing is stored here; the records stay where they are until one is loaded onto a deck.
 */
public final class DjLibrary {
    private DjLibrary() {}

    /** Not up: that is where the DJ stands. */
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};

    /** A container next to the booth. */
    public record Source(Level level, BlockPos pos, IItemHandler items) {}

    /**
     * A playlist: container {@code source} (index into {@link #sources}), or the shulker box at slot
     * {@code shulker} inside it (-1: the container itself).
     */
    public record Playlist(Component name, int source, int shulker) {
        static final StreamCodec<RegistryFriendlyByteBuf, Playlist> CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, Playlist::name,
            ByteBufCodecs.VAR_INT, Playlist::source,
            ByteBufCodecs.VAR_INT, Playlist::shulker,
            Playlist::new);

        public boolean nested() {
            return shulker >= 0;
        }
    }

    /** A record at {@code slot} of playlist {@code playlist} (index into the snapshot's playlists); bpm and length 0 when unknown. */
    public record Entry(int playlist, int slot, Component title, String artist, float bpm, int lengthTicks) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Entry::playlist,
            ByteBufCodecs.VAR_INT, Entry::slot,
            ComponentSerialization.STREAM_CODEC, Entry::title,
            ByteBufCodecs.STRING_UTF8, Entry::artist,
            ByteBufCodecs.FLOAT, Entry::bpm,
            ByteBufCodecs.VAR_INT, Entry::lengthTicks,
            Entry::new);
    }

    public record Snapshot(List<Playlist> playlists, List<Entry> entries) {}

    /** The containers next to the booth at {@code booth}, in {@link #SIDES} order; another booth is not a container. */
    public static List<Source> sources(Level level, BlockPos booth) {
        List<Source> out = new ArrayList<>();
        for (Direction side : SIDES) {
            BlockPos at = booth.relative(side);
            if (level.getBlockEntity(at) instanceof DjBoothBlockEntity) continue;
            IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, at, side.getOpposite());
            if (items != null) out.add(new Source(level, at, items));
        }
        return out;
    }

    public static Snapshot snapshot(Level level, BlockPos booth) {
        List<Playlist> playlists = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        List<Source> sources = sources(level, booth);
        for (int s = 0; s < sources.size(); s++) {
            Source source = sources.get(s);
            int own = playlists.size();
            playlists.add(new Playlist(name(level, source.pos()), s, -1));
            IItemHandler items = source.items();
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (DjBoothBlock.isMusicDisc(stack)) {
                    entries.add(entry(level, own, slot, stack));
                } else if (isShulker(stack)) {
                    int inner = playlists.size();
                    playlists.add(new Playlist(stack.getHoverName(), s, slot));
                    NonNullList<ItemStack> contents = contents(stack);
                    for (int i = 0; i < contents.size(); i++) {
                        if (DjBoothBlock.isMusicDisc(contents.get(i))) entries.add(entry(level, inner, i, contents.get(i)));
                    }
                }
            }
        }
        return new Snapshot(playlists, entries);
    }

    /** Its anvil name if it has one, else what it is ("Chest", "Barrel"). */
    private static Component name(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Nameable named ? named.getDisplayName() : level.getBlockState(pos).getBlock().getName();
    }

    private static Entry entry(Level level, int playlist, int slot, ItemStack disc) {
        TrackInfo info = disc.get(TrackInfo.TRACK_INFO.get());
        float bpm = info != null ? info.bpm() : 0f;
        EtchedCompat.Meta meta = EtchedCompat.meta(disc, level.registryAccess());
        if (meta != null) {
            Component title = meta.tracks() > 1 ? meta.title().copy().append(" [" + meta.tracks() + "]") : meta.title();
            // ponytail: Etched lengths are unknown until streamed; add a measured length to TrackInfo if the column matters
            return new Entry(playlist, slot, title, meta.artist(), bpm, 0);
        }
        var song = JukeboxSong.fromStack(level.registryAccess(), disc);
        Component title = song.map(h -> h.value().description()).orElse(disc.getHoverName());
        int length = song.map(h -> h.value().lengthInTicks()).orElse(0);
        return new Entry(playlist, slot, title, "", bpm, length);
    }

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    /** A shulker box item's 27 slots, as a list that may be changed and written back. */
    public static NonNullList<ItemStack> contents(ItemStack shulker) {
        NonNullList<ItemStack> list = NonNullList.withSize(ShulkerBoxBlockEntity.CONTAINER_SIZE, ItemStack.EMPTY);
        shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(list);
        return list;
    }
}
