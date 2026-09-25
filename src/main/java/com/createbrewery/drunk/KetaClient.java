package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Keta beyond slow and far away, client only. The shader reads Dissoc (scenery slicing, sliding
 * eyes, stretching distances, the K-Loch's white glow and machinescape); the hand is pushed off
 * by {@link #away} (TripClient#onHand).
 *
 * <p>Ketamine (PsychonautWiki): dissociation - the body, the world and the self come apart.
 * Scenery slicing, optical sliding (nystagmus), perspective distortion, a wobbly robotic walk,
 * sounds far off and low, your own body not yours, time stretched. In the K-hole: out of the body
 * altogether, a white glow, glossy synthetic machinescapes, ghosts made of white strings. After
 * it, a lighter mood that can last.
 */
public final class KetaClient {
    private KetaClient() {}

    /** How far your own body has drifted off (0..1), and how deep in the K-Loch. */
    static float away, hole;
    private static CameraType before;
    private static int nextThought = 300;

    static void init() {
        NeoForge.EVENT_BUS.addListener(KetaClient::onCameraDistance);
    }

    static void tick(Minecraft mc, LocalPlayer player) {
        float keta = Math.max(DrugEffect.felt(player, ModEffects.KETA_HIGH), DrugEffect.strength(player, ModEffects.K_HOLE));
        away = DrunkClient.ease(away, keta);
        hole = DrunkClient.ease(hole, DrugEffect.strength(player, ModEffects.K_HOLE));

        // The K-Loch: out of the body. The view drifts off behind and above, far away.
        if (hole > 0.5f && before == null) {
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            think(player, HOLE, 0x8A90D0);
        } else if (hole < 0.2f && before != null) {
            mc.options.setCameraType(before);
            before = null;
            think(player, BACK, 0x8A90D0);
        }

        thoughts(player, keta);
    }

    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (before != null) event.setDistance(event.getDistance() * (1f + 3f * hole));
    }

    /** From the HUD: in the K-Loch, people made of white strings stand about in the glow. */
    static void drawGhosts(LocalPlayer player, GuiGraphics g, float partial) {
        if (hole < 0.3f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = (player.tickCount + partial) / 20f;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        for (int i = 0; i < 3; i++) {
            float flicker = 0.5f + 0.5f * (float) Math.sin(t * (1.3 + i) + i * 2.1);
            int fh = (int) (h * (0.3f + 0.1f * i)), fw = fh / 2;
            int x = (int) (w * (0.2f + 0.3f * i) + Math.sin(t * 0.2 + i) * w * 0.05) - fw / 2;
            g.setColor(0.9f, 0.95f, 1f, (hole - 0.3f) / 0.7f * 0.3f * flicker * DrunkClient.screen());
            g.blit(DrunkClient.SHADOW_FIGURE, x, h / 2 - fh / 2, fw, fh, 0f, 0f, 64, 128, 64, 128);
        }
        g.setColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    // ---- the mind: far off ----

    private static final String[] KETA = {"Ist das meine Hand?", "Wie lange steh ich schon hier? Stunden?",
        "Mein Körper ist irgendwo da unten.", "Alles ist aus Klötzen. Warte— ist es ja.", "Ich bin ein Gedanke, der denkt.",
        "Die Welt ist… ein Bild von der Welt.", "Meine Beine gehen. Ich nicht.", "Alles ist so… weit weg. Und gut so."};
    private static final String[] HOLE = {"Ich bin… weg.", "Wer ist das da unten?", "Da ist ein Licht. Ganz weiß."};
    private static final String[] IN_HOLE = {"Ist das der Tod? Es ist… okay.", "Da sind Leute aus Licht.", "Ich war schon immer hier.",
        "Es gibt kein Oben mehr.", "Ich bin die Maschine."};
    private static final String[] BACK = {"…ach. Ich hab ja einen Körper.", "Wie lange war ich weg? Ein Leben?", "Zurück. Glaub ich."};
    private static final String[] AFTER = {"Irgendwie ist alles leichter jetzt.", "Ich hab keine Sorgen. Komisch.", "Alles halb so schlimm."};

    private static void thoughts(LocalPlayer player, float keta) {
        if (player.tickCount < nextThought || DrunkClient.trip > 0.2f) return;
        if (hole > 0.5f) think(player, IN_HOLE, 0x8A90D0);
        else if (keta > 0.3f) think(player, KETA, 0x8A90D0);
        else if (player.hasEffect(ModEffects.DAZED)) think(player, AFTER, 0xB8C8E0);
        else nextThought = player.tickCount + 200;
    }

    private static void think(LocalPlayer player, String[] pool, int colour) {
        RandomSource r = player.getRandom();
        player.displayClientMessage(Component.literal(pool[r.nextInt(pool.length)]).withStyle(ChatFormatting.ITALIC).withColor(colour), true);
        // Time stretches: thoughts come far apart.
        nextThought = player.tickCount + 800 + r.nextInt(800);
    }
}
