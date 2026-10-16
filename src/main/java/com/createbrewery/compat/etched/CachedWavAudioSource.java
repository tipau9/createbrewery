package com.createbrewery.compat.etched;

import gg.moonflower.etched.api.sound.source.AudioSource;
import net.minecraft.Util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * An Etched {@link AudioSource} that opens a pre-decoded standard WAV file from disk.
 *
 * <p>Because it returns a stream from an existing {@code .wav} file, Etched reads it with
 * its built-in WAV decoder ({@code WaveDataReader}) and streams raw PCM straight to OpenAL
 * without network latency, buffering hiccups or {@code HttpURLConnection} casting errors.
 */
public class CachedWavAudioSource implements AudioSource {

    private final Path wavPath;

    public CachedWavAudioSource(Path wavPath) {
        this.wavPath = wavPath;
    }

    @Override
    public CompletableFuture<InputStream> openStream() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.newInputStream(wavPath);
            } catch (Exception e) {
                throw new CompletionException("Failed to open cached WAV: " + wavPath, e);
            }
        }, Util.nonCriticalIoPool());
    }

    public Path getWavPath() {
        return wavPath;
    }
}
