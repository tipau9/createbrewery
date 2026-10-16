package com.createbrewery.mixin;

import com.createbrewery.compat.etched.SoundCloudAudioResolver;
import gg.moonflower.etched.api.util.DownloadProgressListener;
import gg.moonflower.etched.api.sound.source.AudioSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.util.concurrent.CompletableFuture;

/**
 * Intercepts SoundCloud audio downloads to support modern AAC HLS streams and persistent WAV caching.
 *
 * <p>SoundCloud retired its legacy MP3 HLS stream endpoints, causing unpatched Etched to fail with
 * HTTP 404. This mixin routes SoundCloud URLs to {@link SoundCloudAudioResolver}, which decodes the
 * modern AAC HLS segments into local standard WAV files that OpenAL plays natively.
 */
@Pseudo
@Mixin(targets = "gg.moonflower.etched.api.sound.download.SoundSourceManager", remap = false)
public abstract class SoundSourceManagerMixin {

    @Inject(method = "getAudioSource", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void createbrewery$getAudioSource(
            String url,
            DownloadProgressListener listener,
            Proxy proxy,
            AudioSource.AudioFileType type,
            CallbackInfoReturnable<CompletableFuture<AudioSource>> cir) {

        if (SoundCloudAudioResolver.isSoundCloudUrl(url)) {
            cir.setReturnValue(SoundCloudAudioResolver.getAudioSource(url, listener, proxy, type));
        }
    }
}
