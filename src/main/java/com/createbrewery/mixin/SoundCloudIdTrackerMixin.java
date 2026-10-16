package com.createbrewery.mixin;

import com.createbrewery.compat.etched.SoundCloudAudioResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;

/**
 * Ensures Etched always has a valid SoundCloud API client_id, even when SoundCloud updates its web bundles.
 */
@Pseudo
@Mixin(targets = "gg.moonflower.etched.common.sound.download.SoundCloudIdTracker", remap = false)
public abstract class SoundCloudIdTrackerMixin {

    @Inject(method = "fetch", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void createbrewery$fallbackFetch(Proxy proxy, CallbackInfoReturnable<String> cir) {
        String val = cir.getReturnValue();
        if (val == null || val.isBlank()) {
            cir.setReturnValue(SoundCloudAudioResolver.FALLBACK_CLIENT_ID);
        }
    }
}
