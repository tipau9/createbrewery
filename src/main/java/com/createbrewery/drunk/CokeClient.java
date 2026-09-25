package com.createbrewery.drunk;

import com.createbrewery.ModItems;
import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

/**
 * Koks beyond the sharp look and the heart, client only. The shader reads {@link #coke} and
 * {@link #line}.
 *
 * <p>Cocaine (PsychonautWiki): short and bright - it comes up in minutes and is gone within the
 * hour. Wide pupils, everything crisp and glossy, confidence and ego, a racing mouth, the face
 * and throat going numb with a bitter drip, grinding teeth. Its mark is the redosing: more
 * compulsive than any other stimulant, strongest as it fades. Then the crash - flat, irritable,
 * and still wanting more. Heavy use: "coke bugs", the feeling of insects on the skin.
 *
 * <ul>
 *   <li>A line: a cold white burst, the eyes water, a sniff.</li>
 *   <li>The high: glossy, crisp, glinting like snow; sniffing, the numb face, grinding teeth.</li>
 *   <li>Craving: as it fades and in the crash, the Koks in your hotbar keeps pulling at you.</li>
 * </ul>
 * Chat turns loud and full of yourself (server side, BreweryCommonEvents).
 */
public final class CokeClient {
    private CokeClient() {}

    /** The high, 0..1; a line just now (burst and watering eyes), 0..1; the craving, 0..1. */
    static float coke, line, craving;
    private static int lastDuration, nextSniff = 200, nextThought = 300;

    static void tick(Minecraft mc, LocalPlayer player) {
        RandomSource r = player.getRandom();
        var high = player.getEffect(ModEffects.COKE_HIGH);
        coke = DrunkClient.ease(coke, DrugEffect.felt(player, ModEffects.COKE_HIGH));

        // A line: the duration jumps back up.
        int duration = high == null ? 0 : high.getDuration();
        if (high != null && duration > lastDuration + 20) {
            line = 1f;
            if (r.nextFloat() < 0.5f) think(player, LINE, 0xF4F4FF);
        }
        lastDuration = duration;
        line *= 0.975f;

        // It fades, or it is over: the hunger for the next one.
        boolean fading = high != null && duration < 900;
        craving = DrunkClient.ease(craving, fading ? 0.6f : DrugEffect.strength(player, ModEffects.COKE_CRASH) > 0.1f ? 1f : 0f);

        // Sniffing and grinding teeth.
        if (coke > 0.3f && player.tickCount >= nextSniff) {
            nextSniff = player.tickCount + 300 + r.nextInt(500);
            if (r.nextFloat() < 0.7f) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SNIFF.get(), 1.1f, 0.35f));
            } else {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GRINDSTONE_USE, 1.9f, 0.12f));
            }
        }

        thoughts(player, high, fading);
    }

    /** From the HUD, after the hotbar: the Koks there pulls at you while you crave it. */
    static void drawCraving(LocalPlayer player, GuiGraphics g) {
        if (craving < 0.05f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float pulse = 0.5f + 0.5f * (float) Math.sin(player.tickCount * 0.25);
        int a = (int) (craving * (90 + 140 * pulse) * DrunkClient.screen());
        for (int i = 0; i < 9; i++) {
            if (!player.getInventory().getItem(i).is(ModItems.KOKS.get())) continue;
            int x = w / 2 - 91 + i * 20 + 1, y = h - 22 + 1;
            g.fill(x, y, x + 20, y + 20, (a / 3 << 24) | 0xFFFFFF);
            g.renderOutline(x, y, 20, 20, (a << 24) | 0xFFFFFF);
        }
    }

    // ---- the mind: loud ----

    private static final String[] LINE = {"Sssssnff. Oh ja.", "Kalt. Bitter. Gut.", "Die Augen tränen. Egal.", "Da geht's los."};
    private static final String[] HIGH = {"Ich bin so gut in allem.", "Ich muss dir was erzählen. Und dann noch was.",
        "Alles ist klar. Kristallklar.", "Mein Gesicht ist taub. Meine Zähne auch.", "Bitter hinten im Hals…",
        "Ich hab alles unter Kontrolle.", "Ich hab die beste Idee überhaupt.", "Ich muss mich bewegen. Irgendwas machen.",
        "Warum redet eigentlich keiner mit mir? Ich bin der Interessanteste hier."};
    private static final String[] FADING = {"Noch eine Line. Nur eine.", "Es lässt schon nach? Jetzt schon?",
        "Nur noch eine kleine.", "Wo ist das Tütchen?"};
    private static final String[] CRASH = {"Alles ist scheiße.", "Nur eine, dann geht's wieder.", "Lasst mich in Ruhe.",
        "Warum hab ich so viel geredet?", "Mein Kopf. Mein Kopf.", "Nie wieder. Also… heute nicht mehr."};
    private static final String[] BUGS = {"Da krabbelt was auf dem Arm.", "Käfer? Unter der Haut? Nein. Doch?"};

    private static void thoughts(LocalPlayer player, net.minecraft.world.effect.MobEffectInstance high, boolean fading) {
        if (player.tickCount < nextThought || DrunkClient.trip > 0.2f) return;
        boolean crash = DrugEffect.strength(player, ModEffects.COKE_CRASH) > 0.3f;
        if (high != null && high.getAmplifier() >= 2 && player.getRandom().nextFloat() < 0.2f) think(player, BUGS, 0xF4F4FF);
        else if (fading) think(player, FADING, 0xF4F4FF);
        else if (high != null && coke > 0.3f) think(player, HIGH, 0xF4F4FF);
        else if (crash) think(player, CRASH, 0x8A8A9A);
        else nextThought = player.tickCount + 200;
    }

    private static void think(LocalPlayer player, String[] pool, int colour) {
        player.displayClientMessage(Component.literal(pool[player.getRandom().nextInt(pool.length)])
            .withStyle(ChatFormatting.ITALIC).withColor(colour), true);
        // A racing mouth: on the high the next thought is never far.
        nextThought = player.tickCount + (pool == HIGH ? 240 + player.getRandom().nextInt(300) : 600 + player.getRandom().nextInt(600));
    }
}
