package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Lachgas beyond the wah-wah, client only. The shader reads {@link #gone} (and Wah itself).
 *
 * <p>Nitrous (PsychonautWiki): kicks in within seconds, peaks for half a minute. Tingles over the
 * head and face, vision that pauses and stutters (frame rate suppression), sounds that stutter,
 * echo and phase, and at a big hit a static wall of interlocking circles - then the self
 * dissolves altogether and comes back seconds later (ego death, amnesia). The famous part: a
 * revelation, the meaning of everything - gone the moment it ends. Laughter, and now and then
 * déjà vu.
 */
public final class GasClient {
    private GasClient() {}

    /** The self dissolving into white on the third balloon, 0..1. */
    static float gone;
    private static float top;
    private static int lastLevel = -1, forgetIn = -1;
    private static boolean wasGone;

    static void tick(LocalPlayer player) {
        RandomSource r = player.getRandom();
        var wah = player.getEffect(ModEffects.WAH);
        float strength = DrugEffect.strength(player, ModEffects.WAH);

        // Each new balloon: sometimes, déjà vu.
        int level = wah == null ? -1 : wah.getAmplifier();
        if (level > lastLevel && level >= 0 && r.nextFloat() < 0.2f) think(player, DEJA_VU, 0xC8DCFF);
        lastLevel = level;

        // Ego death: the third balloon in a row, at its height.
        gone += ((level >= 2 && strength > 0.7f ? 1f : 0f) - gone) * 0.15f;
        if (gone > 0.8f) wasGone = true;
        if (wasGone && gone < 0.1f) {
            wasGone = false;
            think(player, BACK, 0xC8DCFF);
        }

        // The revelation: at the height it all makes sense... and as it fades, it is gone.
        top = Math.max(top, strength);
        if (top > 0.8f && strength < 0.5f && forgetIn < 0 && !wasGone) {
            think(player, REVELATION, 0xFFF0A0);
            forgetIn = 50;
        }
        if (forgetIn > 0 && --forgetIn == 0) {
            think(player, FORGOTTEN, 0xC8DCFF);
            forgetIn = -1;
            top = 0f;
        }
        if (wah == null && forgetIn < 0) top = 0f;

        if (strength > 0.4f && r.nextFloat() < 1f / 120f) think(player, GIGGLE, 0xC8DCFF);
    }

    private static final String[] DEJA_VU = {"Moment… das hab ich doch schon mal erlebt.", "Genau so war's schon mal. Genau so.",
        "Ich weiß, was jetzt kommt…"};
    private static final String[] REVELATION = {"ICH HAB'S! Alles ergibt Sinn! Es ist—", "Jetzt versteh ich's. Das Universum ist—",
        "Oh. OH. Die Antwort auf alles ist—"};
    private static final String[] FORGOTTEN = {"…weg. Was war's?", "…es war… irgendwas mit… nein. Weg.", "Verdammt. Es war SO klar."};
    private static final String[] BACK = {"Wer… wo… ach so. Ich. Ich bin das.", "War ich weg? Wie lange?", "Ich war… überall. Und nirgends."};
    private static final String[] GIGGLE = {"HAHA warum ist das so lustig", "Hihihi… wah… wah… hihi", "Meine Stimme klingt so KOMISCH"};

    private static void think(LocalPlayer player, String[] pool, int colour) {
        player.displayClientMessage(Component.literal(pool[player.getRandom().nextInt(pool.length)])
            .withStyle(ChatFormatting.ITALIC).withColor(colour), true);
    }
}
