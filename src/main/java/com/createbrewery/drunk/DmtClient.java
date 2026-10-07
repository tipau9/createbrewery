package com.createbrewery.drunk;

import com.createbrewery.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

/**
 * The DMT breakthrough as the phases people report, client only. The shader reads the four channels.
 *
 * <p>DMT (PsychonautWiki; Lawrence et al. 2022): within seconds a loud, high carrier tone, a
 * crackling, the room vibrating; geometry growing over every surface, neon on black. Then a
 * chrysanthemum unfolding, a tunnel or a waiting room. Then the other side: a palace or hall of
 * light in jewel colours, and beings - mostly kind, often guides or teachers. Then drifting back
 * down through fading, pale geometry, and an afterglow that lasts.
 */
public final class DmtClient {
    private DmtClient() {}

    /** The four phases, each 0..1, eased and overlapping a little. */
    static float crack, waiting, beyond, descent;
    private static int start, lastDuration, said, nextTone;

    /** A new player entity (respawn, new world): the tickCount gates start over. */
    static void reset() {
        nextTone = 0;
    }

    static void tick(Minecraft mc, LocalPlayer player) {
        RandomSource r = player.getRandom();
        var effect = player.getEffect(ModEffects.BREAKTHROUGH);
        int duration = effect == null ? 0 : effect.getDuration();
        // A new dose starts the journey over, however long this one is.
        if (duration > lastDuration + 20) {
            // A real hit (or /brewery test jumping into it) runs on the DMT clock; the short
            // Lachgas spike (Mixes) has its own.
            start = duration > 300 ? com.createbrewery.drugs.Psychedelics.DMT_TICKS : duration;
            said = 0;
        }
        lastDuration = duration;
        float p = effect == null ? 1f : 1f - (float) duration / Math.max(1, start);

        crack = DrunkClient.ease(crack, effect != null && p < 0.12f ? 1f : 0f);
        waiting = DrunkClient.ease(waiting, effect != null && p > 0.1f && p < 0.32f ? 1f : 0f);
        beyond = DrunkClient.ease(beyond, effect != null && p > 0.3f && p < 0.78f ? 1f : 0f);
        // Coming down lingers on for a while after the effect is over.
        if (effect != null && p > 0.75f) descent = DrunkClient.ease(descent, 1f);
        else descent = Math.max(0f, descent - 1f / 400f);

        // The carrier wave: a high, loud tone and a crackle, as it cracks open.
        if (crack > 0.1f && player.tickCount >= nextTone) {
            nextTone = player.tickCount + 18;
            tone(mc, SoundEvents.BEACON_AMBIENT, 2f, crack);
            if (r.nextFloat() < 0.6f) tone(mc, SoundEvents.FIRE_AMBIENT, 1.6f + r.nextFloat() * 0.4f, 0.5f * crack);
        }
        // The waiting room: a hum rising as the flower unfolds.
        if (waiting > 0.1f && player.tickCount >= nextTone) {
            nextTone = player.tickCount + 30;
            tone(mc, SoundEvents.BEACON_AMBIENT, 0.6f + Math.min(1.4f, (p - 0.1f) * 6f), waiting);
        }
        // The other side: a deep chord, and a glassy chime now and then.
        if (beyond > 0.1f && player.tickCount >= nextTone) {
            nextTone = player.tickCount + 50 + r.nextInt(40);
            tone(mc, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f + r.nextInt(3) * 0.25f, beyond);
        }

        if (crack > 0.5f && say(player, 1, CRACK, 0x7CFF9A)) {
            // It cracks open: neon sparks burst out all around.
            if (DrunkClient.quasar()) DrunkClient.veilParticles("dmt_sparks", player.getX(), player.getEyeY(), player.getZ());
        }
        if (waiting > 0.5f) say(player, 2, WAITING, 0xB8A0FF);
        if (beyond > 0.5f && say(player, 4, ARRIVE, 0xFFD27C)) tone(mc, SoundEvents.BEACON_POWER_SELECT, 0.5f, 1f);
        if (beyond > 0.5f && p > 0.55f) say(player, 8, BEINGS, 0xFFD27C);
        if (descent > 0.5f) say(player, 16, DESCENT, 0xC8DCFF);
    }

    private static void tone(Minecraft mc, SoundEvent sound, float pitch, float volume) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static final String[] CRACK = {"createbrewery.thought.dmt.crack.0", "createbrewery.thought.dmt.crack.1",
        "createbrewery.thought.dmt.crack.2"};
    private static final String[] WAITING = {"createbrewery.thought.dmt.waiting.0", "createbrewery.thought.dmt.waiting.1",
        "createbrewery.thought.dmt.waiting.2"};
    private static final String[] ARRIVE = {"createbrewery.thought.dmt.arrive.0", "createbrewery.thought.dmt.arrive.1",
        "createbrewery.thought.dmt.arrive.2"};
    private static final String[] BEINGS = {"createbrewery.thought.dmt.beings.0", "createbrewery.thought.dmt.beings.1",
        "createbrewery.thought.dmt.beings.2", "createbrewery.thought.dmt.beings.3"};
    private static final String[] DESCENT = {"createbrewery.thought.dmt.descent.0", "createbrewery.thought.dmt.descent.1",
        "createbrewery.thought.dmt.descent.2"};

    /** Each line once per journey, marked in {@link #said} by bit. */
    private static boolean say(LocalPlayer player, int bit, String[] pool, int colour) {
        if ((said & bit) != 0) return false;
        said |= bit;
        player.displayClientMessage(Component.translatable(pool[player.getRandom().nextInt(pool.length)])
            .withStyle(ChatFormatting.ITALIC).withColor(colour), true);
        return true;
    }
}
