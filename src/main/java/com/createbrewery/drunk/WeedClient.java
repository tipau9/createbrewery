package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Weed client-side effects: subtle eyelid narrowing, munchies hotbar glow, and drifting thoughts.
 */
public final class WeedClient {
    private WeedClient() {}

    /** Eyelid narrowing (subtle black rim at screen borders). */
    static float lid;
    private static int nextThought = 400;

    /** A new player entity (respawn, new world): the tickCount gates start over. */
    static void reset() {
        nextThought = 400;
    }
    private static String lastThought;

    static void tick(Minecraft mc, LocalPlayer player) {
        RandomSource r = player.getRandom();
        float high = DrugEffect.felt(player, ModEffects.WEED_HIGH);
        // Reduced eyelid black bars as requested: very subtle narrowing
        lid = 0.025f * Math.max(0f, (high - 0.4f) / 0.6f);

        thoughts(player, high, r);
    }

    /** From the HUD, after the hotbar: when high, food glows warm and golden (can be eaten even when full). */
    static void drawMunchies(LocalPlayer player, GuiGraphics g) {
        float high = DrugEffect.felt(player, ModEffects.WEED_HIGH);
        if (high < 0.2f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float pulse = 0.5f + 0.5f * (float) Math.sin(player.tickCount * 0.12);
        int a = (int) (high * (40 + 60 * pulse) * DrunkClient.screen());
        for (int i = 0; i < 9; i++) {
            if (!player.getInventory().getItem(i).has(DataComponents.FOOD)) continue;
            int x = w / 2 - 91 + i * 20 + 1, y = h - 22 + 1;
            g.fillGradient(x, y, x + 20, y + 20, (a << 24) | 0xFFD070, (a / 3 << 24) | 0xFF9030);
        }
    }

    // ---- the mind: round and round ----

    private static final String[] HIGH = {"Was, wenn wir alle nur in einem Spiel leben… Moment.", "Warum heißt das eigentlich Block?",
        "Ich hab so Hunger auf was Süßes.", "Wie lange spielen wir schon? Fünf Minuten? Fünf Stunden?",
        "Was wollt ich grad machen?", "Die Musik ist so… tief. So tief.", "Ich steh gleich auf. Gleich.",
        "Wenn man drüber nachdenkt, ist ein Creeper eigentlich nur traurig.", "Hat jemand Chips?",
        "Warte, was hab ich grad gesagt?", "Das ist so ein guter Gedanke. Den muss ich mir merken. …welcher?"};

    private static void thoughts(LocalPlayer player, float high, RandomSource r) {
        if (player.tickCount < nextThought || DrunkClient.trip > 0.2f) return;
        if (high < 0.3f) {
            nextThought = player.tickCount + 200;
            return;
        }
        // Round and round: now and then the same thought again.
        if (lastThought != null && r.nextFloat() < 0.25f) think(player, lastThought + " …warte, das hab ich grad schon gedacht.", 0x8FCF5A);
        else think(player, lastThought = HIGH[r.nextInt(HIGH.length)], 0x8FCF5A);
    }

    private static void think(LocalPlayer player, String text, int colour) {
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.ITALIC).withColor(colour), true);
        nextThought = player.tickCount + 700 + player.getRandom().nextInt(700);
    }
}
