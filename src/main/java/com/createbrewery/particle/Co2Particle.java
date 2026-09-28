package com.createbrewery.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * One billow of a CO2 cannon's jet. It leaves the nozzle small, dense and very fast, then drag
 * eats its speed within about eight blocks while it swells and churns as it pulls in room air,
 * thinning out. Spent, the cold gas is heavier than air and sinks as it fades. Slides along the
 * ceilings and walls it hits (see {@link FogParticle#move}), so a jet into the ceiling mushrooms
 * out along it. Client only.
 */
public class Co2Particle extends FogParticle {

    private final float startSize, endSize, peakAlpha, spin;

    Co2Particle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
        setSize(0.25f, 0.25f);
        this.lifetime = 28 + random.nextInt(26);
        this.startSize = 0.12f + random.nextFloat() * 0.06f;
        this.endSize = 1.5f + random.nextFloat() * 1.1f;
        this.quadSize = startSize;
        this.peakAlpha = 0.5f + random.nextFloat() * 0.2f;
        // Dense from the first frame: the column is thickest right at the nozzle.
        this.alpha = peakAlpha;
        float white = 0.94f + random.nextFloat() * 0.05f;
        setColor(white, white, Math.min(1f, white + 0.03f));
        this.spin = (random.nextFloat() - 0.5f) * 0.08f;
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
        double speed = Math.sqrt(xd * xd + yd * yd + zd * zd);
        // Turbulence grows as the billow widens and mixes with the air around it.
        float churn = 0.01f + quadSize * 0.012f;
        xd += (random.nextFloat() - 0.5f) * churn;
        yd += (random.nextFloat() - 0.5f) * churn;
        zd += (random.nextFloat() - 0.5f) * churn;
        // Its push spent, the cold gas sinks.
        if (speed < 0.15) yd -= 0.006;
        stir();

        move(xd, yd, zd);
        // Drag: ~1.3 blocks a tick out of the nozzle is spent within about eight blocks.
        xd *= 0.84;
        yd *= 0.84;
        zd *= 0.84;

        oRoll = roll;
        roll += spin;
        float grown = 1f - (float) Math.exp(-age / 9.0);
        quadSize = startSize + (endSize - startSize) * grown;
        float left = 1f - age / (float) lifetime;
        alpha = peakAlpha * (1f - grown * 0.55f) * Math.min(1f, left * 3f);
    }

    /** Drawn where it is: the fog's float above the floor would lift the jet off its nozzle. */
    @Override
    protected float lift() {
        return 0f;
    }

    static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, xd, yd, zd) -> new Co2Particle(level, x, y, z, xd, yd, zd, sprites);
    }
}
