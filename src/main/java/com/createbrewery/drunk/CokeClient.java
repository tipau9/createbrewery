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
 *   <li>Restless: no standing still, glancing round; on a lot of it the body twitches by itself.</li>
 *   <li>Paranoia: steps and doors behind you. And as it fades the hand goes for the bag by itself.</li>
 * </ul>
 * Chat turns loud and full of yourself (server side, BreweryCommonEvents).
 */
public final class CokeClient {
    private CokeClient() {}

    /** The high, 0..1; a line just now (burst and watering eyes), 0..1; the craving, 0..1. */
    static float coke, line, craving;
    /** The legs jump by themselves, once (read by the movement input). */
    static boolean hop;
    private static int lastDuration, nextSniff = 200, nextThought = 300, still, nextParanoia = 400, dripAt = -1;
    private static boolean reached;

    static void tick(Minecraft mc, LocalPlayer player) {
        RandomSource r = player.getRandom();
        var high = player.getEffect(ModEffects.COKE_HIGH);
        coke = DrunkClient.ease(coke, DrugEffect.felt(player, ModEffects.COKE_HIGH));

        // A line: the duration jumps back up.
        int duration = high == null ? 0 : high.getDuration();
        if (high != null && duration > lastDuration + 20) {
            line = 1f;
            if (r.nextFloat() < 0.5f) think(player, LINE, 0xF4F4FF);
            dripAt = player.tickCount + 50;
        }
        // A little later the bitter drip runs down the back of the throat: a swallow.
        if (player.tickCount == dripAt) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_DRINK, 0.6f, 0.25f));
            if (r.nextFloat() < 0.5f) think(player, DRIP, 0xF4F4FF);
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

        body(player, high, r);
        paranoia(mc, player, high, fading, r);
        // The hand goes for it by itself: once as it fades, the Koks in the hotbar is suddenly in hand.
        if (fading && !reached) {
            reached = true;
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getItem(i).is(ModItems.KOKS.get()) && player.getInventory().selected != i) {
                    player.getInventory().selected = i;
                    think(player, REACH, 0xF4F4FF);
                    break;
                }
            }
        }
        if (!fading) reached = false;

        thoughts(player, high, fading);
    }

    /**
     * Restless: standing still does not work, the head keeps glancing round. On a lot of it the
     * body twitches by itself - an arm jerks, the legs jump (Psychedelicraft does this too).
     */
    private static void body(LocalPlayer player, net.minecraft.world.effect.MobEffectInstance high, RandomSource r) {
        boolean moving = player.input.forwardImpulse != 0f || player.input.leftImpulse != 0f || player.input.jumping;
        still = moving ? 0 : still + 1;
        if (coke > 0.5f && still > 80 && r.nextFloat() < 0.03f) {
            player.turn((r.nextBoolean() ? 25f : -25f) / 0.15f, 0.0);
            still = 40;
        }
        if (high != null && high.getAmplifier() >= 2 && coke > 0.5f && r.nextFloat() < 1f / 400f) {
            if (r.nextBoolean()) player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            else hop = true;
        }
    }

    /** A lot of it, or as it fades: a step behind you, a door - was someone there? */
    private static void paranoia(Minecraft mc, LocalPlayer player, net.minecraft.world.effect.MobEffectInstance high,
                                 boolean fading, RandomSource r) {
        boolean jumpy = high != null && (high.getAmplifier() >= 1 && coke > 0.4f || fading);
        if (!jumpy || player.tickCount < nextParanoia) return;
        nextParanoia = player.tickCount + 600 + r.nextInt(900);
        if (r.nextFloat() > 0.5f) return;
        var look = player.getLookAngle();
        var sound = r.nextBoolean() ? SoundEvents.STONE_STEP : SoundEvents.WOODEN_DOOR_CLOSE;
        mc.getSoundManager().play(new SimpleSoundInstance(sound, net.minecraft.sounds.SoundSource.BLOCKS, 0.7f,
            0.9f + r.nextFloat() * 0.2f, r, player.getX() - look.x * 6, player.getY(), player.getZ() - look.z * 6));
        think(player, PARANOID, 0xE0E0F0);
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
    private static final String[] DRIP = {"Bitter… läuft hinten den Hals runter.", "Der Drip. Ekelhaft. Geil."};
    private static final String[] REACH = {"Die Hand ist schon am Tütchen…", "Wie ist das in meine Hand gekommen?"};
    private static final String[] PARANOID = {"War da wer?", "Hinter mir. Da war was.", "Wer ist da?! …niemand?",
        "Die gucken alle. Ich merk das."};
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
