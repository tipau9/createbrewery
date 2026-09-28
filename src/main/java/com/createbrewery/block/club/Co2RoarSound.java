package com.createbrewery.block.club;

import com.createbrewery.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The steady roar of an open CO2 valve: loops while there is pressure and follows it in loudness and pitch. Client only. */
final class Co2RoarSound extends AbstractTickableSoundInstance {
    private final Co2JetBlockEntity jet;

    private Co2RoarSound(Co2JetBlockEntity jet) {
        super(ModSounds.CO2_ROAR.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.jet = jet;
        this.looping = true;
        this.delay = 0;
        this.x = jet.getBlockPos().getX() + 0.5;
        this.y = jet.getBlockPos().getY() + 0.5;
        this.z = jet.getBlockPos().getZ() + 0.5;
        follow();
    }

    /** Keeps {@code playing} (the jet's current roar, or null) going, starting a new one if it has stopped. */
    static Object keep(Object playing, Co2JetBlockEntity jet) {
        if (playing instanceof Co2RoarSound roar && !roar.isStopped()) return roar;
        Co2RoarSound roar = new Co2RoarSound(jet);
        Minecraft.getInstance().getSoundManager().play(roar);
        return roar;
    }

    private void follow() {
        float p = jet.pressure();
        // Loud enough to carry across a club (volume above 1 widens its reach), a touch higher in the surge.
        volume = 1.5f * Math.min(1f, p);
        pitch = 0.9f + 0.12f * p;
    }

    @Override
    public void tick() {
        if (jet.isRemoved() || jet.pressure() <= 0f) {
            stop();
            return;
        }
        follow();
    }
}
