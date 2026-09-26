package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Tells a client to write what the drug vision is doing into its log for a while (see
 * DrunkClient.debugLog), so a test run can be checked from the log alone. Sent by /brewery test.
 *
 * @param seconds how long to log
 */
public record DrugDebug(int seconds) implements CustomPacketPayload {
    public static final Type<DrugDebug> TYPE = new Type<>(CreateBrewery.ID("debug"));
    public static final StreamCodec<ByteBuf, DrugDebug> CODEC = ByteBufCodecs.VAR_INT.map(DrugDebug::new, DrugDebug::seconds);
    /** Client side: until when (System.currentTimeMillis) to log. */
    public static volatile long until;

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, CODEC, DrugDebug::handle);
    }

    private static void handle(DrugDebug debug, IPayloadContext context) {
        until = System.currentTimeMillis() + debug.seconds * 1000L;
    }

    public static boolean on() {
        return System.currentTimeMillis() < until;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
