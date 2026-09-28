package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A microphone linked to a booth: see {@link MicrophoneBlock}. It works out on the server thread
 * who stands at it and where their voice goes; the voice chat thread only reads that. No voice
 * chat classes here, so it loads without the mod.
 */
public class MicrophoneBlockEntity extends SpeakerBlockEntity {
    /** A PA carries a voice only so far: past this many speakers the rest stay quiet. */
    public static final int MAX_SPEAKERS = 16;

    /** Where a player's voice goes out, until {@code until} (ms): renewed while they stay at the mic. */
    public record Route(ServerLevel level, List<Vec3> speakers, long until) {}

    /** Per player at a switched-on mic. Read by the voice chat thread. */
    public static final Map<UUID, Route> ROUTES = new ConcurrentHashMap<>();

    public MicrophoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 5 != 0) return;
        if (!getBlockState().getValue(MicrophoneBlock.ON) || getBooth() == null) return;
        Vec3 head = Vec3.atCenterOf(worldPosition).add(0, 0.4, 0);
        List<ServerPlayer> talking = server.getEntitiesOfClass(ServerPlayer.class, new AABB(head, head).inflate(MicrophoneBlock.REACH),
            p -> p.getEyePosition().distanceToSqr(head) <= MicrophoneBlock.REACH * MicrophoneBlock.REACH);
        if (talking.isEmpty()) return;
        List<Vec3> speakers = speakers();
        long until = System.currentTimeMillis() + 600;
        for (ServerPlayer p : talking) ROUTES.put(p.getUUID(), new Route(server, speakers, until));
    }

    /** Where the mic's voice comes out: its booth's speakers, the nearest first. */
    List<Vec3> speakers() {
        List<Vec3> out = new ArrayList<>();
        for (SpeakerBlockEntity s : linked(level, getBooth())) if (s.isSpeaker()) out.add(s.mouth());
        out.sort(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(Vec3.atCenterOf(worldPosition))));
        return List.copyOf(out.size() > MAX_SPEAKERS ? out.subList(0, MAX_SPEAKERS) : out);
    }
}
