package com.createbrewery.drunk;

import com.createbrewery.Config;
import com.createbrewery.block.club.AmpRackBlockEntity;
import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.block.club.SpeakerBlockEntity;
import com.createbrewery.block.club.SubwooferBlockEntity;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.SOFTSourceLatency;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listens to whatever song is playing - vanilla music, jukebox records, or any mod's (Etched
 * discs and album jukeboxes included) - so a rave on MDMA and the club lights move with the real
 * music, and plays the songs out of the club's speakers.
 *
 * <p>Every streamed sound's audio is wrapped as it is attached to its channel (see
 * {@code ChannelMixin}), and each buffer read goes through a {@link KickDetector}. Once the sound
 * turns out to be MUSIC or RECORDS it is kept. To know what is audible right now, OpenAL is asked
 * where the channel is playing: the buffers still queued are the last ones read, and the offset
 * (minus the output latency) is how far into the first of them it has got. No guessing, so it
 * stays in step through lag and pauses.
 *
 * <p>A song that plays somewhere in the world is not heard from the game's own source (that is
 * muted) but from {@link Emitter}s: the speakers linked to the booth playing it, or else one at the
 * jukebox or booth itself. Each is fed the same samples in step, filtered for the walls between it
 * and you, so a club sounds like a club from the dance floor, the toilets and the street.
 */
public final class MusicPulse {
    private MusicPulse() {}

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** One buffer as the channel queued it: where it starts in the song, what was heard in it, and its samples in mono. */
    private record Chunk(long start, int frames, float[][] heard, float[] mono) {}

    private static final float[] CURRENT_SLICE = new float[KickDetector.CHANNELS];
    /** With linked speakers the booth itself is only the DJ's monitor. */
    private static final float MONITOR = 0.2f;

    /** How loud a place plays a song, and which part of it (see {@link Emitter#band}). */
    private record Want(float level, int band, float drive, double delay, boolean pa) {}

    /** Rays cast per frame across a song's speakers (see {@link Emitter#update}). */
    private static final int RAYS_PER_FRAME = 4;
    private static int raysLeft;

    private static final RoomProbe ROOM = new RoomProbe();

