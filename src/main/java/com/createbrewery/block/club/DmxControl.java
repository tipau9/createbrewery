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
        BLACKOUT = 8, STORE = 9, RECALL = 10, RECORD = 11, CLEAR_SHOW = 12, COLORFX = 13, GOBO = 14, PRISM = 15, ZOOM = 16,
        BLIND_ALL = 17, STROBE_ALL = 18, HAZER = 19, COLOR_RGB = 20, FADE_TIME = 21, TAP_TEMPO = 22, DJ_SYNC = 23, SCENE_PAGE = 24;

    public static final Type<DmxControl> TYPE = new Type<>(CreateBrewery.ID("dmx_control"));
    public static final StreamCodec<ByteBuf, DmxControl> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DmxControl::pos,
        ByteBufCodecs.BYTE, DmxControl::action,
        ByteBufCodecs.BYTE, DmxControl::index,
        ByteBufCodecs.FLOAT, DmxControl::value,
        DmxControl::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        // "4": grandMA3 bump buttons, hazer, 16 scenes, gel color picker, tap tempo and DJ sync.
        event.registrar("4").optional().playToServer(TYPE, CODEC, DmxControl::handle);
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
            case COLOR -> { if (group) dmx.cycleColor(i); }
            case FLASH -> { if (group) dmx.flash(i); }
            case RELEASE -> { if (group) dmx.release(i); }
            case PROGRAM -> dmx.setProgram((int) c.value);
            case MOVE -> dmx.setMove((int) c.value);
            case RATE -> dmx.setRate((int) c.value);
            case BLACKOUT -> dmx.setBlackout(c.value > 0.5f);
            case STORE -> { if (scene) dmx.storeScene(i); }
            case RECALL -> { if (scene) dmx.recallScene(i); }
            case RECORD -> {
                if (!dmx.setRecording(c.value > 0.5f)) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("createbrewery.dmx.no_booth"), true);
            }
            case CLEAR_SHOW -> dmx.clearShow();
            case COLORFX -> dmx.setColorFx((int) c.value);
            case GOBO -> dmx.setGobo((int) c.value);
            case PRISM -> dmx.setPrism(c.value > 0.5f);
            case ZOOM -> dmx.setZoom((int) c.value);
            case BLIND_ALL -> dmx.setBlindAll(c.value > 0.5f);
            case STROBE_ALL -> dmx.setStrobeAll(c.value > 0.5f);
            case HAZER -> dmx.triggerHazer(player);
            case COLOR_RGB -> { if (group) dmx.setColorRgb(i, (int) c.value); }
            case FADE_TIME -> dmx.setFadeTime(c.value);
            case TAP_TEMPO -> dmx.setManualBpm(c.value);
            case DJ_SYNC -> dmx.setDjSync(c.value > 0.5f);
            case SCENE_PAGE -> dmx.setScenePage((int) c.value);
            default -> {}
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
