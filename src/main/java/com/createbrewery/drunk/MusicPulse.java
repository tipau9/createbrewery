package com.createbrewery.drunk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listens to whatever song is playing - vanilla music, jukebox records, or any mod's (Etched
 * discs and album jukeboxes included) - and finds its kicks, so a rave on MDMA pulses with the
 * real beat.
 *
 * <p>Every streamed MUSIC or RECORDS sound gets its audio stream wrapped as it starts, and each
 * chunk read (about a second of PCM) goes through a {@link KickDetector}. Chunks play back to
 * back, so each one is timed to start where the one before ends. The render thread then looks up what is audible right now, weighted by how
 * close the jukebox is.
 */
public final class MusicPulse {
    private MusicPulse() {}

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final double SLICE = KickDetector.SLICE;
    /** Channel#attachBufferStream already queued this many one-second buffers before we see the stream. */
    private static final int QUEUED_AHEAD = 4;

    /** From {@link KickDetector#slices}: per slice the kick, the level and the hats. */
    private record Chunk(double start, float[][] heard) {
        double end() {
            return start + heard[0].length * SLICE;
        }
    }

    private static final class Track {
        final SoundInstance sound;
        final ConcurrentLinkedDeque<Chunk> chunks = new ConcurrentLinkedDeque<>();
        double nextStart = now() + QUEUED_AHEAD;
        final KickDetector detector = new KickDetector();
        int kicks;
        double seconds;

        Track(SoundInstance sound) {
            this.sound = sound;
        }
    }

    private static final List<Track> tracks = new CopyOnWriteArrayList<>();
    private static Field channelStream;
    private static boolean failed;
    private static double lastFrame;
    private static boolean playing;
    /** What is heard right now, weighted by how close the song is; see {@link #update}. */
    static float kick, level, hats;

    static void init() {
        NeoForge.EVENT_BUS.addListener(MusicPulse::onStream);
    }

    private static double now() {
        return System.nanoTime() / 1e9;
    }

    /** On the sound thread, right after the song's channel started. */
    private static void onStream(PlayStreamingSourceEvent event) {
        SoundInstance sound = event.getSound();
        if (failed || (sound.getSource() != SoundSource.MUSIC && sound.getSource() != SoundSource.RECORDS)) return;
        try {
            if (channelStream == null) {
                channelStream = com.mojang.blaze3d.audio.Channel.class.getDeclaredField("stream");
                channelStream.setAccessible(true);
            }
            AudioStream stream = (AudioStream) channelStream.get(event.getChannel());
            if (stream == null || stream.getFormat().getSampleSizeInBits() != 16) return;
            Track track = new Track(sound);
            channelStream.set(event.getChannel(), new Listening(stream, track));
            tracks.add(track);
            AudioFormat format = stream.getFormat();
            LOGGER.info("MDMA hears {} ({} Hz, {} ch)", sound.getLocation(), format.getSampleRate(), format.getChannels());
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            LOGGER.warn("Cannot listen to music; MDMA falls back to a steady beat", e);
        }
    }

    /** Hands every chunk on unchanged, after noting where its kicks are. */
    private record Listening(AudioStream delegate, Track track) implements AudioStream {
        @Override
        public AudioFormat getFormat() {
            return delegate.getFormat();
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            ByteBuffer buffer = delegate.read(size);
            if (buffer != null && buffer.remaining() > 0) analyse(track, delegate.getFormat(), buffer.duplicate());
            return buffer;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static void analyse(Track t, AudioFormat format, ByteBuffer pcm) {
        int bytes = pcm.remaining();
        float[][] heard = t.detector.slices(format, pcm);
        for (float k : heard[KickDetector.KICK]) if (k > 0.5f) t.kicks++;
        t.seconds += heard[0].length * SLICE;
        // Played back to back; if the stream stalled, it starts as soon as it arrives.
        double start = Math.max(t.nextStart, now());
        t.chunks.addLast(new Chunk(start, heard));
        t.nextStart = start + bytes / (2.0 * Math.max(1, format.getChannels())) / format.getSampleRate();
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
        double now = now();
        float k = 0f, l = 0f, h = 0f;
        boolean heard = false;
        for (Track t : tracks) {
            if (!mc.getSoundManager().isActive(t.sound)) {
                tracks.remove(t);
                LOGGER.info("MDMA heard {}: {} kicks in {} s", t.sound.getLocation(), t.kicks, (int) t.seconds);
                continue;
            }
            float near = player == null ? 0f : closeness(t.sound, player);
            heard |= near > 0.05f;
            Chunk c;
            while ((c = t.chunks.peekFirst()) != null && c.end() < now) t.chunks.pollFirst();
            if (c == null || c.start() > now) continue;
            int i = Math.min(c.heard()[0].length - 1, (int) ((now - c.start()) / SLICE));
            k = Math.max(k, c.heard()[KickDetector.KICK][i] * near);
            l = Math.max(l, c.heard()[KickDetector.LEVEL][i] * near);
            h = Math.max(h, c.heard()[KickDetector.HATS][i] * near);
        }
        playing = heard;
        float dt = (float) Math.min(0.1, now - lastFrame);
        lastFrame = now;
        kick = Math.max(k, kick * (float) Math.exp(-dt * 9.0));
        hats = Math.max(h, hats * (float) Math.exp(-dt * 14.0));
        level += (l - level) * (1f - (float) Math.exp(-dt * 6.0));
    }

    /** 1 next to the jukebox (or for music that plays everywhere), 0 out of earshot. */
    private static float closeness(SoundInstance sound, LocalPlayer player) {
        if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE) return 1f;
        float reach = Math.max(sound.getVolume(), 1f) * (sound.getSound() == null ? 16f : sound.getSound().getAttenuationDistance());
        double d = Math.sqrt(player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()));
        return (float) Math.max(0.0, 1.0 - d / reach);
    }
}
