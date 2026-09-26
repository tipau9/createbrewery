package com.createbrewery.drunk;

import com.createbrewery.CreateBrewery;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.quasar.particle.ParticleEmitter;
import foundry.veil.api.quasar.particle.ParticleSystemManager;
import net.minecraft.resources.ResourceLocation;

/**
 * The rest of what Veil can do for the drug vision (see {@link VeilLights} for its lights). Only
 * touched when Veil is loaded (see {@link DrunkClient}), so it stays optional.
 *
 * <ul>
 *   <li>Surface normals: the shader finds the real edges and creases of blocks with them, for the
 *       LSD neon outlines and the lines of the DMT crack.</li>
 *   <li>Quasar particles (assets/createbrewery/veil/quasar/emitters): glowing spores on
 *       mushrooms, a burst of neon sparks as DMT cracks open.</li>
 * </ul>
 */
final class VeilFx {
    private VeilFx() {}

    private static final ResourceLocation BUFFERS = CreateBrewery.ID("drug_vision");
    /** Switching the normal buffer on or off recompiles every shader, so it stays on a while after. */
    private static final int LINGER = 20 * 60;
    private static boolean normals;
    private static int wantedUntil;

    /** Each tick: whether the shader wants the normals right now. */
    static void tick(boolean want, int tickCount) {
        if (want) wantedUntil = tickCount + LINGER;
        boolean on = want || tickCount < wantedUntil;
        if (on == normals) return;
        normals = on;
        if (on) VeilRenderSystem.renderer().enableBuffers(BUFFERS, DynamicBufferType.NORMAL);
        else VeilRenderSystem.renderer().disableBuffers(BUFFERS);
    }

    /** The normal buffer's texture, or -1 while it is off. */
    static int normalTexture() {
        if (!normals) return -1;
        int id = VeilRenderSystem.renderer().getDynamicBufferManger().getBufferTexture(DynamicBufferType.NORMAL);
        return id > 0 ? id : -1;
    }

    /** Starts a Quasar emitter there; false if it could not (then the caller falls back to vanilla). */
    static boolean emit(String name, double x, double y, double z) {
        ParticleSystemManager manager = VeilRenderSystem.renderer().getParticleManager();
        ParticleEmitter emitter = manager.createEmitter(CreateBrewery.ID(name));
        if (emitter == null) return false;
        emitter.setPosition(x, y, z);
        manager.addParticleSystem(emitter);
        return true;
    }
}
