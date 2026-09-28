package com.createbrewery.block.club;

import com.createbrewery.CreateBrewery;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A move on the DMX console screen, sent to the server, which owns the console's settings.
 *
 * @param index the group (0..7) or scene (0..3) it is for, where it is for one
 * @param value a fader position, a colour / program / pattern / rate number, or 0/1
 */
public record DmxControl(BlockPos pos, byte action, byte index, float value) implements CustomPacketPayload {
    public static final byte FADER = 0, MASTER = 1, COLOR = 2, FLASH = 3, RELEASE = 4, PROGRAM = 5, MOVE = 6, RATE = 7,
        BLACKOUT = 8, STORE = 9, RECALL = 10;

    public static final Type<DmxControl> TYPE = new Type<>(CreateBrewery.ID("dmx_control"));
    public static final StreamCodec<ByteBuf, DmxControl> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DmxControl::pos,
        ByteBufCodecs.BYTE, DmxControl::action,
        ByteBufCodecs.BYTE, DmxControl::index,
        ByteBufCodecs.FLOAT, DmxControl::value,
        DmxControl::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(TYPE, CODEC, DmxControl::handle);
    }

    /** Client side. */
    static void send(BlockPos pos, byte action, int index, float value) {
        PacketDistributor.sendToServer(new DmxControl(pos, action, (byte) index, value));
    }

    private static void handle(DmxControl c, IPayloadContext context) {
        // Only a player at the console works it; groups and scenes out of range are ignored.
        if (!(context.player() instanceof ServerPlayer player) || !Float.isFinite(c.value)) return;
        if (!player.level().isLoaded(c.pos) || !player.canInteractWithBlock(c.pos, 1.0)) return;
        if (!(player.level().getBlockEntity(c.pos) instanceof DmxConsoleBlockEntity dmx)) return;
        int i = c.index;
        boolean group = i >= 0 && i < DmxProgram.GROUPS, scene = i >= 0 && i < DmxProgram.SCENES;

        switch (c.action) {
            case FADER -> { if (group) dmx.setFader(i, c.value); }
            case MASTER -> dmx.setMaster(c.value);
            case COLOR -> { if (group) dmx.setColor(i, (int) c.value); }
            case FLASH -> { if (group) dmx.flash(i); }
            case RELEASE -> { if (group) dmx.release(i); }
            case PROGRAM -> dmx.setProgram((int) c.value);
            case MOVE -> dmx.setMove((int) c.value);
            case RATE -> dmx.setRate((int) c.value);
            case BLACKOUT -> dmx.setBlackout(c.value > 0.5f);
            case STORE -> { if (scene) dmx.storeScene(i); }
            case RECALL -> { if (scene) dmx.recallScene(i); }
            default -> {}
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
