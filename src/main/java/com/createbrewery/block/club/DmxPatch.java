package com.createbrewery.block.club;

import com.createbrewery.CreateBrewery;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The console's patch: the lights linked to it, and moving one of them to another group from the
 * console screen, without walking up to it.
 */
public record DmxPatch(BlockPos console, BlockPos light, byte action, byte value) implements CustomPacketPayload {
    /** What a patch move sets: the group (0..7), the brightness (1..10 tenths) or the reverse bits (0..3, fixtures only). */
    static final byte GROUP = 0, DIM = 1, INVERT = 2;
    public static final Type<DmxPatch> TYPE = new Type<>(CreateBrewery.ID("dmx_patch"));
    public static final StreamCodec<ByteBuf, DmxPatch> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DmxPatch::console,
        BlockPos.STREAM_CODEC, DmxPatch::light,
        ByteBufCodecs.BYTE, DmxPatch::action,
        ByteBufCodecs.BYTE, DmxPatch::value,
        DmxPatch::new);

    /** A light linked to the console: where it is, what it is, its group (0..7), and for fixtures brightness and reverse bits (effects: -1). */
    record Light(BlockPos pos, Block block, int group, int dim, int invert) {
        boolean fixture() {
            return dim > 0;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").optional().playToServer(TYPE, CODEC, DmxPatch::handle);
    }

    /** Client side. */
    static void send(BlockPos console, BlockPos light, byte action, int value) {
        PacketDistributor.sendToServer(new DmxPatch(console, light, action, (byte) value));
    }

    /** The loaded lights linked to {@code console}, nearest first. Either side: every linked block entity syncs its console and group. */
    static List<Light> linkedTo(Level level, BlockPos console) {
        List<Light> out = new ArrayList<>();
        int r = FixtureBlock.MAX_LINK;
        for (BlockEntity be : DmxConsoleBlockEntity.blockEntitiesNear(level, console, r, -r, r)) {
            if (be instanceof FixtureBlockEntity f && console.equals(f.getConsole())) {
                out.add(new Light(be.getBlockPos(), be.getBlockState().getBlock(), f.getGroup(), f.getDim(), f.getInvert()));
            } else if (be instanceof ConsoleLinked l && console.equals(l.consoleLink().console)) {
                out.add(new Light(be.getBlockPos(), be.getBlockState().getBlock(), l.consoleLink().group, -1, -1));
            }
        }
        out.sort(Comparator.comparingDouble(l -> l.pos().distSqr(console)));
        return out;
    }

    /** Server: moves the light at {@code light} to {@code group}, if it is linked to {@code console}. */
    static boolean regroup(Level level, BlockPos console, BlockPos light, int group) {
        if (group < 0 || group >= DmxProgram.GROUPS || !light.closerThan(console, FixtureBlock.MAX_LINK) || !level.isLoaded(light)) return false;
        BlockEntity be = level.getBlockEntity(light);
        if (be instanceof FixtureBlockEntity f && console.equals(f.getConsole())) {
            f.setGroup(group);
            return true;
        }
        if (be instanceof ConsoleLinked l && console.equals(l.consoleLink().console)) {
            ConsoleLink.setGroup(be, l.consoleLink(), group);
            return true;
        }
        return false;
    }

    /** Server: sets a linked fixture's brightness or reverse bits; false if out of range or not a fixture linked here. */
    static boolean adjust(Level level, BlockPos console, BlockPos light, int action, int value) {
        if (!light.closerThan(console, FixtureBlock.MAX_LINK) || !level.isLoaded(light)) return false;
        if (!(level.getBlockEntity(light) instanceof FixtureBlockEntity f) || !console.equals(f.getConsole())) return false;
        if (action == DIM && value >= 1 && value <= 10) {
            f.setDim(value);
            return true;
        }
        if (action == INVERT && value >= 0 && value <= 3) {
            f.setInvert(value);
            return true;
        }
        return false;
    }

    private static void handle(DmxPatch p, IPayloadContext context) {
        // Only a player at the console patches it.
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.level().isLoaded(p.console) || !player.canInteractWithBlock(p.console, 1.0)) return;
        if (!(player.level().getBlockEntity(p.console) instanceof DmxConsoleBlockEntity)) return;
        if (p.action == GROUP) regroup(player.level(), p.console, p.light, p.value);
        else adjust(player.level(), p.console, p.light, p.action, p.value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
