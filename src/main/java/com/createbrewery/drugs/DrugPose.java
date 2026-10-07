package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a player's body is doing on drugs, so everyone around sees it.
 * <ul>
 *   <li>Lasting poses - dancing, nodding off, slumped in a K-hole, doubled over laughing, shivering
 *       in withdrawal - and the
 *       look of the eyes: each client works out its own and sends it; the server passes it on to
 *       everyone who can see that player.</li>
 *   <li>One-off actions - throwing up, a seizure, a line, a drag (smoke or a balloon), a shot, collapsing: the server
 *       sends them ({@link #act}) to the player and everyone who can see them.</li>
 * </ul>
 *
 * @param entity the player's entity id (ignored on the way to the server)
 * @param kind   a pose ({@link #DANCE}..{@link #SHIVER}, or {@link #NONE}) or an action ({@link #VOMIT}..{@link #INHALE})
 * @param amount a pose: how strongly, 0..255; an action: how long, in ticks / 4
 * @param eyes   a pose: {@link #EYES_RED}, {@link #EYES_LIDS}, {@link #EYES_GLASSY} or 0
 */
public record DrugPose(int entity, byte kind, byte amount, byte eyes) implements CustomPacketPayload {
    public static final int NONE = 0, DANCE = 1, NOD = 2, SLUMP = 3, LAUGH = 4, SHIVER = 5;
    public static final int VOMIT = 6, SEIZURE = 7, SNIFF = 8, SMOKE = 9, INJECT = 10, COLLAPSE = 11, INHALE = 12;
    public static final int EYES_RED = 1, EYES_LIDS = 2, EYES_GLASSY = 3;
    /** Full realistic sniff routine: phone out, baggie pour, card chop to line, straw sniff along line, tuck away, dose kicks in. */
    public static final int SNIFF_TICKS = 210;
    public static final float SNIFF_POUR_START = 0.16f;
    public static final float SNIFF_POUR_END = 0.30f;
    public static final float SNIFF_CARD_START = 0.35f;
    public static final float SNIFF_CARD_END = 0.55f;
    public static final float SNIFF_STRAW_START = 0.58f;
    public static final float SNIFF_LINE_START = 0.63f;
    public static final float SNIFF_LINE_END = 0.81f;
    public static final float SNIFF_TUCK = 0.86f;
    public static final float DOSE_AT = 0.95f;
    public static final float SNIFF_AT = SNIFF_LINE_START;
    public static final Type<DrugPose> TYPE = new Type<>(CreateBrewery.ID("pose"));
    public static final StreamCodec<ByteBuf, DrugPose> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, DrugPose::entity, ByteBufCodecs.BYTE, DrugPose::kind, ByteBufCodecs.BYTE, DrugPose::amount,
        ByteBufCodecs.BYTE, DrugPose::eyes, DrugPose::new);
    /** Client side: the last pose heard for each player, by entity id. */
    public static final Map<Integer, DrugPose> SEEN = new ConcurrentHashMap<>();
    /** Client side: the running action of each player, by entity id. */
    public static final Map<Integer, Action> ACTING = new ConcurrentHashMap<>();
    /** Server: the tick each player's pose was last passed on. Weak, so a player who left is forgotten. */
    private static final Map<ServerPlayer, Integer> RELAYED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** An action as the client plays it: which, since when and until when (System.currentTimeMillis). */
    public record Action(int kind, long start, long end) {
        public float progress(long now) {
            return Math.min(1f, (now - start) / (float) Math.max(1L, end - start));
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").optional().playBidirectional(TYPE, CODEC, DrugPose::handle);
    }

    /** Server side: {@code player} does {@code kind} for {@code ticks}; they and everyone near see it. */
    public static void act(Player player, int kind, int ticks) {
        if (!(player instanceof ServerPlayer sp)) return;
        DrugPose pose = new DrugPose(sp.getId(), (byte) kind, (byte) Math.min(255, (ticks + 3) / 4), (byte) 0);
        for (ServerPlayer other : sp.serverLevel().players()) {
            if ((other == sp || other.distanceToSqr(sp) < 96 * 96) && other.connection.hasChannel(TYPE)) {
                PacketDistributor.sendToPlayer(other, pose);
            }
        }
    }

    private static void handle(DrugPose pose, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            // Clients only report their lasting pose; actions come from the server.
            // Untrusted input: a known pose, eyes in range, and no faster than the client's own four a second.
            if (context.player() instanceof ServerPlayer player && pose.kind >= NONE && pose.kind < VOMIT
                && pose.eyes >= 0 && pose.eyes <= EYES_GLASSY && player.tickCount - RELAYED.getOrDefault(player, -10) >= 4) {
                RELAYED.put(player, player.tickCount);
                DrugPose relay = new DrugPose(player.getId(), pose.kind, pose.amount, pose.eyes);
                for (ServerPlayer other : player.serverLevel().players()) {
                    if (other != player && other.distanceToSqr(player) < 96 * 96 && other.connection.hasChannel(TYPE)) {
                        PacketDistributor.sendToPlayer(other, relay);
                    }
                }
            }
        } else if (pose.kind >= VOMIT) {
            long now = System.currentTimeMillis();
            ACTING.put(pose.entity, new Action(pose.kind, now, now + (pose.amount & 0xFF) * 200L));
            if (pose.kind == SMOKE) com.createbrewery.drunk.DrunkClient.exhale(pose.entity);
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
