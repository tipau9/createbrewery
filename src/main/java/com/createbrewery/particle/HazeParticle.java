package com.createbrewery.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Hazer mist: far finer than the fog machine's. Out of the nozzle (given a speed) it is a thin
 * jet that fans out and slows within a few blocks; standing still (no speed) it is a patch of the
 * haze already hanging in the room - very large, very faint, and as heavy as the air, so it drifts
 * instead of sinking. Many of them together make an even veil. Client only.
 */
public class HazeParticle extends FogParticle {

    private final float startSize, endSize, peakAlpha, spin, drag;
    private final boolean jet;

    HazeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
        setSize(0.3f, 0.3f);
        this.jet = xd * xd + yd * yd + zd * zd > 4e-4;
        if (jet) {
            lifetime = 220 + random.nextInt(120);
            startSize = 0.15f + random.nextFloat() * 0.1f;
            endSize = 2.8f + random.nextFloat() * 1.2f;
            peakAlpha = 0.09f + random.nextFloat() * 0.03f;
            drag = 0.93f;
        } else {
            lifetime = 500 + random.nextInt(300);
            startSize = 2.6f + random.nextFloat() * 1.2f;
            endSize = startSize + 1.2f;
            peakAlpha = 0.014f + random.nextFloat() * 0.008f;
            drag = 0.97f;
        }
        quadSize = startSize;
        alpha = jet ? peakAlpha : 0f;
        float white = 0.88f + random.nextFloat() * 0.06f;
        setColor(white, white, Math.min(1f, white + 0.03f));
        spin = (random.nextFloat() - 0.5f) * 0.004f;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        // As heavy as the air: only the room's slow currents move it.
        xd += (random.nextFloat() - 0.5f) * 0.0012;
        yd += (random.nextFloat() - 0.5f) * 0.0006;
        zd += (random.nextFloat() - 0.5f) * 0.0012;
        // Its push spent, the jet's mist keeps spreading out through the room.
        if (jet && age > 15) spread(0.03, 8);
        stir();
        move(xd, yd, zd);
        xd *= drag;
        yd *= drag;
        zd *= drag;

        oRoll = roll;
        roll += spin;
        float grown = 1f - (float) Math.exp(-age / (jet ? 40.0 : 300.0));
        quadSize = startSize + (endSize - startSize) * grown;
        float in = jet ? 1f : Math.min(1f, age / 60f);
        float out = Math.min(1f, (lifetime - age) / 140f);
        // The jet is densest at the nozzle and thins to the room's haze as it widens.
        float thin = jet ? Mth.lerp(grown, 1f, 0.2f) : 1f;
        alpha = peakAlpha * in * out * thin * nearCamera();
    }

    /** A patch this large right at your eyes would read as a flat sheet: it fades out as you walk into it. */
    private float nearCamera() {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = Math.sqrt(cam.distanceToSqr(x, y, z));
        return (float) Mth.clamp((d - 0.5) / (quadSize * 0.8), 0, 1);
    }

    @Override
    protected float lift() {
        return 0f;
    }

    static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, xd, yd, zd) -> new HazeParticle(level, x, y, z, xd, yd, zd, sprites);
    }
}
