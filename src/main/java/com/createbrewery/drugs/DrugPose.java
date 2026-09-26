package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a player's body is doing on drugs - dancing, nodding off, slumped in a K-hole, doubled
 * over laughing - so everyone around sees it. Each client works out its own pose and sends it;
 * the server passes it on to everyone who can see that player.
 *
 * @param entity the player's entity id (ignored on the way to the server)
 * @param kind   one of {@link #DANCE}, {@link #NOD}, {@link #SLUMP}, {@link #LAUGH}, or {@link #NONE}
 * @param amount how strongly, 0..255
 */
public record DrugPose(int entity, byte kind, byte amount) implements CustomPacketPayload {
    public static final int NONE = 0, DANCE = 1, NOD = 2, SLUMP = 3, LAUGH = 4;
    public static final Type<DrugPose> TYPE = new Type<>(CreateBrewery.ID("pose"));
    public static final StreamCodec<ByteBuf, DrugPose> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, DrugPose::entity, ByteBufCodecs.BYTE, DrugPose::kind, ByteBufCodecs.BYTE, DrugPose::amount, DrugPose::new);
    /** Client side: the last pose heard for each player, by entity id. */
    public static final Map<Integer, DrugPose> SEEN = new ConcurrentHashMap<>();

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playBidirectional(TYPE, CODEC, DrugPose::handle);
    }

    private static void handle(DrugPose pose, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            if (context.player() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayersTrackingEntity(player, new DrugPose(player.getId(), pose.kind, pose.amount));
            }
        } else {
            SEEN.put(pose.entity, pose);
        }
    }

    public float strength() {
        return (amount & 0xFF) / 255f;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
