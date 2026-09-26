package com.createbrewery.mixin;

import com.createbrewery.drunk.DrugAudio;
import com.mojang.blaze3d.audio.Library;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.IntBuffer;

/**
 * Asks the OpenAL context for more effect sends, so the drug effects get one of their own.
 * Priority above Sound Physics Remastered's, so this runs after it has asked for its four.
 * Not required: without it there are just fewer sends, which DrugAudio logs.
 */
@Mixin(value = Library.class, priority = 1500)
public abstract class LibraryMixin {
    @ModifyArg(method = "init", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"), index = 1, require = 0)
    private IntBuffer createbrewery$moreSends(IntBuffer attributes) {
        return DrugAudio.moreSends(attributes);
    }
}
