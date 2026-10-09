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
import net.minecraft.world.Containers;
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
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

    /** The browser's sort columns; INDEX is the order the records sit in their containers. */
    public enum Sort { INDEX, TITLE, ARTIST, BPM, LENGTH }

    /** What the browser lists: playlist {@code playlist} (-1: all), matching {@code query} in title or artist, sorted. */
    public static List<Entry> view(Snapshot snapshot, int playlist, String query, Sort sort, boolean descending) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : snapshot.entries()) {
            if (playlist >= 0 && e.playlist() != playlist) continue;
            if (!q.isEmpty() && !lower(e.title().getString()).contains(q) && !lower(e.artist()).contains(q)) continue;
            out.add(e);
        }
        Comparator<Entry> order = switch (sort) {
            case INDEX -> null;
            case TITLE -> Comparator.comparing((Entry e) -> lower(e.title().getString()));
            case ARTIST -> Comparator.comparing((Entry e) -> lower(e.artist()));
            case BPM -> Comparator.comparingDouble(Entry::bpm);
            case LENGTH -> Comparator.comparingInt(Entry::lengthTicks);
        };
        if (order != null) out.sort(order);
        if (descending) Collections.reverse(out);
        return out;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

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

    /**
     * Takes the record at the address: slot {@code slot} of container {@code source}, or of the
     * shulker box at {@code shulker} inside it. Empty, with nothing changed, if no record is there
     * any more (the browser's list can be a second old).
     */
    public static ItemStack take(List<Source> sources, int source, int shulker, int slot) {
        if (source < 0 || source >= sources.size()) return ItemStack.EMPTY;
        Source src = sources.get(source);
        IItemHandler items = src.items();
        if (shulker < 0) {
            if (slot < 0 || slot >= items.getSlots() || !DjBoothBlock.isMusicDisc(items.getStackInSlot(slot))) return ItemStack.EMPTY;
            return items.extractItem(slot, 1, false);
        }
        if (shulker >= items.getSlots()) return ItemStack.EMPTY;
        ItemStack box = items.getStackInSlot(shulker);
        if (!isShulker(box)) return ItemStack.EMPTY;
        NonNullList<ItemStack> contents = contents(box);
        if (slot < 0 || slot >= contents.size() || !DjBoothBlock.isMusicDisc(contents.get(slot))) return ItemStack.EMPTY;
        ItemStack disc = contents.get(slot);
        contents.set(slot, ItemStack.EMPTY);
        return replaceBox(src, shulker, box, contents) ? disc : ItemStack.EMPTY;
    }

    /** Puts {@code disc} at the address if that slot is free, else anywhere in that container, else drops it at the booth. */
    public static void putBack(Level level, BlockPos booth, List<Source> sources, int source, int shulker, int slot, ItemStack disc) {
        if (source >= 0 && source < sources.size()) {
            Source src = sources.get(source);
            IItemHandler items = src.items();
            if (shulker >= 0) {
                if (shulker < items.getSlots() && isShulker(items.getStackInSlot(shulker))) {
                    ItemStack box = items.getStackInSlot(shulker);
                    NonNullList<ItemStack> contents = contents(box);
                    if (slot >= 0 && slot < contents.size() && contents.get(slot).isEmpty()) {
                        contents.set(slot, disc);
                        if (replaceBox(src, shulker, box, contents)) return;
                    }
                }
            } else if (slot >= 0 && slot < items.getSlots()) {
                disc = items.insertItem(slot, disc, false);
                if (disc.isEmpty()) return;
            }
            disc = ItemHandlerHelper.insertItem(items, disc, false);
            if (disc.isEmpty()) return;
        }
        Containers.dropItemStack(level, booth.getX() + 0.5, booth.getY() + 1.0, booth.getZ() + 0.5, disc);
    }

    /** The slots holding records in container {@code source}, or in the shulker box at {@code shulker} inside it. */
    public static List<Integer> discSlots(List<Source> sources, int source, int shulker) {
        List<Integer> out = new ArrayList<>();
        if (source < 0 || source >= sources.size()) return out;
        IItemHandler items = sources.get(source).items();
        if (shulker < 0) {
            for (int i = 0; i < items.getSlots(); i++) if (DjBoothBlock.isMusicDisc(items.getStackInSlot(i))) out.add(i);
        } else if (shulker < items.getSlots() && isShulker(items.getStackInSlot(shulker))) {
            NonNullList<ItemStack> contents = contents(items.getStackInSlot(shulker));
            for (int i = 0; i < contents.size(); i++) if (DjBoothBlock.isMusicDisc(contents.get(i))) out.add(i);
        }
        return out;
    }

    /**
     * Swaps the shulker box at {@code slot} for a copy holding {@code contents}. False, with the
     * original back in place, if the container will not take the copy.
     */
    private static boolean replaceBox(Source src, int slot, ItemStack box, NonNullList<ItemStack> contents) {
        ItemStack updated = box.copyWithCount(1);
        updated.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        ItemStack original = src.items().extractItem(slot, 1, false);
        if (original.isEmpty()) return false;
        if (src.items().insertItem(slot, updated, false).isEmpty()) return true;
        ItemStack left = src.items().insertItem(slot, original, false);
        if (!left.isEmpty()) Containers.dropItemStack(src.level(), src.pos().getX() + 0.5, src.pos().getY() + 1.0, src.pos().getZ() + 0.5, left);
        return false;
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
