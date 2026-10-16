package com.createbrewery.mixin;

import com.createbrewery.drunk.HearingClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Sound Physics Remastered owns every sound's direct filter, so ringing ears dull its highs there:
 * the last float of {@code setEnvironment} but one is what it sets as the filter's GAINHF.
 * Not required: without the mod (or after it changes) the hook is just not there.
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsMixin {
    @ModifyVariable(method = "setEnvironment(IFFFFFFFFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 8, require = 0, remap = false)
    private static float createbrewery$tinnitus(float directGainHf) {
        return directGainHf * HearingClient.gainHf();
    }
}
