package com.createbrewery.drunk;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.SOFTSourceLatency;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listens to whatever song is playing - vanilla music, jukebox records, or any mod's (Etched
 * discs and album jukeboxes included) - so a rave on MDMA moves with the real music.
 *
 * <p>Every streamed sound's audio is wrapped as it is attached to its channel (see
 * {@code ChannelMixin}), and each buffer read goes through a {@link KickDetector}. Once the sound
 * turns out to be MUSIC or RECORDS it is kept. To know what is audible right now, OpenAL is asked
 * where the channel is playing: the buffers still queued are the last ones read, and the offset
 * (minus the output latency) is how far into the first of them it has got. No guessing, so it
 * stays in step through lag and pauses.
 */
public final class MusicPulse {
    private MusicPulse() {}

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** One buffer as the channel queued it: its length and what was heard in it. */
    private record Chunk(int frames, float[][] heard) {}

    private static final float[] CURRENT_SLICE = new float[3];

    private static final class Track {
        final int source;
        final float rate;
        final int perSlice;
        /** Every buffer read so far, in the order the channel queued them. */
        final List<Chunk> chunks = new CopyOnWriteArrayList<>();
        final KickDetector detector = new KickDetector();
        volatile SoundInstance sound;
        volatile boolean ignored;
        int kicks;
        /** The gain the game set, and the one set here on top of it (-1: none yet). */
        float gain, louder = -1f;
        int filter = -1;
        float muffle = 0f;
        boolean kickLatched = false;
        int subBassSource = -1;

