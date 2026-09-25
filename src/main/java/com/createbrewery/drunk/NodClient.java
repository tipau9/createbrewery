package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;

/**
 * Heroin beyond the warm dim look, client only. The shader reads {@link #flood}, {@link #dream},
 * {@link #breath}, {@link #air} and {@link #sick}; the HUD {@link #lid}.
 *
 * <p>Heroin (PsychonautWiki; accounts of users): seconds after the shot the rush - a wave of
 * warmth rolling up from the belly, bliss, the whole body wrapped in it. Then "on the nod": the
 * eyelids sink, the head drops forward, a moment of dream, and it jerks up awake - over and over.
 * Pinpoint pupils (light no longer glares), the world as if through cotton wool, the nose itches,
 * the breath goes slow and shallow. Too much and the breath nearly stops: between breaths it gets
 * cold, blue and dark. First times the stomach turns. Without it later: cold turkey - freezing and
 * sweating, gooseflesh, yawning, restless legs, and only one thought.
 */
public final class NodClient {
    private NodClient() {}

    /** The rush after a shot; a nod's dream behind the closed eyes; each breath (0..1). */
    static float flood, dream, breath;
    /** Heading for no air: blue between the breaths; cold turkey. */
    static float air, sick;
    /** How far the eyes are shut (the HUD draws the lids); a jolt waking from a nod. */
    static float lid, jerk;

    private static int lastDuration, rushTicks = -1, nodTicks = -1, nodLength, nextNod = 400, nextItch = 600, nextThought = 300, itching;
    private static float drooped;
    private static double breathPhase;