    /** Once a frame: keeps the shared reverb on the room around the listener while any club speaker plays, and frees it when none does. */
    private static void reverb(Minecraft mc, LocalPlayer player, double now, float dt) {
        boolean any = false;
        for (Track t : tracks) {
            if (!t.emitters.isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) {
            ReverbBus.release();
            ROOM.reset();
            return;
        }
        if (player == null || mc.level == null || !ReverbBus.ensure()) return;
        ROOM.update(mc.level, player, now, dt);
        boolean on = Config.CLIENT_SPEC.isLoaded() && Config.CLUB_REVERB.get();
        float amount = on ? Config.REVERB_AMOUNT.get().floatValue() : 0f;
        if (ROOM.params() != null) ReverbBus.apply(ROOM.params(), amount, now);
    }

    private static int maxSpeakers() {
        return Config.CLIENT_SPEC.isLoaded() ? Config.MAX_SPEAKERS.get() : 12;
    }

    /** A speaker's delay-tower alignment in seconds, or -1 with the speed of sound off. */
    private static double align(boolean flight, Vec3 speaker, Vec3 booth, double nearest) {
        return flight ? Math.max(0, speaker.distanceTo(booth) - nearest) / DeckFx.SPEED_OF_SOUND : -1;
    }

    /** Client: per booth, how hard its amps are limiting (1 not at all .. 0), for the rack's clip light. */
    private static final Map<BlockPos, Float> LIMITING = new ConcurrentHashMap<>();

    /** The lowest limiter gain on the booth's speakers of late: under 1 the amps are being driven into their limiters. */
    public static float limiting(BlockPos booth) {
        return LIMITING.getOrDefault(booth, 1f);
    }

    public static final class Track {
        final int source;
        /** Its channel: OpenAL hands a freed source's number straight to the next sound, so this is what tells songs apart. */
        final Channel channel;
        public final float rate;
        final int perSlice;
        /** The buffers read and not yet played out, in the order the channel queued them. */
        final List<Chunk> chunks = new CopyOnWriteArrayList<>();
        final KickDetector detector = new KickDetector();
        volatile SoundInstance sound;
        volatile boolean ignored;
        /** Frames read so far (sound thread). */
        volatile long read;
        volatile int kicks;
        /** The gain the game set; what is set on the source is this times the mix. */
        volatile float gain;
        /** The game's later volume changes come through {@link #holdVolume}, not the source. */
        volatile boolean holding;
        /** This song's own kick (times its deck gain), dying away like the global one: what a club light near it follows. */
        float pulse;
        /** This song's own beats and drops, heard unmixed so its tempo stays known while it is faded out. */
        final DropDetector song = new DropDetector();
        /** Played from a DJ booth deck: its crossfader gain and pitch; 1 otherwise. */
        float mix = 1f, pitch = 1f;
        /** Where it is heard from, by position (render thread). */
        final Map<Vec3, Emitter> emitters = new HashMap<>();
        /** The DJ's headphones, while this deck is cued on this client; null otherwise. */
        Emitter phones;
        /** The deck's mixer channel: EQ knobs (high, mid, low), filter, effect and send; see {@link DeckFx#set}. */
        final float[] eq = {0.5f, 0.5f, 0.5f};
        float filter, fxAmount;
        int fx;
        int colorType = DeckFx.COLOR_FILTER;
        float colorParam = 0.5f;
        float beatFxBeats = 1.0f;
        /** The song frame of the last beat heard, and the loop playing (slip mode), in song frames; 0 long while not looping. */
        long lastBeat = -1;
        public volatile long loopStart, loopLen;
        int loopSerial = Integer.MIN_VALUE;
        /** Played from a DJ booth deck (render thread): only then does it get the mixer channel. */
        public volatile boolean deck;
        public BlockPos boothPos;
        public int deckIndex = -1;

        AudioStream delegate;
        final Object streamLock = new Object();
        final java.util.Queue<ByteBuffer> pendingBuffers = new java.util.concurrent.ConcurrentLinkedQueue<>();
        volatile boolean eof;
        volatile boolean preloading;
        public volatile long deckFrame;
        public volatile long slipFrame;
        public volatile long cueFrame = 0;
        public volatile boolean auditioning = false;
        public final long[] hotCues = {-1, -1, -1, -1, -1, -1, -1, -1};
        public volatile long manualLoopIn = -1, manualLoopOut = -1;
        public volatile boolean masterTempo = true;
        public volatile int lastSeekSerial = 0;

        public long getEffectiveFrame() {
            long len = loopLen;
            long start = loopStart;
            long cur = deckFrame;
            if (len > 0 && cur >= start) {
                return start + ((cur - start) % len);
            }
            return Math.max(0, cur);
        }

        public long getLoopStart() { return loopStart; }
        public long getLoopLen() { return loopLen; }
        volatile boolean deckFrameInitialized;
        private double lastUpdateTime;

        /** The song frame heard right now (the output latency taken off); -1 until known. */
        long heard = -1;

        /** Game volume changes held since it started (see {@link #holdVolume}), for the log. */
        volatile int held;

        Track(Channel channel, int source, AudioFormat format) {
            this.channel = channel;
            this.source = source;
            this.rate = format.getSampleRate();
            this.perSlice = Math.max(1, (int) (rate * KickDetector.SLICE));
        }

        ByteBuffer readNext(int size, AudioStream stream) throws IOException {
            synchronized (streamLock) {
                if (!pendingBuffers.isEmpty()) {
                    return pendingBuffers.poll();
                }
                if (eof) return null;
                ByteBuffer buffer = stream.read(size);
                if (buffer != null && !ignored) {
                    processDecodedBuffer(buffer);
                } else if (buffer == null) {
                    eof = true;
                }
                return buffer;
            }
        }

        void processDecodedBuffer(ByteBuffer buffer) {
            AudioFormat format = delegate != null ? delegate.getFormat() : new AudioFormat(rate, 16, 2, true, false);
            float[][] heard = detector.slices(format, buffer.duplicate());
            for (float k : heard[KickDetector.KICK]) if (k > 0.5f) kicks++;
            float[] mono = MusicPulse.mono(format, buffer.duplicate());
            chunks.add(new Chunk(read, mono.length, heard, mono));
            read += mono.length;
        }

        public void pumpAhead(long untilFrame) {
            if (delegate == null || eof) return;
            synchronized (streamLock) {
                while (read < untilFrame && !eof) {
                    try {
                        ByteBuffer buffer = delegate.read(16384);
                        if (buffer == null || buffer.remaining() == 0) {
                            eof = true;
                            break;
                        }
                        processDecodedBuffer(buffer);
                        pendingBuffers.add(buffer);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to pump audio ahead", e);
                        eof = true;
                        break;
                    }
                }
            }
        }

        void startPreload() {
            if (preloading || eof || delegate == null) return;
            preloading = true;
            Thread preloader = new Thread(() -> {
                try {
                    while (!eof && AL10.alIsSource(source)) {
                        synchronized (streamLock) {
                            if (eof) break;
                            ByteBuffer buf = delegate.read(16384);
                            if (buf == null || buf.remaining() == 0) {
                                eof = true;
                                break;
                            }
                            processDecodedBuffer(buf);
                            pendingBuffers.add(buf);
                        }
                        Thread.sleep(1);
                    }
                } catch (Exception ignored) {
                }
            }, "Brewery-Deck-Preload");
            preloader.setDaemon(true);
            preloader.start();
        }

        public void scrub(long deltaFrames) {
            deckFrame = Math.max(0, deckFrame + deltaFrames);
            if (deltaFrames > 0 && !eof) {
                pumpAhead(deckFrame + (long) (5 * rate));
            }
        }

        /** Returns {bass, loud, high, kick} for the given frame, or null if outside loaded audio. */
        public float[] getWaveformSlice(long frame) {
            if (frame < 0 || chunks.isEmpty()) return null;
            int low = 0, high = chunks.size() - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                Chunk c = chunks.get(mid);
                if (frame < c.start()) {
                    high = mid - 1;
                } else if (frame >= c.start() + c.frames()) {
                    low = mid + 1;
                } else {
                    float[][] heard = c.heard();
                    int sliceIdx = Math.min(heard[0].length - 1, (int) ((frame - c.start()) / perSlice));
                    if (sliceIdx >= 0) {
                        return new float[] {
                            heard[KickDetector.BASS][sliceIdx],
                            heard[KickDetector.LOUD][sliceIdx],
                            heard[KickDetector.HIGH][sliceIdx],
                            heard[KickDetector.KICK][sliceIdx]
                        };
                    }
                    return null;
                }
            }
            return null;
        }

        public double getBeatPeriod() {
            return song.period();
        }

        public int getBeats() {
            return song.beats;
        }

        public long getFramesRead() {
            return read;
        }

        /** Copies up to {@code n} mono samples from song frame {@code from} on; how many there were. */
        int copy(long from, int n, float[] into) {
            int got = 0;
            for (Chunk c : chunks) {
                if (got >= n) break;
                long at = from + got;
                if (at < c.start || at >= c.start + c.frames) continue;
                int off = (int) (at - c.start), take = Math.min(n - got, c.frames - off);
                System.arraycopy(c.mono, off, into, got, take);
                got += take;
            }
            return got;
        }

        /** Like {@link #copy}, but inside a loop: from its end the song frames wrap back to its start. */
        int copyLooped(long from, int n, float[] into) {
            if (loopLen <= 0 || from < loopStart) return copy(from, n, into);
            long at = DeckFx.loopFrame(from, loopStart, loopLen);
            int got = copy(at, (int) Math.min(n, loopStart + loopLen - at), into);
            // A short fade either side of the splice, so the wrap does not click.
            int fade = Math.max(1, (int) (rate * 0.003));
            boolean wrapped = from >= loopStart + loopLen;
            for (int i = 0; i < got; i++) {
                long f = at + i;
                float g = 1f;
                if (wrapped && f - loopStart < fade) g = (f - loopStart) / (float) fade;
                long toEnd = loopStart + loopLen - f;
                if (toEnd <= fade) g = Math.min(g, (toEnd - 1) / (float) fade);
                into[i] *= Math.max(0f, g);
            }
            return got;
        }

        /**
         * Played at a fixed spot in the world. Background music plays everywhere, and a moving
         * one (a minecart or backpack jukebox) is left to the game, which follows it about.
         */
        /** Where a ticking sound was first heard: one that stays there (an Etched record) is placed, one that moves is not. */
        private Vec3 origin;

        boolean placed() {
            SoundInstance s = sound;
            if (s == null || s.isRelative() || s.getAttenuation() == SoundInstance.Attenuation.NONE || (s.getX() == 0 && s.getY() == 0 && s.getZ() == 0)) return false;
            if (!(s instanceof net.minecraft.client.resources.sounds.TickableSoundInstance)) return true;
            // Etched plays its records as ticking sounds that never move; a minecart's does move.
            Vec3 at = new Vec3(s.getX(), s.getY(), s.getZ());
            if (origin == null) origin = at;
            return origin.distanceToSqr(at) < 1e-4;
        }
    }

