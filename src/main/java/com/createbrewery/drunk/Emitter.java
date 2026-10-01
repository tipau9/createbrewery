package com.createbrewery.drunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * One place a song comes out of: a club speaker, or the jukebox or booth itself. It is its own
 * OpenAL source, fed copies of the song's samples in step with the game's own (muted) source, and
 * every piece goes through a {@link WallFilter} set by what stands between it and the listener.
 * Render thread only; see {@link MusicPulse}.
 */
final class Emitter {
    /** ~23 ms: how finely the filter follows you through a door. */
    private static final int PIECE = 1024;
    /** Seconds queued ahead of the game's source: rides out a slow frame. */
    private static final double LEAD = 0.3;
    /** Rays at 20 Hz are plenty for walking about, and cheap with a dozen speakers. */
    private static final double RAY_EVERY = 0.05;
    /** Highs fade over distance in air too, even with nothing in the way. */
    private static final float AIR_ABSORPTION = 1.5f;

    /** Which part of the song it plays: all of it, or one side of the amp rack's crossover. */
    static final int FULL = 0, LOW = 1, HIGH = 2;

    final Vec3 pos;
    float level;
    /** Set each frame: {@link #FULL}, {@link #LOW} (a subwoofer) or {@link #HIGH} (a speaker above the subs), and the crossover in Hz. */
    int band = FULL;
    float crossover = 100f;
    private DeckFx.Crossover split;
    /** The amp rack's gain for this side, put into the samples so the limiter sees it (the game's volume stays on the source). */
    float drive = 1f;
    /** With the rack's speed of sound on: this speaker's alignment delay in seconds (a delay tower's); -1 off. */
    double delay = -1;
    private final DeckFx.Limiter limiter;
    private final BlockPos home;
    /** Which way the sound leaves it. */
    private final Vec3 front;
    private final WallFilter filter;
    /** The deck's EQ, filter and effect, run on this stream; only made for a DJ booth deck. */
    private DeckFx fx;
    /** In the DJ's ears, not in the world: no walls, no distance. */
    private final boolean phones;
    private final double rate;
    private int source = -1;
    /** The song frame fed up to. */
    private long fed;
    /** Queued buffers and their lengths, oldest first. */
    private final ArrayDeque<int[]> queued = new ArrayDeque<>();
    private final float[] in = new float[PIECE], out = new float[PIECE];
    private final ByteBuffer pcm = MemoryUtil.memAlloc(PIECE * 2);
    /** 0 in plain view .. 1 walled off, and how many solid blocks are in the way; both eased. */
    float muffle;
    private float walls, targetMuffle, targetWalls;
    double lastRay = -1;
    private final SyncPolicy sync = new SyncPolicy();
    /** Restarts so far, and how many of them were for each reason (see {@link SyncPolicy}); logged every 20th. */
    private int resyncs, stoppedResyncs, starvedResyncs, driftResyncs;
    /** Processed buffers kept to be filled again: no alGenBuffers / alDeleteBuffers every 23 ms. */
    private final ArrayDeque<Integer> free = new ArrayDeque<>();
    private static final int POOL = 48;
    /** Bass carries further than the highs: a subwoofer is heard from this much further away. */
    static final float SUB_REACH = 1.5f;
    /** Set each frame to the PA's stack gain (see {@link PaLevel}) and eased, so a speaker crossing the radius does not step the level. */
    float stack = 1f;
    /** The radius (blocks) inside which the source is spread over both ears, set each frame from the config; subwoofers are bigger. */
    float spread = 3f;
    private float radius = -1f;
    private float stackNow = 1f;
    private float baseRef;
    private int reachBand = -1;
    /** Its send to the room reverb: the low-pass filter, the gain last set on it, and whether the send is connected. */
    private int sendFilter = -1;
    private float sent = -1f;
    private boolean sendOn;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    Emitter(Vec3 pos, float level, double rate) {
        this(pos, level, rate, false);
    }

    /** The DJ's headphones. */
    static Emitter headphones(double rate) {
        return new Emitter(Vec3.ZERO, 1f, rate, true);
    }

    private Emitter(Vec3 pos, float level, double rate, boolean phones) {
        this.phones = phones;
        this.pos = pos;
        this.level = level;
        this.home = BlockPos.containing(pos);
        // A speaker's mouth sits in its front face; a jukebox or booth plays from its middle, heard from above.
        Vec3 out = pos.subtract(Vec3.atCenterOf(home));
        this.front = out.lengthSqr() > 0.01 ? out.normalize() : new Vec3(0, 1, 0);
        this.rate = rate;
        this.filter = new WallFilter(rate);
        this.limiter = new DeckFx.Limiter(rate);
    }

