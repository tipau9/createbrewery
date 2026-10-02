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
 * @param action one of the AmpSettings action bytes
 * @param zone   the zone it is for (0 for the rack-wide ones)
 * @param value  the new value; for RESET the action to reset
 * @param target the speaker, for ASSIGN; the rack itself otherwise
 */
public record AmpControl(BlockPos pos, byte action, byte zone, float value, BlockPos target) implements CustomPacketPayload {
    public static final Type<AmpControl> TYPE = new Type<>(CreateBrewery.ID("amp_control"));
    public static final StreamCodec<ByteBuf, AmpControl> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, AmpControl::pos,
        ByteBufCodecs.BYTE, AmpControl::action,
        ByteBufCodecs.BYTE, AmpControl::zone,
        ByteBufCodecs.FLOAT, AmpControl::value,
        BlockPos.STREAM_CODEC, AmpControl::target,
        AmpControl::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").optional().playToServer(TYPE, CODEC, AmpControl::handle);
    }

    /** Client side. */
    static void send(BlockPos pos, byte action, int zone, float value, BlockPos target) {
        PacketDistributor.sendToServer(new AmpControl(pos, action, (byte) zone, value, target));
    }

    private static void handle(AmpControl c, IPayloadContext context) {
        // Only a player at the rack works it; a NaN would silence the whole system for everyone.
        if (!(context.player() instanceof ServerPlayer player) || !Float.isFinite(c.value)) return;
        if (!player.level().isLoaded(c.pos) || !player.canInteractWithBlock(c.pos, 1.0)) return;
        if (!(player.level().getBlockEntity(c.pos) instanceof AmpRackBlockEntity rack)) return;
        // A second rack on a booth only looks; the first one drives.
        if (rack.getBooth() != null && !rack.drives()) return;
        rack.apply(c.action, c.zone, c.value, c.target, player);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