    /** Headphone cue on this client: the booth and deck the DJ listens to (-1: none). */
    private static BlockPos cueBooth;
    private static int cueDeck = -1;
    /** Headphones are only worn at the booth. */
    private static final double CUE_REACH = 6.0;
    private static final float CUE_GAIN = 0.7f;

    /** Client: cue {@code deck} of the booth at {@code booth} in the headphones, or the same again to take them off. */
    public static void toggleCue(BlockPos booth, int deck) {
        if (booth.equals(cueBooth) && cueDeck == deck) {
            cueBooth = null;
            cueDeck = -1;
        } else {
            cueBooth = booth.immutable();
            cueDeck = deck;
        }
    }

    public static boolean isCued(BlockPos booth, int deck) {
        return booth.equals(cueBooth) && cueDeck == deck;
    }

    /** Wrapped, but not yet known what it is (see {@link #onStream}). */
    private static final Map<Channel, Track> starting = new ConcurrentHashMap<>();
    private static final List<Track> tracks = new CopyOnWriteArrayList<>();
    private static double lastFrame;
    private static boolean playing;
    private static Boolean latencyKnown;
    private static final double[] offsetAndLatency = new double[2];
    /** What is heard right now, weighted by how close the song is; see {@link #update}. */
    static float kick, level, hats;
    /** Beats, tempo, build-ups and drops of what is heard. */
    static final DropDetector song = new DropDetector();

    static void init() {
        NeoForge.EVENT_BUS.addListener(MusicPulse::onStream);
    }

    /** On the sound thread, as a stream is attached to its channel (ChannelMixin). */
    public static AudioStream listen(AudioStream stream, Channel channel, int source) {
        if (stream.getFormat().getSampleSizeInBits() != 16) return stream;
        Track track = new Track(channel, source, stream.getFormat());
        track.delegate = stream;
        starting.put(channel, track);
        return new Listening(stream, track);
    }

    /** On the sound thread, right after the channel started: keep only songs. */
    private static void onStream(PlayStreamingSourceEvent event) {
        Track track = starting.remove(event.getChannel());
        if (track == null) return;
        SoundInstance sound = event.getSound();
        if (sound.getSource() != SoundSource.MUSIC && sound.getSource() != SoundSource.RECORDS) {
            track.ignored = true;
            track.chunks.clear();
            return;
        }
        track.sound = sound;
        if (Config.CLIENT_SPEC.isLoaded() && Config.RECORD_MUSIC.get()) trace(track, sound);
        tracks.add(track);
        LOGGER.info("MDMA hears {} ({} Hz)", sound.getLocation(), track.rate);
    }

    /** Starts writing what the detector hears of this song to logs/brewery-traces (Config.RECORD_MUSIC). */
    private static void trace(Track track, SoundInstance sound) {
        try {
            java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve("brewery-traces");
            java.nio.file.Files.createDirectories(dir);
            String name = sound.getLocation().toString().replaceAll("[^a-zA-Z0-9._-]", "_") + "-"
                + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
            java.io.PrintWriter out = new java.io.PrintWriter(java.nio.file.Files.newBufferedWriter(dir.resolve(name)), true); // flushed per line: survives quitting mid-song
            out.println(KickDetector.TRACE_HEADER);
            // Buffers queued before the song was known are already heard: keep the song clock right.
            track.detector.traced = track.read / track.perSlice;
            track.detector.trace = out;
            LOGGER.info("MDMA records {} to {}", sound.getLocation(), dir.resolve(name));
        } catch (IOException e) {
            LOGGER.warn("Could not record music trace", e);
        }
    }