        Track(int source, AudioFormat format) {
            this.source = source;
            this.rate = format.getSampleRate();
            this.perSlice = Math.max(1, (int) (rate * KickDetector.SLICE));
        }
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
        Track track = new Track(source, stream.getFormat());
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
            return;
        }
        track.sound = sound;
        tracks.add(track);
        LOGGER.info("MDMA hears {} ({} Hz)", sound.getLocation(), track.rate);
    }

    /** Hands every buffer on unchanged, after noting what is in it. */
    private record Listening(AudioStream delegate, Track track) implements AudioStream {
        @Override
        public AudioFormat getFormat() {
            return delegate.getFormat();
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            ByteBuffer buffer = delegate.read(size);
            // The channel queues every buffer it gets, so every one is counted, even an empty one.
            if (buffer != null && !track.ignored) {
                AudioFormat format = delegate.getFormat();
                int frames = buffer.remaining() / (2 * Math.max(1, format.getChannels()));
                float[][] heard = track.detector.slices(format, buffer.duplicate());
                for (float k : heard[KickDetector.KICK]) if (k > 0.5f) track.kicks++;
                track.chunks.add(new Chunk(frames, heard));
            }
            return buffer;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
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
        float k = 0f, l = 0f, h = 0f;
        boolean heard = false;
        for (Track t : tracks) {
            if (!mc.getSoundManager().isActive(t.sound)) {
                if (t.filter > 0) {
                    try {
                        if (AL10.alIsSource(t.source)) {
                            AL10.alSourcei(t.source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                        }
                        EXTEfx.alDeleteFilters(t.filter);
                    } catch (Throwable ignored) {}
                    t.filter = -1;
                }
                if (t.subBassSource > 0) {
                    try {
                        if (AL10.alIsSource(t.subBassSource)) {
                            AL10.alSourceStop(t.subBassSource);
                            AL10.alDeleteSources(t.subBassSource);
                        }
                    } catch (Throwable ignored) {}
                    t.subBassSource = -1;
                }
                tracks.remove(t);
                LOGGER.info("MDMA heard {}: {} kicks", t.sound.getLocation(), t.kicks);
                continue;
            }
            louder(t);
            applyMuffleFilter(t, player, mc, dt);
            float near = player == null ? 0f : closeness(t.sound, player);
            heard |= near > 0.05f;
            if (!getHeardNow(t, CURRENT_SLICE)) continue;
            float kickNow = CURRENT_SLICE[KickDetector.KICK];
            k = Math.max(k, kickNow * near);
            l = Math.max(l, CURRENT_SLICE[KickDetector.LEVEL] * near);
            h = Math.max(h, CURRENT_SLICE[KickDetector.HATS] * near);

            // Sub-Bass Transmission through restroom stalls and club walls:
            // When muffled behind walls, trigger deep 46 Hz sub-bass punch in sync with the beat
            if (t.muffle > 0.12f && player != null && near > 0.02f) {
                if (kickNow > 0.35f && !t.kickLatched) {
                    t.kickLatched = true;
                    playTrackSubBass(t, player, kickNow, mc);
                } else if (kickNow < 0.20f) {
                    t.kickLatched = false;
                }
            }
        }
        playing = heard;
        song.hear(k, l, h, heard, now, dt);
        // At techno tempos each kick dies away faster, so hits stay apart instead of smearing.
        kick = Math.max(k, kick * (float) Math.exp(-dt * Math.max(9.0, 4.5 / song.period())));
        hats = Math.max(h, hats * (float) Math.exp(-dt * 14.0));
        // Up fast, down slowly: loud bits hit at once, the quiet comes in gently.
        level += (l - level) * (1f - (float) Math.exp(-dt * (l > level ? 25.0 : 4.0)));
    }

    private static int subBassBuffer = -1;

    private static int getOrCreateSubBassBuffer() {
        if (subBassBuffer > 0 && AL10.alIsBuffer(subBassBuffer)) return subBassBuffer;
        try {
            int n = (int) (44100 * 0.32);
            ByteBuffer pcm = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder());
            double phase = 0.0;
            for (int i = 0; i < n; i++) {
                double t = (double) i / 44100.0;
                // Realistic club sub-bass acoustic transmission:
                // Fast transient attack punch sweeping from 72 Hz down into a deep 46 Hz room resonance
                double freq = 46.0 + 26.0 * Math.exp(-t * 50.0);
                phase += 2.0 * Math.PI * freq / 44100.0;
                double sine = Math.sin(phase);
                // Soft saturation gives rich chest-thump warmth on headphones & speakers
                double wave = Math.tanh(sine * 1.4) * 0.82;
                // 4ms smooth attack, exponential sub-bass decay, gentle tail fadeout
                double attack = t < 0.004 ? 0.5 * (1.0 - Math.cos(Math.PI * t / 0.004)) : 1.0;
                double decay = Math.exp(-t * 8.5);
                double tail = t > 0.24 ? Math.max(0.0, 1.0 - (t - 0.24) / 0.08) : 1.0;
                double amp = attack * decay * tail;
                pcm.putShort((short) Math.round(wave * amp * 32000.0));
            }
            pcm.flip();
            int buf = AL10.alGenBuffers();
            AL10.alBufferData(buf, AL10.AL_FORMAT_MONO16, pcm, 44100);
            subBassBuffer = buf;
            return subBassBuffer;
        } catch (Throwable e) {
            LOGGER.warn("Could not generate club sub-bass buffer", e);
            return -1;
        }
    }

    private static void playTrackSubBass(Track t, LocalPlayer player, float kickIntensity, Minecraft mc) {
        if (t.sound == null) return;
        Vec3 soundPos = new Vec3(t.sound.getX(), t.sound.getY(), t.sound.getZ());
        if (soundPos.lengthSqr() <= 1.0) return;

        if (t.subBassSource == -1) {
            try {
                int buf = getOrCreateSubBassBuffer();
                if (buf > 0) {
                    int src = AL10.alGenSources();
                    if (src > 0) {
                        AL10.alSourcei(src, AL10.AL_BUFFER, buf);
                        AL10.alSourcei(src, AL10.AL_LOOPING, AL10.AL_FALSE);
                        AL10.alSourcef(src, AL10.AL_ROLLOFF_FACTOR, 0.65f); // Sub-bass carries through walls and air
                        AL10.alSourcef(src, AL10.AL_REFERENCE_DISTANCE, 6.0f);
                        AL10.alSourcef(src, AL10.AL_MAX_DISTANCE, 38.0f);
                        t.subBassSource = src;
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (t.subBassSource > 0 && AL10.alIsSource(t.subBassSource)) {
            try {
                AL10.alSource3f(t.subBassSource, AL10.AL_POSITION, (float) soundPos.x, (float) soundPos.y, (float) soundPos.z);
                float djPitch = 1.0f;
                if (mc.level != null && mc.level.getBlockEntity(BlockPos.containing(soundPos)) instanceof com.createbrewery.block.club.DjBoothBlockEntity dj) {
                    djPitch = dj.getPitch();
                }
                AL10.alSourcef(t.subBassSource, AL10.AL_PITCH, Mth.clamp(djPitch, 0.5f, 2.0f));

                double dist = Math.sqrt(player.distanceToSqr(soundPos.x, soundPos.y, soundPos.z));
                float falloff = (float) Math.max(0.0, 1.0 - dist / 34.0);
                float gain = t.muffle * (0.6f + 0.4f * kickIntensity) * falloff * 1.4f;
                AL10.alSourcef(t.subBassSource, AL10.AL_GAIN, Math.min(1.0f, gain));
                AL10.alSourcePlay(t.subBassSource);
            } catch (Throwable ignored) {}
        }
    }

    /** Behind closed doors, inside restroom stalls or outside, cut the highs for that authentic muffled club sub-bass sound. */
    private static void applyMuffleFilter(Track t, LocalPlayer player, Minecraft mc, float dt) {
        if (player == null || mc.level == null || t.sound == null) return;
        Vec3 eye = player.getEyePosition();
        Vec3 soundPos = new Vec3(t.sound.getX(), t.sound.getY(), t.sound.getZ());
        if (soundPos.lengthSqr() > 1.0) {
            BlockPos soundBlock = BlockPos.containing(soundPos);

            // Real-time pitch control if played from DJ booth
            if (mc.level.getBlockEntity(soundBlock) instanceof com.createbrewery.block.club.DjBoothBlockEntity dj) {
                float djPitch = dj.getPitch();
                if (djPitch >= 0.5f && djPitch <= 2.0f && AL10.alIsSource(t.source)) {
                    try {
                        AL10.alSourcef(t.source, AL10.AL_PITCH, djPitch);
                    } catch (Throwable ignored) {}
                }
            }

            // Raycast multiple sample points (center, turntable/speaker surface, overhead space)
            // to ensure accurate occlusion: doors and solid walls block all rays, while direct line of sight
            // or peeking around corners handles diffraction gracefully.
            Vec3[] targets = new Vec3[] {
                soundPos,
                soundPos.add(0.0, 0.6, 0.0),
                soundPos.add(0.0, 1.2, 0.0)
            };

            int blocked = 0;
            for (Vec3 target : targets) {
                BlockHitResult hit = mc.level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(soundBlock) && !hit.getBlockPos().equals(soundBlock.above())) {
                    blocked++;
                }
            }

            float targetMuffle = (float) blocked / targets.length;
            // Smoothly glide towards target muffle (approx 0.2s for physical door sweep)
            t.muffle += (targetMuffle - t.muffle) * Math.min(1.0f, dt * 7.0f);
        } else {
            t.muffle = 0f;
        }

        if (t.filter == -1) {
            try {
                int f = EXTEfx.alGenFilters();
                if (f > 0) {
                    EXTEfx.alFilteri(f, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                    t.filter = f;
                } else {
                    t.filter = -2;
                }
            } catch (Throwable e) {
                t.filter = -2;
            }
        }

        if (t.filter > 0 && AL10.alIsSource(t.source)) {
            try {
                if (t.muffle < 0.005f) {
                    // Transparent: direct line of sight in the club
                    EXTEfx.alFilterf(t.filter, EXTEfx.AL_LOWPASS_GAIN, 1.0f);
                    EXTEfx.alFilterf(t.filter, EXTEfx.AL_LOWPASS_GAINHF, 1.0f);
                    AL10.alSourcei(t.source, EXTEfx.AL_DIRECT_FILTER, t.filter);
                } else {
                    // Club Restroom Stall / Outside wall filter:
                    // High and mid frequencies cut into a faint distant murmur (gainHF -> 0.0)
                    // The direct track volume drops by ~18 dB (gainLF -> 0.12)
                    // Vocals, synths, and melodies are muffled into an indistinct background drone,
                    // while the synchronized sub-bass pulse thumps through the walls.
                    float gainHF = Mth.clamp(Mth.lerp(t.muffle, 1.0f, 0.0f), 0.0f, 1.0f);
                    float gainLF = Mth.clamp(Mth.lerp(t.muffle, 1.0f, 0.12f), 0.0f, 1.0f);
                    EXTEfx.alFilterf(t.filter, EXTEfx.AL_LOWPASS_GAIN, gainLF);
                    EXTEfx.alFilterf(t.filter, EXTEfx.AL_LOWPASS_GAINHF, gainHF);
                    AL10.alSourcei(t.source, EXTEfx.AL_DIRECT_FILTER, t.filter);
                }
            } catch (Throwable ignored) {}
        }
    }

    public static float getMusicBassShake(net.minecraft.world.entity.player.Player player) {
        if (player == null || tracks.isEmpty()) return 0f;
        float k = kick;
        if (k < 0.12f) return 0f;
        float total = 0f;
        for (Track t : tracks) {
            if (t.sound == null) continue;
            double distSq = player.distanceToSqr(t.sound.getX(), t.sound.getY(), t.sound.getZ());
            if (distSq < 289.0) { // within 17 blocks
                double dist = Math.sqrt(distSq);
                float falloff = (float) Math.max(0.0, 1.0 - (dist / 17.0));
                float wallFactor = t.muffle > 0.25f ? 1.25f : 0.85f;
                total += k * falloff * falloff * wallFactor * 0.75f;
            }
        }
        return Math.min(1.2f, total);
    }

    /** At the peak the music sounds louder: up to twice the gain the game gave it. */
    private static void louder(Track t) {
        if (!AL10.alIsSource(t.source)) return;
        float now = AL10.alGetSourcef(t.source, AL10.AL_GAIN);
        // Whatever the game set since (volume slider, a moving sound) is the new base.
        if (Math.abs(now - t.louder) > 1e-4f) t.gain = now;
        t.louder = t.gain * (1f + RollClient.peak);
        AL10.alSourcef(t.source, AL10.AL_MAX_GAIN, 2f);
        AL10.alSourcef(t.source, AL10.AL_GAIN, t.louder);
    }

    /** What is audible right now, or false if nothing is known yet. */
    private static boolean getHeardNow(Track t, float[] out) {
        if (!AL10.alIsSource(t.source)) return false;
        // ponytail: between the read and the queueing (microseconds, once a second) this is one buffer ahead.
        int head = t.chunks.size() - AL10.alGetSourcei(t.source, AL10.AL_BUFFERS_QUEUED);
        if (head < 0) return false;
        if (latencyKnown == null) latencyKnown = AL10.alIsExtensionPresent("AL_SOFT_source_latency");
        double frames;
        if (latencyKnown) {
            SOFTSourceLatency.alGetSourcedvSOFT(t.source, SOFTSourceLatency.AL_SEC_OFFSET_LATENCY_SOFT, offsetAndLatency);
            frames = Math.max(0.0, offsetAndLatency[0] - offsetAndLatency[1]) * t.rate;
        } else {
            frames = AL10.alGetSourcei(t.source, AL11.AL_SAMPLE_OFFSET);
        }
        // The offset counts from the first queued buffer, which may already have played out.
        while (head < t.chunks.size() && frames >= t.chunks.get(head).frames()) frames -= t.chunks.get(head++).frames();
        if (head >= t.chunks.size()) return false;
        float[][] heard = t.chunks.get(head).heard();
        int i = (int) (frames / t.perSlice);
        if (i >= heard[0].length) return false;
        out[KickDetector.KICK] = heard[KickDetector.KICK][i];
        out[KickDetector.LEVEL] = heard[KickDetector.LEVEL][i];
        out[KickDetector.HATS] = heard[KickDetector.HATS][i];
        return true;
    }

    /** 1 next to the jukebox (or for music that plays everywhere), 0 out of earshot. */
    private static float closeness(SoundInstance sound, LocalPlayer player) {
        if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE) return 1f;
        float reach = Math.max(sound.getVolume(), 1f) * (sound.getSound() == null ? 16f : sound.getSound().getAttenuationDistance());
        double d = Math.sqrt(player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()));
        return (float) Math.max(0.0, 1.0 - d / reach);
    }
}
