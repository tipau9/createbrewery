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
 * A move on the DJ mixer screen, sent to the server, which owns the decks.
 *
 * @param value the fader position for CROSSFADER / PITCH_*, 0 or 1 for AUTOMIX, unused otherwise
 */
public record DjControl(BlockPos pos, byte action, byte deck, float value) implements CustomPacketPayload {
    public static final byte TOGGLE_A = 0, TOGGLE_B = 1, CROSSFADER = 2, PITCH_A = 3, PITCH_B = 4, DROP = 5, EJECT = 6, AUTOMIX = 7;
    /** Per deck (see {@code deck}): EQ bands, filter, effect, effect amount, loop length in beats. */
    public static final byte EQ_HIGH = 8, EQ_MID = 9, EQ_LOW = 10, FILTER = 11, FX = 12, FX_AMOUNT = 13, LOOP = 14;

    public static final Type<DjControl> TYPE = new Type<>(CreateBrewery.ID("dj_control"));
    public static final StreamCodec<ByteBuf, DjControl> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DjControl::pos,
        ByteBufCodecs.BYTE, DjControl::action,
        ByteBufCodecs.BYTE, DjControl::deck,
        ByteBufCodecs.FLOAT, DjControl::value,
        DjControl::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        // "2": the deck byte was added.
        event.registrar("2").optional().playToServer(TYPE, CODEC, DjControl::handle);
    }

    /** Client side. */
    public static void send(BlockPos pos, byte action, float value) {
        send(pos, action, 0, value);
    }

    public static void send(BlockPos pos, byte action, int deck, float value) {
        PacketDistributor.sendToServer(new DjControl(pos, action, (byte) deck, value));
    }

    private static void handle(DjControl control, IPayloadContext context) {
        // Only a player standing at the booth mixes on it; the values are clamped by the booth itself.
        if (!(context.player() instanceof ServerPlayer player) || !Float.isFinite(control.value)) return;
        if (!player.level().isLoaded(control.pos) || !player.canInteractWithBlock(control.pos, 1.0)) return;
        if (!(player.level().getBlockEntity(control.pos) instanceof DjBoothBlockEntity dj)) return;
        int deck = control.deck;
        if (control.action >= EQ_HIGH && deck != DjBoothBlockEntity.A && deck != DjBoothBlockEntity.B) return;

        switch (control.action) {
            case TOGGLE_A -> dj.toggleDeck(DjBoothBlockEntity.A, player);
            case TOGGLE_B -> dj.toggleDeck(DjBoothBlockEntity.B, player);
            case CROSSFADER -> dj.setCrossfader(control.value);
            case PITCH_A -> dj.setPitch(DjBoothBlockEntity.A, control.value);
            case PITCH_B -> dj.setPitch(DjBoothBlockEntity.B, control.value);
            case DROP -> dj.triggerDrop(player);
            case EJECT -> dj.ejectDiscs(player);
            case AUTOMIX -> dj.setAutomix(control.value > 0.5f);
            case EQ_HIGH -> dj.setEq(deck, DjBoothBlockEntity.HIGH, control.value);
            case EQ_MID -> dj.setEq(deck, DjBoothBlockEntity.MID, control.value);
            case EQ_LOW -> dj.setEq(deck, DjBoothBlockEntity.LOW, control.value);
            case FILTER -> dj.setFilter(deck, control.value);
            case FX -> dj.setFx(deck, (int) control.value);
            case FX_AMOUNT -> dj.setFxAmount(deck, control.value);
            case LOOP -> dj.setLoop(deck, (int) control.value);
            default -> {}
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
