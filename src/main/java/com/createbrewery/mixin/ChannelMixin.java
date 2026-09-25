package com.createbrewery.mixin;

import com.createbrewery.drunk.MusicPulse;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets MusicPulse hear every streamed sound from its very first buffer. */
@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow
    @Final
    private int source;

    @ModifyVariable(method = "attachBufferStream", at = @At("HEAD"), argsOnly = true)
    private AudioStream createbrewery$listen(AudioStream stream) {
        return MusicPulse.listen(stream, (Channel) (Object) this, source);
    }
}
