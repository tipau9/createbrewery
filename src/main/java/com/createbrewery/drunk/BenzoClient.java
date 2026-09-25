package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Xanax beyond "no fear", client only. The shader reads {@link #calm} and {@link #rebound}.
 *
 * <p>Alprazolam (PsychonautWiki): sedation, heavy body, anxiety and emotion suppression, blurred
 * vision, slowed thinking and speech - and amnesia: at higher doses whole stretches are simply
 * gone, while you went on doing things. You feel sober the whole time ("delusions of sobriety"),
 * which is why people take more. After it, rebound anxiety.
 *
 * <ul>
 *   <li>The look: flat, soft and grey-lilac, everything a little out of focus and unimportant.</li>
 *   <li>Slow, heavy blinks.</li>
 *   <li>Memory gaps: a hard cut to nothing, and seconds later you are back - somewhere a little
 *       different, maybe holding something else. And you open a chest and forget what for.</li>
 *   <li>Monsters sound quieter: they just do not matter much.</li>
 *   <li>Afterwards the fear comes back doubled: jumpy, a racing pulse, everything too sharp.</li>
 * </ul>
 * Speech trails off in chat (server side, BreweryCommonEvents).
 */
public final class BenzoClient {
    private BenzoClient() {}

    static float calm, rebound, lid;
    private static int gapTicks, nextGap = 1200, nextThought = 400, reboundTicks, forgetTicks = -1, lastLevel;
    private static boolean had;

    static void tick(Minecraft mc, LocalPlayer player) {
        RandomSource r = player.getRandom();
        var instance = player.getEffect(ModEffects.CALM);
        float felt = DrugEffect.felt(player, ModEffects.CALM);
        calm = DrunkClient.ease(calm, felt);

        // Rebound anxiety once it has worn off (not when it ends by dying).
        if (instance != null) lastLevel = instance.getAmplifier();
        if (had && instance == null && player.isAlive()) reboundTicks = 2400 + 1200 * lastLevel;
        had = instance != null;
        if (reboundTicks > 0) reboundTicks--;
        rebound = DrunkClient.ease(rebound, reboundTicks > 0 ? Math.min(1f, reboundTicks / 600f) : 0f);

        // Slow, heavy blinks, every few seconds.
        int cycle = player.tickCount % 140;
        float blink = cycle < 30 ? (float) Math.sin(Math.PI * cycle / 30.0) : 0f;
        lid = calm * (0.08f + 0.35f * blink);

        // Memory gaps: from a proper dose on, now and then a stretch is just gone.
        if (gapTicks > 0 && --gapTicks == 0) {
            think(player, GAP);
            // What were you doing in there? Something else is in your hand now.
            if (r.nextBoolean()) player.getInventory().selected = r.nextInt(9);
        }
        if (gapTicks == 0 && felt > 0.45f && player.tickCount >= nextGap) {
            gapTicks = 30 + r.nextInt(50) + (int) (40 * felt);
            nextGap = player.tickCount + 900 + r.nextInt(1500);
        }

        // Open a chest, and forget what for.
        if (mc.screen instanceof AbstractContainerScreen<?> && felt > 0.4f) {
            if (forgetTicks < 0) forgetTicks = r.nextFloat() < 0.2f ? 50 + r.nextInt(60) : 0;
            if (forgetTicks > 0 && --forgetTicks == 0) {
                player.closeContainer();
                think(player, FORGOT);
            }
        } else {
            forgetTicks = -1;
        }

        thoughts(player, felt);
    }

    /** From the HUD, drawn last: the gap - nothing at all, no fade. */
    static void drawGap(GuiGraphics g) {
        if (gapTicks > 0) g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFF000000);
    }

    // ---- the mind: calm, then not ----

    private static final String[] CALM = {"Alles halb so wild.", "Mir ist alles egal. Angenehm egal.", "Ich merk gar nichts. Wirkt das überhaupt?",
        "Ich bin total klar im Kopf. Total.", "Vielleicht noch eine. Merk ja eh nix.", "Keine Sorgen. Nicht eine.",
        "Ich könnt jetzt überall schlafen.", "Was hab ich… ach, egal."};
    private static final String[] GAP = {"…Moment. Wie bin ich hierher gekommen?", "Was ist grad passiert?", "Hab ich was gemacht?",
        "Wie spät ist es?", "…wo war ich?"};
    private static final String[] FORGOT = {"Was wollte ich hier nochmal?", "Warum hab ich die aufgemacht?", "Hm. Vergessen."};
    private static final String[] REBOUND = {"Irgendwas stimmt nicht.", "Mein Herz rast.", "Was hab ich gestern gemacht? Ich weiß es nicht mehr.",
        "Warum hab ich solche Angst?", "Alles ist zu laut.", "Nur eine. Nur um runterzukommen."};

    private static void thoughts(LocalPlayer player, float felt) {
        if (player.tickCount < nextThought || DrunkClient.trip > 0.2f || gapTicks > 0) return;
        if (felt > 0.3f) think(player, CALM);
        else if (rebound > 0.3f) think(player, REBOUND);
        else nextThought = player.tickCount + 200;
    }

    private static void think(LocalPlayer player, String[] pool) {
        player.displayClientMessage(Component.literal(pool[player.getRandom().nextInt(pool.length)]).withStyle(ChatFormatting.ITALIC)
            .withColor(pool == REBOUND ? 0xD06070 : 0xB8C8E0), true);
        nextThought = player.tickCount + 700 + player.getRandom().nextInt(800);
    }
}
