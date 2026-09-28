package com.createbrewery.mixin;

import com.createbrewery.drunk.MusicPulse;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets MusicPulse hear every streamed sound from its very first buffer, and own a song's volume and pitch. */
@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow
    @Final
    private int source;

    @ModifyVariable(method = "attachBufferStream", at = @At("HEAD"), argsOnly = true)
    private AudioStream createbrewery$listen(AudioStream stream) {
        return MusicPulse.listen(stream, (Channel) (Object) this, source);
    }

    // A ticking sound (an Etched record) has its volume and pitch set again every tick: that would
    // unmute the game's source between frames, so bits of the dry song leak past the speakers.
    @Inject(method = "setVolume", at = @At("HEAD"), cancellable = true)
    private void createbrewery$holdVolume(float volume, CallbackInfo ci) {
        if (MusicPulse.holdVolume((Channel) (Object) this, volume)) ci.cancel();
    }

    @Inject(method = "setPitch", at = @At("HEAD"), cancellable = true)
    private void createbrewery$holdPitch(float pitch, CallbackInfo ci) {
        if (MusicPulse.holdPitch((Channel) (Object) this)) ci.cancel();
    }
}
