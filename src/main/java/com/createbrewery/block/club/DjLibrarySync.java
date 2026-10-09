package com.createbrewery.block.club;

import com.createbrewery.CreateBrewery;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The booth's library between server and browser: the browser asks for it while open (about once a
 * second) and asks for records to be loaded; the server, which can see inside the containers, answers.
 */
public final class DjLibrarySync {
    private DjLibrarySync() {}

    /** Client → server: send me this booth's library. */
    public record Request(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CreateBrewery.ID("dj_library_request"));
        static final StreamCodec<ByteBuf, Request> CODEC = BlockPos.STREAM_CODEC.map(Request::new, Request::pos);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** Client → server: load the record at this library address onto {@code deck}, if it is still {@code disc}. */
    public record Load(BlockPos pos, int deck, int source, int shulker, int slot, ItemStack disc) implements CustomPacketPayload {
        public static final Type<Load> TYPE = new Type<>(CreateBrewery.ID("dj_library_load"));
        static final StreamCodec<RegistryFriendlyByteBuf, Load> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Load::pos,
            ByteBufCodecs.VAR_INT, Load::deck,
            ByteBufCodecs.VAR_INT, Load::source,
            ByteBufCodecs.VAR_INT, Load::shulker,
            ByteBufCodecs.VAR_INT, Load::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, Load::disc,
            Load::new);

        @Override
        public Type<Load> type() {
            return TYPE;
        }
    }

    /** Server → client: the library of the booth at {@code pos}. */
    public record Library(BlockPos pos, List<DjLibrary.Playlist> playlists, List<DjLibrary.Entry> entries) implements CustomPacketPayload {
        public static final Type<Library> TYPE = new Type<>(CreateBrewery.ID("dj_library"));
        static final StreamCodec<RegistryFriendlyByteBuf, Library> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Library::pos,
            DjLibrary.Playlist.CODEC.apply(ByteBufCodecs.list()), Library::playlists,
            DjLibrary.Entry.CODEC.apply(ByteBufCodecs.list()), Library::entries,
            Library::new);

        @Override
        public Type<Library> type() {
            return TYPE;
        }
    }

    /** Client: the last library received per booth. */
    private static final Map<BlockPos, DjLibrary.Snapshot> RECEIVED = new ConcurrentHashMap<>();

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Request.TYPE, Request.CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player && booth(player, request.pos()) != null) send(player, request.pos());
        });
        registrar.playToServer(Load.TYPE, Load.CODEC, (load, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DjBoothBlockEntity dj = booth(player, load.pos());
            if (dj == null) return;
            dj.loadFromLibrary(load.deck(), load.source(), load.shulker(), load.slot(), load.disc(), player);
            send(player, load.pos());
        });
        registrar.playToClient(Library.TYPE, Library.CODEC,
            (library, context) -> RECEIVED.put(library.pos(), new DjLibrary.Snapshot(library.playlists(), library.entries())));
    }

    /** The booth at {@code pos}, if {@code player} stands at it. */
    @Nullable
    private static DjBoothBlockEntity booth(ServerPlayer player, BlockPos pos) {
        if (!player.level().isLoaded(pos) || !player.canInteractWithBlock(pos, 1.0)) return null;
        return player.level().getBlockEntity(pos) instanceof DjBoothBlockEntity dj ? dj : null;
    }

    private static void send(ServerPlayer player, BlockPos pos) {
        DjLibrary.Snapshot snapshot = DjLibrary.snapshot(player.level(), pos);
        PacketDistributor.sendToPlayer(player, new Library(pos, snapshot.playlists(), snapshot.entries()));
    }

    /** Client side. */
    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new Request(pos));
    }

    /** Client side. */
    public static void load(BlockPos pos, int deck, int source, int shulker, int slot, ItemStack disc) {
        PacketDistributor.sendToServer(new Load(pos, deck, source, shulker, slot, disc));
    }

    /** Client side: null until the server has answered once. */
    @Nullable
    public static DjLibrary.Snapshot received(BlockPos pos) {
        return RECEIVED.get(pos);
    }
}
