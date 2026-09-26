package com.createbrewery.drunk;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Real coloured light in the world through Veil, which comes along with Sable / Create
 * Aeronautics. Only touched when Veil is loaded (see {@link DrunkClient}), so it stays optional.
 * With an Iris shaderpack Veil's lights are skipped - then this simply shows nothing.
 *
 * <ul>
 *   <li>MDMA: club lights circling you, flashing on the kick, changing colour with each beat.</li>
 *   <li>DMT, on the other side: jewel-coloured lights drifting around you - the beings glow.</li>
 * </ul>
 */
final class VeilLights {
    private VeilLights() {}

    private static final int COUNT = 3;
    @SuppressWarnings("unchecked")
    private static final LightRenderHandle<PointLightData>[] lights = new LightRenderHandle[COUNT];
    private static final int[] CLUB = {0xFF2080, 0x20C0FF, 0x80FF40, 0xFFB020, 0xA040FF};
    private static final int[] JEWEL = {0xE01040, 0x10C060, 0x2050F0, 0x9020D0, 0xFFC040};

    /** Each frame, on the render thread. */
    static void frame(LocalPlayer player, float partial) {
        float club = RollClient.beat * DrunkClient.rolling * DrunkClient.screen();
        float glow = DmtClient.beyond * DrunkClient.screen();
        if (club < 0.02f && glow < 0.02f) {
            clear();
            return;
        }
        boolean dmt = glow > club;
        double t = (player.tickCount + partial) / 20.0;
        Vec3 at = player.getPosition(partial);
        int beats = MusicPulse.song.beats;
        for (int i = 0; i < COUNT; i++) {
            if (lights[i] == null || !lights[i].isValid()) {
                lights[i] = VeilRenderSystem.renderer().getLightRenderer().addLight(new PointLightData());
            }
            PointLightData light = lights[i].getLightData();
            double angle = t * (dmt ? 0.3 : 0.9) + i * Math.PI * 2 / COUNT;
            double radius = dmt ? 2.5 + Math.sin(t * 0.4 + i) : 4.0;
            light.setPosition(at.x + Math.cos(angle) * radius, at.y + (dmt ? 1.2 + 0.6 * Math.sin(t * 0.7 + i * 2) : 2.5), at.z + Math.sin(angle) * radius);
            if (dmt) {
                light.setColor(JEWEL[(i + (int) (t * 0.2)) % JEWEL.length]).setRadius(7f).setBrightness(1.5f * glow);
            } else {
                // The kick flashes every light; each beat the colours move on one.
                light.setColor(CLUB[(beats + i) % CLUB.length]).setRadius(10f)
                    .setBrightness(club * (0.4f + 2.5f * Mth.clamp(MusicPulse.kick, 0f, 1f)));
            }
            lights[i].markDirty();
        }
    }

    static void clear() {
        for (int i = 0; i < COUNT; i++) {
            if (lights[i] != null) {
                lights[i].free();
                lights[i] = null;
            }
        }
    }
}
