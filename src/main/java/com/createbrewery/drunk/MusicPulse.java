package com.createbrewery.drunk;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.SOFTSourceLatency;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
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

    /** The slice of a chunk that is audible right now. */
    private record Now(float[][] heard, int slice) {
        float get(int what) {
            return heard[what][slice];
        }
    }

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
    static boolean playing() {
        return playing;
    }

    /**
     * Once a frame: what is heard right now. Kicks and hats flash up and die away quickly, the
     * level follows smoothly. All 0 without music - no music, no beat.
     */
    static void update() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        double now = System.nanoTime() / 1e9;
        float k = 0f, l = 0f, h = 0f;
        boolean heard = false;
        for (Track t : tracks) {
            if (!mc.getSoundManager().isActive(t.sound)) {
                tracks.remove(t);
                LOGGER.info("MDMA heard {}: {} kicks", t.sound.getLocation(), t.kicks);
                continue;
            }
            float near = player == null ? 0f : closeness(t.sound, player);
            heard |= near > 0.05f;
            Now at = heardNow(t);
            if (at == null) continue;
            k = Math.max(k, at.get(KickDetector.KICK) * near);
            l = Math.max(l, at.get(KickDetector.LEVEL) * near);
            h = Math.max(h, at.get(KickDetector.HATS) * near);
        }
        playing = heard;
        float dt = (float) Math.min(0.1, now - lastFrame);
        lastFrame = now;
        song.hear(k, l, h, heard, now, dt);
        // At techno tempos each kick dies away faster, so hits stay apart instead of smearing.
        kick = Math.max(k, kick * (float) Math.exp(-dt * Math.max(9.0, 4.5 / song.period())));
        hats = Math.max(h, hats * (float) Math.exp(-dt * 14.0));
        // Up fast, down slowly: loud bits hit at once, the quiet comes in gently.
        level += (l - level) * (1f - (float) Math.exp(-dt * (l > level ? 25.0 : 4.0)));
    }

    /** What is audible right now, or null if nothing is known yet. */
    private static Now heardNow(Track t) {
        if (!AL10.alIsSource(t.source)) return null;
        // ponytail: between the read and the queueing (microseconds, once a second) this is one buffer ahead.
        int head = t.chunks.size() - AL10.alGetSourcei(t.source, AL10.AL_BUFFERS_QUEUED);
        if (head < 0) return null;
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
        if (head >= t.chunks.size()) return null;
        float[][] heard = t.chunks.get(head).heard();
        int i = (int) (frames / t.perSlice);
        if (i >= heard[0].length) return null;
        return new Now(heard, i);
    }

    /** 1 next to the jukebox (or for music that plays everywhere), 0 out of earshot. */
    private static float closeness(SoundInstance sound, LocalPlayer player) {
        if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE) return 1f;
        float reach = Math.max(sound.getVolume(), 1f) * (sound.getSound() == null ? 16f : sound.getSound().getAttenuationDistance());
        double d = Math.sqrt(player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()));
        return (float) Math.max(0.0, 1.0 - d / reach);
    }
}