    /** Every client tick, from DrunkClient. */
    static void tick(Minecraft mc, LocalPlayer player) {
        RandomSource r = player.getRandom();
        var nod = player.getEffect(ModEffects.NOD);
        float felt = DrugEffect.felt(player, ModEffects.NOD);

        // The rush: every shot (the duration jumps back up) floods you, up in two seconds, gone in fifteen.
        int duration = nod == null ? 0 : nod.getDuration();
        if (nod != null && duration > lastDuration + 20) {
            rushTicks = 0;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.5f, 0.5f));
            think(player, RUSH, 0xE8B060);
        }
        lastDuration = duration;
        if (rushTicks >= 0) {
            rushTicks++;
            flood = (rushTicks < 40 ? rushTicks / 40f : Math.max(0f, 1f - (rushTicks - 40) / 260f)) * Math.max(0.5f, felt);
            if (rushTicks > 300) rushTicks = -1;
        } else {
            flood = 0f;
        }

        // On the nod: the eyes sink, the head drops, a dream - and awake with a jerk. Not while
        // running about or getting hurt, and not during the rush.
        boolean busy = player.isSprinting() || player.hurtTime > 0 || player.swinging || player.isInWater();
        if (nodTicks < 0 && felt > 0.3f && rushTicks < 0 && !busy && player.tickCount >= nextNod) {
            nodTicks = 0;
            nodLength = 120 + r.nextInt(100) + (int) (120 * felt);
            drooped = 0f;
        }
        if (nodTicks >= 0) {
            nodTicks++;
            float sink = Math.min(1f, nodTicks / 60f);
            lid = 0.5f * sink;
            dream = Math.max(0f, Math.min(1f, (nodTicks - 60) / 40f)) * Math.min(1f, (nodLength - nodTicks) / 20f);
            // The head sinks forward, slowly.
            if (drooped < 30f) {
                player.turn(0.0, 0.3 / 0.15);
                drooped += 0.3f;
            }
            if (nodTicks >= nodLength || busy) {
                // Awake: the head snaps up, a jolt, eyes open.
                player.turn(0.0, -drooped * 0.8 / 0.15);
                jerk = 1f;
                nodTicks = -1;
                dream = 0f;
                nextNod = player.tickCount + (int) ((500 + r.nextInt(700)) * (1.3f - felt));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 0.8f, 0.6f));
                if (r.nextFloat() < 0.5f) think(player, WOKE, 0xC8A060);
            }
        } else {
            lid = Math.max(0f, lid - 0.12f);
            lid = Math.max(lid, 0.12f * felt); // heavy eyelids all the time
        }
        jerk *= 0.8f;

        // The itch: a histamine itch, the nose, the arms - scratching.
        if (felt > 0.3f && player.tickCount >= nextItch) {
            nextItch = player.tickCount + 500 + r.nextInt(900);
            itching = 30;
            if (r.nextFloat() < 0.6f) think(player, ITCH, 0xC8A060);
        }
        if (itching > 0 && itching-- % 8 == 0) {
            player.swing(InteractionHand.MAIN_HAND);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1.6f, 0.25f));
        }

        // The breath: slow on heroin, slower and shallower when it starts to fail.
        boolean failing = player.hasEffect(ModEffects.RESPIRATORY_DEPRESSION);
        air = DrunkClient.ease(air, failing ? 1f : 0f);
        // At rest about 15 breaths a minute; on heroin 8; failing, 4.
        breathPhase += Math.PI * 2.0 * Math.max(0.06, 0.25 - 0.12 * felt - 0.07 * air) / 20.0;
        breath = (float) Math.pow(0.5 + 0.5 * Math.sin(breathPhase), 3.0 + 4.0 * air);

        sick = DrunkClient.ease(sick, DrugEffect.strength(player, ModEffects.WITHDRAWAL));
        // Cold turkey: yawning, sneezing, the legs will not keep still.
        if (sick > 0.3f && r.nextFloat() < 1f / 600f) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PANDA_SNEEZE, 1.1f, 0.3f));
        }

        // A low rumbling in the ears, now and then.
        if (felt > 0.5f && r.nextFloat() < 1f / 900f) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_AMBIENT, 0.5f, 0.4f * felt));
        }

        thoughts(player, felt, failing);
    }

    // ---- the mind: slow ----

    private static final String[] RUSH = {"Ohhhhh…", "Warm. So warm.", "Das ist es. Das ist alles.", "Wie nach Hause kommen."};
    private static final String[] NOD = {"Nur kurz die Augen zu…", "Alles egal. Schön egal.", "Wie in Watte.",
        "Ich bin nicht müde. Ich ruh mich nur aus.", "Nichts tut weh. Gar nichts.", "Keine Sorgen. Keine einzige.",
        "Ich könnt hier ewig sitzen.", "Alles ist… gut… so."};
    private static final String[] WOKE = {"Hm? Wo war ich?", "Hab ich geschlafen?", "Ich hab was geträumt… was war das?",
        "Ich war doch grad noch…", "Nicht einschlafen. Nicht einschlafen."};
    private static final String[] ITCH = {"Juckt die Nase…", "Kratzen. Nur kurz kratzen.", "Überall juckt's. Angenehm irgendwie."};
    private static final String[] AIR = {"Atmen… nicht vergessen… zu atmen…", "So kalt…", "So schwer…", "Nur… ein… bisschen… schlafen…"};
    private static final String[] SICK = {"Mir ist kalt. Und heiß. Und kalt.", "Alles tut weh. Die Knochen.",
        "Nur ein bisschen. Dann geht's wieder.", "Meine Beine. Die müssen sich bewegen.", "*gähn* …*gähn*",
        "Gänsehaut. Überall.", "Ich brauch was. Ich brauch was."};

    private static void thoughts(LocalPlayer player, float felt, boolean failing) {
        if (player.tickCount < nextThought || DrunkClient.trip > 0.2f) return;
        if (failing) think(player, AIR, 0x6080C0);
        else if (felt > 0.3f && rushTicks < 0) think(player, NOD, 0xC8A060);
        else if (sick > 0.3f) think(player, SICK, 0x8A9A6A);
        else nextThought = player.tickCount + 200;
    }

    private static void think(LocalPlayer player, String[] pool, int colour) {
        player.displayClientMessage(Component.literal(pool[player.getRandom().nextInt(pool.length)])
            .withStyle(ChatFormatting.ITALIC).withColor(colour), true);
        // Slow thoughts, far apart; the dying breath does not wait.
        nextThought = player.tickCount + (pool == AIR ? 200 : 700 + player.getRandom().nextInt(700));
    }
}
