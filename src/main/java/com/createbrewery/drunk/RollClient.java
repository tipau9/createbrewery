package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.drugs.Psychedelics;
import com.createbrewery.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;

/**
 * MDMA beyond the pink glow, client only. Reads {@link DrunkClient#rolling}; the shader reads
 * {@link #rush}, {@link #beat} and {@link #heat}.
 *
 * <ul>
 *   <li>Rushes: every half minute or so a wave of euphoria washes over you - light, goosebumps, a sigh.</li>
 *   <li>Music gets into the body: while a song plays, the picture pumps with the beat and rave lasers sweep.</li>
 *   <li>Soft things underfoot (wool, carpet, moss) feel wonderful.</li>
 *   <li>Everyone is lovely: chat comes with a heart, and monsters are just "Kumpel".</li>
 *   <li>Dancing on it heats you up: the view flushes and throbs. Water and rest cool it.</li>
 *   <li>Thoughts, and the empty days after (Tiefpunkt).</li>
 * </ul>
 */
public final class RollClient {
    private RollClient() {}

    static float rush, beat, heat;
    /** The last song that started (music or a record), to know whether one is still playing. */
    static SoundInstance music;

    private static int rushTicks = -1, nextRush = 400, nextThought = 300;
    private static float sweat, lastWalk;

    static void init() {
        NeoForge.EVENT_BUS.addListener(RollClient::onChat);
        NeoForge.EVENT_BUS.addListener(RollClient::onNameTag);
    }

    /** Every client tick, from DrunkClient once its channels are eased. */
    static void tick(Minecraft mc, LocalPlayer player) {
        float roll = DrunkClient.rolling;
        RandomSource r = player.getRandom();

        // Rushes: up in a second, fading over four.
        if (rushTicks < 0 && roll > 0.3f && player.tickCount >= nextRush) {
            rushTicks = 0;
            nextRush = player.tickCount + 600 + r.nextInt(800);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f * roll));
            if (r.nextBoolean()) think(player, RUSH);
        }
        if (rushTicks >= 0) {
            rushTicks++;
            rush = (rushTicks < 20 ? rushTicks / 20f : Math.max(0f, 1f - (rushTicks - 20) / 80f)) * Math.min(1f, roll * 1.5f);
            if (rushTicks > 100) {
                rushTicks = -1;
                rush = 0f;
            }
        }

        // The music, in the body: strongest while a song plays, and more still when dancing.
        boolean playing = music != null && mc.getSoundManager().isActive(music);
        beat = DrunkClient.ease(beat, Math.min(1f, roll * (playing ? 1f : 0.25f) * (player.isSprinting() ? 1.3f : 1f)));

        // Dancing heats you up; water and rest cool you down. The real heat is on the server
        // (Stimulants#body), which only tells us once it is too late (Hitzschlag), so this guesses ahead.
        boolean dancing = roll > 0.1f && player.isSprinting();
        sweat = dancing ? Math.min(1f, sweat + 1f / 600f) : Math.max(0f, sweat - (player.isInWaterOrRain() ? 1f / 40f : 1f / 400f));
        heat = DrunkClient.ease(heat, Math.max(DrugEffect.strength(player, ModEffects.HYPERTHERMIA), 0.5f * sweat));

        // Soft things underfoot feel wonderful.
        if (roll > 0.2f && mc.level != null && player.onGround() && (int) player.walkDist != (int) lastWalk
            && (soft(mc.level.getBlockState(player.blockPosition())) || soft(mc.level.getBlockState(player.getOnPos())))) {
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.25f * roll, 1.4f + r.nextFloat() * 0.4f, r, player.getX(), player.getY(), player.getZ()));
            if (r.nextFloat() < 0.05f) think(player, SOFT);
        }
        lastWalk = player.walkDist;

        thoughts(player, roll);
    }

    private static boolean soft(BlockState state) {
        return state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSS_CARPET);
    }

    // ---- the mind ----

    private static final String[] COMING_UP = {"Hm. Ist mir schlecht?", "Mein Bauch kribbelt so komisch.", "Oh… oh. OH.",
        "Wirkt das schon? Ich glaub… ja."};
    private static final String[] ROLLING = {"Ich liebe euch alle!", "Die Musik… ich BIN die Musik.", "Mein Kiefer macht, was er will.",
        "Warum hab ich nie gesagt, wie toll du bist?", "Alles ist gut. Wirklich alles.", "Fühlt sich das gut an…",
        "Ich will jeden umarmen.", "Hat jemand Kaugummi?"};
    private static final String[] RUSH = {"Wow… WOW.", "Da ist sie wieder, die Welle.", "Gänsehaut. Überall."};
    private static final String[] SOFT = {"Ist das weich…", "Ich könnte den ganzen Tag über Wolle laufen.", "Dieser Teppich. DIESER TEPPICH."};
    private static final String[] HOT = {"Wasser… brauch Wasser.", "Ist das heiß hier drin.", "Kurz Pause machen. Nur kurz."};
    private static final String[] LOW = {"Warum bin ich so traurig?", "Nichts macht mehr Spaß.", "Nie wieder. …bis Samstag.",
        "Alles ist so grau."};

    private static void thoughts(LocalPlayer player, float roll) {
        if (player.tickCount < nextThought) return;
        // Tripping, the trip does the thinking (see TripClient) - except when overheating.
        String[] pool = sweat > 0.6f || heat > 0.5f ? HOT
            : DrunkClient.trip > 0.2f ? null
            : roll > 0.05f && Psychedelics.comingUp(player, ModEffects.ROLLING) ? COMING_UP
            : roll > 0.3f ? ROLLING
            : DrugEffect.strength(player, ModEffects.COMEDOWN) > 0.3f ? LOW : null;
        if (pool == null) {
            nextThought = player.tickCount + 200;
            return;
        }
        think(player, pool);
    }

    private static void think(LocalPlayer player, String[] pool) {
        player.displayClientMessage(Component.literal(pool[player.getRandom().nextInt(pool.length)]).withStyle(ChatFormatting.ITALIC)
            .withColor(pool == LOW ? 0x8A8A9A : pool == HOT ? 0xFF6040 : 0xFF7EB6), true);
        nextThought = player.tickCount + 500 + player.getRandom().nextInt(500);
    }

    // ---- everyone is lovely ----

    private static void onChat(ClientChatReceivedEvent event) {
        // Tripping hard, the letters are already scrambled (TripClient).
        if (DrunkClient.rolling < 0.3f || DrunkClient.trip >= 0.3f) return;
        event.setMessage(Component.literal("♥ ").withColor(0xFF7EB6).append(event.getMessage()));
    }

    private static void onNameTag(RenderNameTagEvent event) {
        if (DrunkClient.rolling < 0.4f || !(event.getEntity() instanceof Enemy)) return;
        event.setContent(Component.literal("Kumpel ♥").withColor(0xFF7EB6));
        event.setCanRender(TriState.TRUE);
    }
}