    /** Hands every buffer on unchanged, after noting what is in it. */
    private record Listening(AudioStream delegate, Track track) implements AudioStream {
        @Override
        public AudioFormat getFormat() {
            return delegate.getFormat();
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            return track.readNext(size, delegate);
        }

        @Override
        public void close() throws IOException {
            synchronized (track.streamLock) {
                delegate.close();
            }
        }
    }

    /** 16-bit PCM, any channel count, as mono floats: speakers in the world are points, so they play mono. */
    private static float[] mono(AudioFormat format, ByteBuffer pcm) {
        pcm.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int channels = Math.max(1, format.getChannels());
        float[] out = new float[pcm.remaining() / (2 * channels)];
        for (int i = 0; i < out.length; i++) {
            float sum = 0f;
            for (int c = 0; c < channels; c++) sum += pcm.getShort() / 32768f;
            out[i] = sum / channels;
        }
        return out;
    }

    /** True while a song can be heard. Updated by {@link #update}. */
    public static boolean playing() {
        return playing;
    }

    public static float kick() {
        return kick;
    }

    public static float drop() {
        return song.drop;
    }

    public static int beats() {
        return song.beats;
    }

    /**
     * Once a frame: what is heard right now. Kicks and hats flash up and die away quickly, the
     * level follows smoothly. All 0 without music - no music, no beat.
     */
    static void update() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        double now = System.nanoTime() / 1e9;
        float dt = (float) Math.min(0.1, now - lastFrame);
        lastFrame = now;
        raysLeft = RAYS_PER_FRAME;
        float k = 0f, l = 0f, h = 0f, bass = 0f, loud = 0f, high = 0f;
        boolean heard = false;
        for (Track t : tracks) {
            if (!mc.getSoundManager().isActive(t.sound) || !AL10.alIsSource(t.source)) {
                t.emitters.values().forEach(Emitter::delete);
                t.emitters.clear();
                if (t.phones != null) t.phones.delete();
                t.phones = null;
                tracks.remove(t);
                LOGGER.info("MDMA heard {}: {} kicks, {} volume changes held", t.sound.getLocation(), t.kicks, t.held);
                java.io.PrintWriter trace = t.detector.trace;
                t.detector.trace = null;
                if (trace != null) trace.close();
                continue;
            }
            deck(t, mc);
            long cursor = cursor(t);
            boolean known = heardNow(t, CURRENT_SLICE);
            play(t, mc, player, cursor, now, dt);

            // A deck faded out on the crossfader is not heard, so it moves nothing.
            float near = (player == null ? 0f : closeness(t, player.position())) * t.mix;
            heard |= near > 0.05f;
            float kickNow = known ? CURRENT_SLICE[KickDetector.KICK] : 0f;
            t.pulse = Math.max(kickNow * t.mix, t.pulse * (float) Math.exp(-dt * Math.max(9.0, 4.5 / t.song.period())));
            if (!known) continue;
            int beatsBefore = t.song.beats;
            t.song.hear(kickNow, CURRENT_SLICE[KickDetector.LEVEL], CURRENT_SLICE[KickDetector.HATS], CURRENT_SLICE[KickDetector.BASS],
                CURRENT_SLICE[KickDetector.LOUD], CURRENT_SLICE[KickDetector.HIGH], true, now, dt);
            // Where a loop set now would start: on the beat as heard, a few ms early so the splice misses the kick.
            if (t.song.beats != beatsBefore && t.heard >= 0) t.lastBeat = Math.max(0, t.heard - (long) (t.rate * 0.004));
            k = Math.max(k, kickNow * near);
            l = Math.max(l, CURRENT_SLICE[KickDetector.LEVEL] * near);
            h = Math.max(h, CURRENT_SLICE[KickDetector.HATS] * near);
            bass = Math.max(bass, CURRENT_SLICE[KickDetector.BASS] * near);
            loud = Math.max(loud, CURRENT_SLICE[KickDetector.LOUD] * near);
            high = Math.max(high, CURRENT_SLICE[KickDetector.HIGH] * near);
        }
        reverb(mc, player, now, dt);
        playing = heard;
        song.hear(k, l, h, bass, loud, high, heard, now, dt);
        // At techno tempos each kick dies away faster, so hits stay apart instead of smearing.
        kick = Math.max(k, kick * (float) Math.exp(-dt * Math.max(9.0, 4.5 / song.period())));
        hats = Math.max(h, hats * (float) Math.exp(-dt * 14.0));
        // Up fast, down slowly: loud bits hit at once, the quiet comes in gently.
        level += (l - level) * (1f - (float) Math.exp(-dt * (l > level ? 25.0 : 4.0)));
    }

    /**
     * Sends the song where it is heard from. Background music is left to the game; a song in the
     * world goes out of its speakers (the linked ones of the booth playing it, or one at the
     * jukebox or booth itself) and the game's own source is muted.
     */
    private static void play(Track t, Minecraft mc, LocalPlayer player, long cursor, double now, float dt) {
        float base = baseGain(t);
        float lift = 1f + RollClient.peak; // at the peak the music sounds louder
        if (!t.placed() || mc.level == null) {
            setGain(t, base * lift * t.mix);
            return;
        }
        setGain(t, 0f);

        Vec3 at = new Vec3(t.sound.getX(), t.sound.getY(), t.sound.getZ());
        DjBoothBlockEntity dj = DjBoothBlockEntity.playingAt(mc.level, BlockPos.containing(at));
        List<SpeakerBlockEntity> linked = dj == null ? List.of() : SpeakerBlockEntity.linked(mc.level, dj.getBlockPos());
        // The booth's amp rack (the first, if there are two), its speakers and its switched-on subwoofers.
        AmpRackBlockEntity rack = null;
        List<Vec3> tops = new ArrayList<>(), subs = new ArrayList<>();
        for (SpeakerBlockEntity s : linked) {
            if (s instanceof AmpRackBlockEntity r) {
                if (rack == null) rack = r;
            } else if (s instanceof SubwooferBlockEntity sub) {
                if (sub.isActive()) subs.add(sub.mouth());
            } else if (s.isSpeaker()) {
                tops.add(s.mouth());
            }
        }
        // Without a rack the speakers play everything and the subs only thump; with one and subs to
        // drive, it splits at the crossover: the lows to the subs, the rest to the speakers.
        boolean split = rack != null && !subs.isEmpty();
        float topDrive = rack == null ? 1f : DeckFx.eqGain(rack.getTopGain());
        // Speed of sound (off unless the rack has it on): every speaker is heard as late as its sound
        // takes to reach you, and those further from the booth than the nearest are held back by the
        // extra distance - delay towers, so the far ones land in step with the main stack.
        boolean flight = rack != null && rack.isPropagation();
        Vec3 boothAt = dj == null ? at : Vec3.atCenterOf(dj.getBlockPos());
        double ref = Double.MAX_VALUE;
        for (Vec3 p : tops) ref = Math.min(ref, p.distanceTo(boothAt));
        for (Vec3 p : subs) ref = Math.min(ref, p.distanceTo(boothAt));
        Map<Vec3, Want> wanted = new HashMap<>();
        for (Vec3 top : tops) wanted.put(top, new Want(1f, split ? Emitter.HIGH : Emitter.FULL, topDrive, align(flight, top, boothAt, ref), true));
        if (split) {
            float subDrive = DeckFx.eqGain(rack.getSubGain());
            for (Vec3 sub : subs) wanted.put(sub, new Want(1f, Emitter.LOW, subDrive, align(flight, sub, boothAt, ref), true));
        }
        wanted.putIfAbsent(at, new Want(wanted.isEmpty() ? 1f : MONITOR, Emitter.FULL, 1f, flight ? 0 : -1, false));
        float crossover = rack == null ? 100f : rack.getCrossover();

        // Only the nearest speakers play (subwoofers first): each is a source with its own DSP.
        Vec3 ear = player == null ? at : player.getEyePosition();
        List<EmitterBudget.Offer<Vec3>> offers = new ArrayList<>();
        for (Map.Entry<Vec3, Want> w : wanted.entrySet()) {
            offers.add(new EmitterBudget.Offer<>(w.getKey(), w.getKey().distanceToSqr(ear), w.getValue().band() == Emitter.LOW));
        }
        wanted.keySet().retainAll(EmitterBudget.choose(offers, maxSpeakers(), t.emitters.keySet()));
        // A stack adds up: each speaker of a band near you backs off by the square root of how many there are.
        int[] near = new int[3];
        double radiusSq = PaLevel.STACK_RADIUS * PaLevel.STACK_RADIUS;
        for (Map.Entry<Vec3, Want> w : wanted.entrySet()) {
            if (w.getValue().pa() && w.getKey().distanceToSqr(ear) <= radiusSq) near[w.getValue().band()]++;
        }

        for (Iterator<Map.Entry<Vec3, Emitter>> it = t.emitters.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Vec3, Emitter> e = it.next();
            if (!wanted.containsKey(e.getKey())) {
                e.getValue().delete();
                it.remove();
            }
        }
        // The ones that went longest without a ray get this frame's rays.
        List<Map.Entry<Vec3, Want>> order = new ArrayList<>(wanted.entrySet());
        order.sort(java.util.Comparator.comparingDouble(w -> {
            Emitter known = t.emitters.get(w.getKey());
            return known == null ? -2 : known.lastRay;
        }));
        for (Map.Entry<Vec3, Want> w : order) {
            Emitter e = t.emitters.computeIfAbsent(w.getKey(), pos -> new Emitter(pos, w.getValue().level(), t.rate));
            e.level = w.getValue().level();
            e.band = w.getValue().band();
            e.drive = w.getValue().drive();
            e.delay = w.getValue().delay();
            e.crossover = crossover;
            e.stack = w.getValue().pa() ? PaLevel.stackGain(near[w.getValue().band()]) : 1f;
            if (e.update(t, t.source, cursor, base * lift * t.mix, t.pitch, mc.level, player, now, dt, raysLeft > 0)) raysLeft--;
        }
        if (dj != null) {
            // The clip light: falls at once with the gain, lets go over about a second.
            float worst = 1f;
            for (Emitter e : t.emitters.values()) worst = Math.min(worst, e.takeReduction());
            float shown = limiting(dj.getBlockPos());
            LIMITING.put(dj.getBlockPos(), Math.min(worst, shown + (1f - shown) * Math.min(1f, dt * 2f)));
        }

        // The DJ's headphones: the cued deck whatever the crossfader says, so the next record can be
        // lined up before the crowd hears it.
        boolean cued = dj != null && player != null && dj.getBlockPos().equals(cueBooth) && dj.deckAt(BlockPos.containing(at)) == cueDeck
            && player.position().closerThan(Vec3.atCenterOf(cueBooth), CUE_REACH);
        if (cued) {
            if (t.phones == null) t.phones = Emitter.headphones(t.rate);
            t.phones.update(t, t.source, cursor, base * CUE_GAIN, t.pitch, mc.level, player, now, dt, false);
        } else if (t.phones != null) {
            t.phones.delete();
            t.phones = null;
        }
    }

    /**
     * Sound thread (ChannelMixin): the game setting a song's volume. Once the song is known the
     * volume is only noted, and set here each frame - on a muted source it would otherwise leak out.
     */
    public static boolean holdVolume(Channel channel, float volume) {
        for (Track t : tracks) {
            if (t.channel == channel && t.holding) {
                t.gain = volume;
                t.held++;
                return true;
            }
        }
        return false;
    }

    /** Sound thread (ChannelMixin): a deck's pitch is the booth's, not the game's. */
    public static boolean holdPitch(Channel channel) {
        for (Track t : tracks) if (t.channel == channel && t.deck) return true;
        return false;
    }

    /** The gain the game gives the song (volume sliders, a moving sound); what is set here on top is told apart. */
    private static float baseGain(Track t) {
        if (!t.holding) {
            // The volume the game started it at; later ones come through holdVolume.
            t.gain = AL10.alGetSourcef(t.source, AL10.AL_GAIN);
            t.holding = true;
        }
        return t.gain;
    }

    private static void setGain(Track t, float gain) {
        AL10.alSourcef(t.source, AL10.AL_MAX_GAIN, 2f);
        AL10.alSourcef(t.source, AL10.AL_GAIN, gain);
    }

    /** A DJ booth deck playing this song: take its crossfader gain and pitch. */
    private static void deck(Track t, Minecraft mc) {
        t.mix = t.pitch = 1f;
        DjBoothBlockEntity dj = null;
        BlockPos at = null;
        if (mc.level != null && t.sound != null) {
            at = BlockPos.containing(t.sound.getX(), t.sound.getY(), t.sound.getZ());
            dj = DjBoothBlockEntity.playingAt(mc.level, at);
        }
        if (dj == null) {
            t.deck = false;
            t.boothPos = null;
            t.deckIndex = -1;
            return;
        }
        int deck = dj.deckAt(at);
        t.deck = true;
        t.boothPos = dj.getBlockPos();
        t.deckIndex = deck;

        if (!t.deckFrameInitialized) {
            long c = cursor(t);
            t.deckFrame = Math.max(0, c);
            t.slipFrame = t.deckFrame;
            t.deckFrameInitialized = true;
            t.lastUpdateTime = System.nanoTime() / 1e9;
            t.startPreload();
        }

        double now = System.nanoTime() / 1e9;
        double dt = t.lastUpdateTime > 0 ? Math.min(0.1, now - t.lastUpdateTime) : 0.0;
        t.lastUpdateTime = now;

        boolean held = dj.isScratchHeld(deck) && dj.isVinylMode(deck);
        boolean playing = dj.isPlaying(deck) || t.auditioning;
        float speed = dj.getPitch(deck) * (dj.isReverse(deck) ? -1f : 1f);

        if (playing) {
            t.slipFrame = Math.max(0, t.slipFrame + (long) (dt * t.rate * speed));
            if (!held) {
                t.deckFrame = Math.max(0, t.deckFrame + (long) (dt * t.rate * speed));
            }
        }

        t.pumpAhead(Math.max(t.deckFrame, t.slipFrame) + (long) (10 * t.rate));

        t.mix = playing ? dj.deckGain(deck, mc.level.getGameTime()) : 0f;
        if (held) {
            t.mix = 0f;
        }
        t.pitch = dj.getPitch(deck);
        AL10.alSourcef(t.source, AL10.AL_PITCH, t.pitch);
        t.eq[0] = dj.getEq(deck, DjBoothBlockEntity.HIGH);
        t.eq[1] = dj.getEq(deck, DjBoothBlockEntity.MID);
        t.eq[2] = dj.getEq(deck, DjBoothBlockEntity.LOW);
        t.filter = dj.getFilter(deck);
        if (dj.isBeatFxOn() && (dj.getBeatFxChannel() == -1 || dj.getBeatFxChannel() == deck)) {
            t.fx = switch (dj.getBeatFxType()) {
                case DjBoothBlockEntity.BFX_DELAY -> DeckFx.DELAY;
                case DjBoothBlockEntity.BFX_ECHO -> DeckFx.ECHO;
                case DjBoothBlockEntity.BFX_REVERB -> DeckFx.REVERB;
                case DjBoothBlockEntity.BFX_FLANGER -> DeckFx.FLANGER;
                case DjBoothBlockEntity.BFX_PHASER -> DeckFx.PHASER;
                case DjBoothBlockEntity.BFX_ROLL -> DeckFx.ROLL;
                case DjBoothBlockEntity.BFX_TRANS -> DeckFx.TRANS;
                case DjBoothBlockEntity.BFX_HELIX -> DeckFx.HELIX;
                case DjBoothBlockEntity.BFX_PINGPONG -> DeckFx.PINGPONG;
                default -> DeckFx.ECHO;
            };
            t.fxAmount = dj.getBeatFxDepth();
            t.beatFxBeats = dj.getBeatFxBeats();
        } else {
            t.fx = dj.getFx(deck);
            t.fxAmount = dj.getFxAmount(deck);
            t.beatFxBeats = 1.0f;
        }
        t.colorType = dj.getActiveColorFx();
        t.colorParam = dj.getColorFxParam();
        t.masterTempo = dj.isMasterTempo(deck);

        int sSerial = dj.getSeekSerial(deck);
        if (sSerial != t.lastSeekSerial) {
            t.lastSeekSerial = sSerial;
            long syncFrame = dj.getPlayheadFrame(deck);
            if (Math.abs(t.deckFrame - syncFrame) > t.rate * 0.05) {
                t.deckFrame = syncFrame;
                t.slipFrame = syncFrame;
            }
        }

        int serial = dj.getLoopSerial(deck), beats = dj.getLoopBeats(deck);
        if (serial != t.loopSerial) {
            boolean first = t.loopSerial == Integer.MIN_VALUE;
            t.loopSerial = serial;
            if (beats > 0 && !first && !t.chunks.isEmpty()) {
                if (t.manualLoopIn >= 0 && t.manualLoopOut > t.manualLoopIn) {
                    t.loopStart = t.manualLoopIn;
                    t.loopLen = t.manualLoopOut - t.manualLoopIn;
                } else {
                    double period = t.song.period();
                    if (period <= 0) period = 0.5; // fallback to 120 BPM
                    long beatFrames = Math.max(1, Math.round(period * t.rate));
                    long cur = t.getEffectiveFrame();
                    if (beatFrames > 0) cur = Math.round((double) cur / beatFrames) * beatFrames;
                    t.loopStart = Math.max(0, cur);
                    t.loopLen = Math.max(1, (long) (beats * beatFrames));
                }
                t.deckFrame = t.loopStart;
            } else if (beats == 0) {
                if (t.loopLen > 0) {
                    t.deckFrame = t.getEffectiveFrame();
                }
                t.loopLen = 0;
                t.manualLoopIn = -1;
                t.manualLoopOut = -1;
            }
        }
        if (beats == 0 && t.loopLen > 0) {
            t.deckFrame = t.getEffectiveFrame();
            t.loopLen = 0;
            t.manualLoopIn = -1;
            t.manualLoopOut = -1;
        }
    }

    /** How far a club light still follows a song, in blocks from where it is heard. */
    private static final double CLUB_REACH = 32.0;

    /** Where a song is heard from: its speakers, or where it plays. */
    private static Iterable<Vec3> heardFrom(Track t) {
        if (!t.emitters.isEmpty()) return t.emitters.keySet();
        SoundInstance s = t.sound;
        return List.of(new Vec3(s.getX(), s.getY(), s.getZ()));
    }

    private static boolean near(Track t, BlockPos pos) {
        // Background music plays everywhere and belongs to no club.
        if (!t.placed()) return false;
        for (Vec3 from : heardFrom(t)) if (pos.distToCenterSqr(from.x, from.y, from.z) < CLUB_REACH * CLUB_REACH) return true;
        return false;
    }

    /** The kick of the music playing near {@code pos}, 0..1; 0 when nothing plays near it. */
    public static float kickNear(BlockPos pos) {
        float k = 0f;
        for (Track t : tracks) if (near(t, pos)) k = Math.max(k, t.pulse);
        return k;
    }

    /** 1 at a drop of the music playing near {@code pos}, dying away over a second or two. */
    public static float dropNear(BlockPos pos) {
        float d = 0f;
        for (Track t : tracks) if (near(t, pos)) d = Math.max(d, t.song.drop * t.mix);
        return d;
    }

    /** 0..1 how far into a build-up the music playing near {@code pos} is. */
    public static float tensionNear(BlockPos pos) {
        float x = 0f;
        for (Track t : tracks) if (near(t, pos)) x = Math.max(x, t.song.tension * t.mix);
        return x;
    }

    /** Seconds per beat of the loudest-mixed music near {@code pos}; half a second (120 BPM) until known. */
    public static double periodNear(BlockPos pos) {
        Track best = null;
        for (Track t : tracks) if (near(t, pos) && t.song.beats >= 8 && (best == null || t.mix > best.mix)) best = t;
        return best == null ? 0.5 : best.song.period();
    }

    /** True while music can be heard near {@code pos}. */
    public static boolean playingNear(BlockPos pos) {
        for (Track t : tracks) if (near(t, pos) && t.mix > 0.05f) return true;
        return false;
    }

    /** Seconds per beat of the song playing from exactly {@code soundBlock}, as heard; 0 until eight beats are in. */
    public static double beatPeriodAt(BlockPos soundBlock) {
        for (Track t : tracks) {
            SoundInstance s = t.sound;
            if (s != null && t.song.beats >= 8 && BlockPos.containing(s.getX(), s.getY(), s.getZ()).equals(soundBlock)) {
                return t.song.period();
            }
        }
        return 0.0;
    }

    /** Camera shake from the kick of the speakers around you; behind a wall the thump is what gets through. */
    public static float getMusicBassShake(net.minecraft.world.entity.player.Player player) {
        if (player == null || tracks.isEmpty()) return 0f;
        float total = 0f;
        for (Track t : tracks) {
            // Each song shakes with its own kick: a quiet jukebox next door does not borrow the club's.
            float k = t.pulse;
            if (t.sound == null || k < 0.12f) continue;
            for (Emitter e : t.emitters.values()) {
                double distSq = player.distanceToSqr(e.pos);
                if (distSq >= 289.0) continue; // within 17 blocks
                float falloff = (float) Math.max(0.0, 1.0 - Math.sqrt(distSq) / 17.0);
                float wallFactor = e.muffle > 0.25f ? 1.25f : 0.85f;
                total += k * falloff * falloff * wallFactor * 0.75f * e.level * Math.min(1.5f, e.drive);
            }
        }
        return Math.min(1.2f, total);
    }

    /** The song frame the game's source is playing, or -1 if not known yet. Also forgets played buffers. */
    private static long cursor(Track t) {
        if (!AL10.alIsSource(t.source)) return -1;
        // ponytail: between the read and the queueing (microseconds, once a second) this is one buffer ahead.
        int head = t.chunks.size() - AL10.alGetSourcei(t.source, AL10.AL_BUFFERS_QUEUED);
        if (head < 0) return -1;
        // Buffers before the queue have played out; forget them so an hour-long stream stays a few chunks long.
        // One is kept: in the race above head is one too far, and dropping a still-queued chunk would misalign for good.
        // Two seconds are kept too, so a loop can start on a beat already played, and a loop keeps its own.
        // For DJ decks, all chunks are preserved so the DJ can scrub all the way back to the beginning.
        long keepFrom = t.deck ? Long.MIN_VALUE : (head < t.chunks.size() ? t.chunks.get(head).start() - (long) (2 * t.rate) : Long.MIN_VALUE);
        if (t.loopLen > 0) keepFrom = Math.min(keepFrom, t.loopStart);
        for (; head > 1 && t.chunks.get(0).start() + t.chunks.get(0).frames() < keepFrom; head--) t.chunks.remove(0);
        long frames = AL10.alGetSourcei(t.source, AL11.AL_SAMPLE_OFFSET);
        // The offset counts from the first queued buffer, which may already have played out.
        while (head < t.chunks.size() && frames >= t.chunks.get(head).frames()) frames -= t.chunks.get(head++).frames();
        return head < t.chunks.size() ? t.chunks.get(head).start() + frames : -1;
    }

    /** What is audible right now, or false if nothing is known yet. */
    private static boolean heardNow(Track t, float[] out) {
        if (t.deck) {
            t.heard = Math.max(0, t.deckFrame);
            float[] slice = t.getWaveformSlice(t.heard);
            if (slice != null) {
                out[KickDetector.BASS] = slice[0];
                out[KickDetector.LOUD] = slice[1];
                out[KickDetector.HIGH] = slice[2];
                out[KickDetector.KICK] = slice[3];
                out[KickDetector.LEVEL] = slice[1];
                out[KickDetector.HATS] = slice[2];
                return true;
            }
            return false;
        }
        if (!AL10.alIsSource(t.source) || t.chunks.isEmpty()) return false;
        int head = Math.max(0, t.chunks.size() - AL10.alGetSourcei(t.source, AL10.AL_BUFFERS_QUEUED));
        if (latencyKnown == null) latencyKnown = AL10.alIsExtensionPresent("AL_SOFT_source_latency");
        double frames;
        if (latencyKnown) {
            SOFTSourceLatency.alGetSourcedvSOFT(t.source, SOFTSourceLatency.AL_SEC_OFFSET_LATENCY_SOFT, offsetAndLatency);
            frames = Math.max(0.0, offsetAndLatency[0] - offsetAndLatency[1]) * t.rate;
        } else {
            frames = AL10.alGetSourcei(t.source, AL11.AL_SAMPLE_OFFSET);
        }
        while (head < t.chunks.size() && frames >= t.chunks.get(head).frames()) frames -= t.chunks.get(head++).frames();
        if (head >= t.chunks.size()) return false;
        t.heard = t.chunks.get(head).start() + (long) frames;
        float[][] heard = t.chunks.get(head).heard();
        int i = (int) (frames / t.perSlice);
        if (i >= heard[0].length) return false;
        out[KickDetector.KICK] = heard[KickDetector.KICK][i];
        out[KickDetector.LEVEL] = heard[KickDetector.LEVEL][i];
        out[KickDetector.HATS] = heard[KickDetector.HATS][i];
        out[KickDetector.BASS] = heard[KickDetector.BASS][i];
        out[KickDetector.LOUD] = heard[KickDetector.LOUD][i];
        out[KickDetector.HIGH] = heard[KickDetector.HIGH][i];
        return true;
    }

    /** 1 next to where the song is heard from (or for music that plays everywhere), 0 out of earshot. */
    private static float closeness(Track t, Vec3 listener) {
        SoundInstance sound = t.sound;
        if (!t.placed()) return 1f;
        float reach = Math.max(sound.getVolume(), 1f) * (sound.getSound() == null ? 16f : sound.getSound().getAttenuationDistance());
        float best = 0f;
        for (Vec3 from : heardFrom(t)) best = Math.max(best, (float) Math.max(0.0, 1.0 - Math.sqrt(listener.distanceToSqr(from)) / reach));
        return best;
    }

    /** Returns the Track playing on the given DJ booth and deck, or null if not currently playing. */
    public static Track trackFor(BlockPos boothPos, int deck) {
        if (boothPos == null) return null;
        for (Track t : tracks) {
            if (t.deck && boothPos.equals(t.boothPos) && t.deckIndex == deck) {
                return t;
            }
        }
        return null;
    }
}