    /**
     * Once a frame. {@code original} is the game's own source for the song, {@code cursor} the song
     * frame it is playing (-1 if not known yet).
     */
    boolean update(MusicPulse.Track t, int original, long cursor, float gain, float pitch, Level world, Entity listener, double now, float dt, boolean mayRay) {
        if (source <= 0 || !AL10.alIsSource(source)) create(original);
        if (source <= 0) return false;
        reclaim();

        int songState = AL10.alGetSourcei(original, AL10.AL_SOURCE_STATE);
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (songState != AL10.AL_PLAYING || (t.deck ? t.deckFrame < 0 : cursor < 0)) {
            // The game paused (menu) or the song has not started: hold still with it.
            if (state == AL10.AL_PLAYING) AL10.alSourcePause(source);
            return false;
        }

        // With the speed of sound on, a speaker is heard as far behind the song as its sound takes to reach you.
        long target = t.deck ? Math.max(0, t.deckFrame) : cursor;
        if (delay >= 0 && !phones && listener != null) {
            target = target - (long) (DeckFx.propagation(delay, listener.getEyePosition().distanceTo(pos)) * rate * pitch);
        }
        long here = fed - queuedFrames() + AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
        boolean running = state == AL10.AL_PLAYING || state == AL10.AL_PAUSED;
        SyncPolicy.Decision decision = sync.decide(running, queued.size(), here, target, rate, now);
        if (decision.action() == SyncPolicy.Action.RESTART) {
            // Only the first start is expected; more mean lag spikes or drift, worth seeing in the log.
            if (fed >= 0) {
                // A streaming source that runs dry is stopped by OpenAL, so that is what starving looks like.
                String reason = decision.reason().equals("stopped") && state == AL10.AL_STOPPED ? "starved" : decision.reason();
                switch (reason) {
                    case "stopped" -> stoppedResyncs++;
                    case "starved" -> starvedResyncs++;
                    default -> driftResyncs++;
                }
                if (++resyncs % 20 == 1) {
                    LOGGER.info("Speaker at {} resynced ({} times: {} stopped, {} starved, {} drift; now {}, {} ms off)",
                        home, resyncs, stoppedResyncs, starvedResyncs, driftResyncs, reason, Math.round((here - target) * 1000 / rate));
                }
            }
            restart(target);
        } else if (!running) {
            // Silent anyway, so starting over costs no click; feeding on from the old position would replay stale audio.
            restart(target);
        }
        // A small drift is pulled back through the playing speed (within 2 %), not by a restart.
        float servo = decision.action() == SyncPolicy.Action.SERVO ? decision.pitch() : 1f;

        boolean cast = false;
        if (mayRay && !phones && listener != null && now - lastRay >= RAY_EVERY) {
            lastRay = now;
            hear(world, listener);
            cast = true;
        }
        // Eased, so walking through a door opens the sound up over a fifth of a second, not in one click.
        float ease = Math.min(1f, dt * 10f);
        muffle += (targetMuffle - muffle) * ease;
        walls += (targetWalls - walls) * ease;
        filter.set(muffle, Math.max(1f, walls));

        if (t.deck && fx == null) fx = new DeckFx(rate);
        if (fx != null) {
            fx.set(t.eq[2], t.eq[1], t.eq[0], t.filter, t.fx, t.fxAmount, t.song.period() * pitch, t.beatFxBeats);
            fx.setColorFx(t.colorType, t.filter, t.colorParam);
            fx.setMasterTempo(t.masterTempo, pitch);
        }
        // A rack placed or removed, the last sub switched off, the corner moved: taken up at once.
        if (band == FULL) split = null;
        else {
            if (split == null) split = new DeckFx.Crossover(rate);
            split.set(band == LOW, crossover);
        }
        feed(t, target + (long) (LEAD * rate * pitch));
        float wantRadius = phones ? 0f : spread * (band == LOW ? 1.7f : 1f);
        if (radius != wantRadius) {
            radius = wantRadius;
            try {
                AL10.alSourcef(source, org.lwjgl.openal.EXTSourceRadius.AL_SOURCE_RADIUS, wantRadius);
                AL10.alGetError(); // an OpenAL without the extension only flags an error; the game must not log it as its own
            } catch (Throwable ignored) {}
        }
        stackNow += (stack - stackNow) * Math.min(1f, dt * 5f);
        if (!phones && reachBand != band) {
            reachBand = band;
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, baseRef * (band == LOW ? SUB_REACH : 1f));
        }
        AL10.alSourcef(source, AL10.AL_GAIN, gain * level * stackNow);
        AL10.alSourcef(source, AL10.AL_PITCH, pitch * servo);
        if (!phones) wet();
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && !queued.isEmpty()) AL10.alSourcePlay(source);
        return cast;
    }

    /** Connects the source to the room reverb, or lets go of it when there is none; the filter follows how walled off it is. */
    private void wet() {
        int slot = ReverbBus.slot();
        if (slot == 0) {
            if (sendOn) {
                AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
                AL10.alGetError();
                sendOn = false;
            }
            return;
        }
        if (sendFilter < 0) sendFilter = ReverbBus.newFilter();
        if (sendFilter < 0) return;
        float g = ReverbBus.send(band, (float) WallFilter.loss(muffle, walls));
        if (sendOn && Math.abs(g - sent) < 0.02f) return;
        ReverbBus.setFilterGain(sendFilter, g);
        // A changed filter only counts once it is attached again.
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 0, sendFilter);
        AL10.alGetError();
        sent = g;
        sendOn = true;
    }

    /** The OpenAL context this emitter's names belong to: after a sound reload they are all stale. */
    private long context;

    private void create(int original) {
        try {
            context = org.lwjgl.openal.ALC10.alcGetCurrentContext();
            source = AL10.alGenSources();
            sendOn = false;
            sent = -1f;
            radius = -1f;
            AL10.alSource3f(source, AL10.AL_POSITION, (float) pos.x, (float) pos.y, (float) pos.z);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, phones ? AL10.AL_TRUE : AL10.AL_FALSE);
            if (phones) {
                // At the head, the same at any distance.
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
                AL10.alSourcef(source, AL10.AL_MAX_GAIN, 2f);
                fed = -1;
                return;
            }
            // Heard over the same distance as the game would play the record.
            AL10.alSourcei(source, 0xD000, AL10.alGetSourcei(original, 0xD000)); // AL_DISTANCE_MODEL (per source)
            for (int param : new int[] {AL10.AL_REFERENCE_DISTANCE, AL10.AL_MAX_DISTANCE, AL10.AL_ROLLOFF_FACTOR}) {
                AL10.alSourcef(source, param, AL10.alGetSourcef(original, param));
            }
            baseRef = AL10.alGetSourcef(original, AL10.AL_REFERENCE_DISTANCE);
            reachBand = -1;
            AL10.alSourcef(source, AL10.AL_MAX_GAIN, 2f);
            try {
                AL10.alSourcef(source, EXTEfx.AL_AIR_ABSORPTION_FACTOR, AIR_ABSORPTION);
                AL10.alGetError(); // without EFX the call only flags an error; clear it so the game does not log it as its own
            } catch (Throwable ignored) {} // no EFX: no air absorption
            fed = -1;
        } catch (Throwable e) {
            source = -1;
        }
    }

    /** Drops the buffers the source has played. */
    private void reclaim() {
        for (int n = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED); n > 0; n--) {
            recycle(AL10.alSourceUnqueueBuffers(source));
            queued.pollFirst();
        }
    }

    /** Keeps an unqueued buffer to fill again; past the pool's size it is freed. */
    private void recycle(int buffer) {
        if (free.size() < POOL) free.addLast(buffer);
        else AL10.alDeleteBuffers(buffer);
    }

    private long queuedFrames() {
        long frames = 0;
        for (int[] b : queued) frames += b[1];
        return frames;
    }

    /** Starts over from where the song is: first start, after a lag spike, or once it drifted. */
    private void restart(long cursor) {
        AL10.alSourceStop(source);
        for (int n = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED); n > 0; n--) recycle(AL10.alSourceUnqueueBuffers(source));
        queued.clear();
        fed = cursor;
    }

    private void feed(MusicPulse.Track t, long until) {
        while (fed < until) {
            int n = t.copyLooped(fed, PIECE, in);
            if (n <= 0) return;
            if (fx != null && t.deck) fx.process(in, n);
            if (split != null) split.process(in, n);
            if (drive != 1f) for (int i = 0; i < n; i++) in[i] *= drive;
            limiter.process(in, n);
            filter.process(in, 0, n, out);
            pcm.clear();
            for (int i = 0; i < n; i++) {
                float s = Math.max(-1f, Math.min(1f, out[i]));
                pcm.putShort((short) (s * 32767));
            }
            pcm.flip();
            int buffer = free.isEmpty() ? AL10.alGenBuffers() : free.pollFirst();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, pcm, (int) rate);
            AL10.alSourceQueueBuffers(source, buffer);
            queued.addLast(new int[] {buffer, n});
            fed += n;
        }
    }

    /**
     * What stands between the listener and this speaker. Four rays - to the speaker and to points
     * above and beside it - tell how walled off it is (sound bends around door frames and corners,
     * so one clear ray is a partly open path); the middle one counts the solid blocks in the way.
     */
    private void hear(Level world, Entity listener) {
        Vec3 eye = listener.getEyePosition();
        // The air just in front of the mouth, so a stacked neighbour, the wall behind or a low
        // ceiling never counts as something between you and the speaker.
        Vec3 air = pos.add(front.scale(0.6));
        Vec3 toward = air.subtract(eye);
        Vec3 side = new Vec3(-toward.z, 0, toward.x);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize().scale(0.8);
        int middle = wallsBetween(world, eye, air, listener);
        int blocked = middle > 0 ? 1 : 0;
        for (Vec3 around : new Vec3[] {air.add(0, 0.8, 0), air.add(side), air.subtract(side)}) {
            if (wallsBetween(world, eye, around, listener) > 0) blocked++;
        }
        targetMuffle = WallFilter.muffleFor(middle, blocked);
        targetWalls = middle;
    }

    /** Solid blocks along the line, the speaker's own block not counted; stops counting at 5. */
    private int wallsBetween(Level world, Vec3 from, Vec3 to, Entity listener) {
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 1e-3) return 0;
        dir = dir.scale(1 / length);
        int walls = 0;
        Vec3 at = from;
        while (walls < 5) {
            BlockHitResult hit = world.clip(new ClipContext(at, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, listener));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(home)) break;
            // Right at the end of the ray is the speaker's own surroundings, not a wall in between.
            if (hit.getLocation().distanceToSqr(to) < 0.36) break;
            BlockPos wall = hit.getBlockPos();
            // Other speakers in a stack or line array are not walls either.
            // (Amp racks are speaker blocks too; subwoofers are not, but stand in the same stacks.)
            net.minecraft.world.level.block.Block block = world.getBlockState(wall).getBlock();
            if (!(block instanceof com.createbrewery.block.club.SpeakerBlock || block instanceof com.createbrewery.block.club.SubwooferBlock)) walls++;
            // On through this block to where the ray leaves it.
            at = hit.getLocation();
            for (int i = 0; i < 40 && BlockPos.containing(at).equals(wall); i++) at = at.add(dir.scale(0.05));
            if (at.distanceToSqr(from) >= length * length) break;
        }
        return walls;
    }

    /** How hard the limiter worked since last asked: 1 not at all .. 0. */
    float takeReduction() {
        return limiter.takeReduction();
    }

    private boolean deleted;

    void delete() {
        if (deleted) return;
        deleted = true;
        // The context was recreated (F3+T, another device): every name here is stale, and deleting one could
        // hit a new sound's buffer or flag an error the game would log as its own. Only the Java side goes.
        boolean stale = context != 0 && org.lwjgl.openal.ALC10.alcGetCurrentContext() != context;
        if (stale) {
            source = -1;
            sendFilter = -1;
            free.clear();
            queued.clear();
            MemoryUtil.memFree(pcm);
            return;
        }
        if (source > 0) {
            try {
                if (AL10.alIsSource(source)) {
                    AL10.alSourceStop(source);
                    for (int n = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED); n > 0; n--) AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(source));
                    AL10.alDeleteSources(source);
                }
            } catch (Throwable ignored) {}
        }
        source = -1;
        if (sendFilter >= 0) ReverbBus.deleteFilter(sendFilter);
        sendFilter = -1;
        try {
            for (int buffer : free) AL10.alDeleteBuffers(buffer);
        } catch (Throwable ignored) {}
        AL10.alGetError();
        free.clear();
        queued.clear();
        MemoryUtil.memFree(pcm);
    }
}
