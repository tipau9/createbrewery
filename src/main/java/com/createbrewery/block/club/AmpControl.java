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
 * A move on the amp rack screen, sent to the server, which owns the rack's settings.
 *
 * @param value the crossover in Hz, or a gain knob 0..1
 */
public record AmpControl(BlockPos pos, byte action, float value) implements CustomPacketPayload {
    public static final byte CROSSOVER = 0, SUB_GAIN = 1, TOP_GAIN = 2, PROPAGATION = 3,
        MUTE_TOPS = 4, MUTE_SUBS = 5, BASS_CONTOUR = 6, SUB_CUT = 7, DELAY_MS = 8;

    public static final Type<AmpControl> TYPE = new Type<>(CreateBrewery.ID("amp_control"));
    public static final StreamCodec<ByteBuf, AmpControl> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, AmpControl::pos,
        ByteBufCodecs.BYTE, AmpControl::action,
        ByteBufCodecs.FLOAT, AmpControl::value,
        AmpControl::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").optional().playToServer(TYPE, CODEC, AmpControl::handle);
    }

    /** Client side. */
    static void send(BlockPos pos, byte action, float value) {
        PacketDistributor.sendToServer(new AmpControl(pos, action, value));
    }

    /** The rack clamps what it is given. */
    static void apply(AmpRackBlockEntity rack, byte action, float value) {
        switch (action) {
            case CROSSOVER -> rack.setCrossover(value);
            case SUB_GAIN -> rack.setSubGain(value);
            case TOP_GAIN -> rack.setTopGain(value);
            case PROPAGATION -> rack.setPropagation(value > 0.5f);
            case MUTE_TOPS -> rack.setMuteTops(value > 0.5f);
            case MUTE_SUBS -> rack.setMuteSubs(value > 0.5f);
            case BASS_CONTOUR -> rack.setBassContour((int) value);
            case SUB_CUT -> rack.setSubCut((int) value);
            case DELAY_MS -> rack.setDelayMs(value);
            default -> {}
        }
    }

    private static void handle(AmpControl c, IPayloadContext context) {
        // Only a player at the rack works it; a NaN would silence the whole system for everyone.
        if (!(context.player() instanceof ServerPlayer player) || !Float.isFinite(c.value)) return;
        if (!player.level().isLoaded(c.pos) || !player.canInteractWithBlock(c.pos, 1.0)) return;
        if (player.level().getBlockEntity(c.pos) instanceof AmpRackBlockEntity rack) apply(rack, c.action, c.value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
