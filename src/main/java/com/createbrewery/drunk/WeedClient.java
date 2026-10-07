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

    private static final String[] HIGH = {"createbrewery.thought.weed.high.0", "createbrewery.thought.weed.high.1",
        "createbrewery.thought.weed.high.2", "createbrewery.thought.weed.high.3",
        "createbrewery.thought.weed.high.4", "createbrewery.thought.weed.high.5", "createbrewery.thought.weed.high.6",
        "createbrewery.thought.weed.high.7", "createbrewery.thought.weed.high.8",
        "createbrewery.thought.weed.high.9", "createbrewery.thought.weed.high.10"};

    private static void thoughts(LocalPlayer player, float high, RandomSource r) {
        if (player.tickCount < nextThought || DrunkClient.trip > 0.2f) return;
        if (high < 0.3f) {
            nextThought = player.tickCount + 200;
            return;
        }
        // Round and round: now and then the same thought again.
        if (lastThought != null && r.nextFloat() < 0.25f) think(player, Component.translatable("createbrewery.thought.weed.again", Component.translatable(lastThought)), 0x8FCF5A);
        else think(player, Component.translatable(lastThought = HIGH[r.nextInt(HIGH.length)]), 0x8FCF5A);
    }

    private static void think(LocalPlayer player, Component text, int colour) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.ITALIC).withColor(colour), true);
        nextThought = player.tickCount + 700 + player.getRandom().nextInt(700);
    }
}
