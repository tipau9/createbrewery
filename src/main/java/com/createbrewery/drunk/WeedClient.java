package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;

/**
 * Weed beyond the look and the munchies' taste, client only.
 *
 * <p>Cannabis (PsychonautWiki): couchlock, laughter for little or no reason, thought loops and a
 * short memory, time stretching, heavy red eyes, the munchies.
 *
 * <ul>
 *   <li>Couchlock: stand still for a while and getting going again takes a moment.</li>
 *   <li>Giggles: an animal looking at you is suddenly the funniest thing in the world.</li>
 *   <li>Thought loops: the same thought comes round again - "hab ich das grad schon gedacht?".</li>
 *   <li>The munchies: food in the hotbar glows, warm and golden, while you are hungry.</li>
 *   <li>Heavy eyelids, half shut.</li>
 * </ul>
 */
public final class WeedClient {
    private WeedClient() {}

    /** Stuck to the spot (0..1: how slowly the legs get going); a giggle shaking you; half-shut eyes. */
    static float couch, laugh, lid;
    private static int still, nextLaugh = 200, nextThought = 400;
    private static String lastThought;

    static void tick(Minecraft mc, LocalPlayer player) {
        RandomSource r = player.getRandom();
        float high = DrugEffect.felt(player, ModEffects.WEED_HIGH);
        lid = 0.12f * Math.max(0f, (high - 0.3f) / 0.7f);

        // Couchlock: the longer you have stood still, the harder the first steps.
        boolean moving = player.input.forwardImpulse != 0f || player.input.leftImpulse != 0f || player.input.jumping;
        if (!moving) still++;
        if (high > 0.4f && still > 100) couch = high;
        if (moving) couch = Math.max(0f, couch - 1f / 50f);
        if (moving) still = 0;

        // Giggles: an animal in the crosshair, and it is just too funny.
        laugh *= 0.93f;
        if (high > 0.3f && mc.crosshairPickEntity instanceof Animal animal && player.tickCount >= nextLaugh && r.nextFloat() < 0.02f) {
            nextLaugh = player.tickCount + 400 + r.nextInt(600);
            laugh = 1f;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.GIGGLE.get(), 0.95f + r.nextFloat() * 0.1f, 0.7f));
            think(player, "HAHA " + animal.getName().getString() + "… guck mal wie " + FUNNY[r.nextInt(FUNNY.length)] + " HAHAHA", 0x8FCF5A);
        }

        thoughts(player, high, r);
    }

    /** Couchlock scales the legs: 1 = free, less while getting going. */
    static float legs() {
        return 1f - 0.8f * couch;
    }

    /** From the HUD, after the hotbar: hungry and high, the food there glows. */
    static void drawMunchies(LocalPlayer player, GuiGraphics g) {
        float high = DrugEffect.felt(player, ModEffects.WEED_HIGH);
        if (high < 0.2f || !player.getFoodData().needsFood()) return;
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

    private static final String[] FUNNY = {"der guckt", "die kaut", "das dasteht", "der blinzelt"};
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
