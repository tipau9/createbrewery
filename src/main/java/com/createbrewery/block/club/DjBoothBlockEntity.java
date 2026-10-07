package com.createbrewery.block.club;

import com.createbrewery.compat.EtchedCompat;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Two decks that play at the same time, mixed on a crossfader.
 *
 * <p>Vanilla and Etched both keep one playing song per block position, so deck A plays from the
 * booth and deck B from the block above it. The server only starts and stops the songs; the
 * crossfader gain and the pitch are applied on each client to the song's OpenAL source (see
 * MusicPulse), so every listener hears the same mix without a packet per volume change.
 */
public class DjBoothBlockEntity extends BlockEntity {

    public static final int A = 0, B = 1, C = 2, D = 3;
    public static final int DECKS = 4;
    /** A turntable's pitch fader: +-8 %. */
    public static final float PITCH_RANGE = 0.08f;
    /** Auto-mix starts the cued deck this long before the playing song ends and crossfades over it. */
    static final int MIX_TICKS = 200;

    // Performance Pad Modes
    public static final int PAD_HOT_CUE = 0, PAD_BEAT_LOOP = 1, PAD_SLIP_LOOP = 2, PAD_BEAT_JUMP = 3;

    // Sound Color FX
    public static final int COLOR_SPACE = 0, COLOR_DUB_ECHO = 1, COLOR_SWEEP = 2, COLOR_NOISE = 3, COLOR_CRUSH = 4, COLOR_FILTER = 5;
    public static final int COLOR_FX_COUNT = 6;
    public static final String[] COLOR_FX_NAMES = {"SPACE", "DUB ECHO", "SWEEP", "NOISE", "CRUSH", "FILTER"};

    // Beat FX Unit
    public static final int BFX_DELAY = 0, BFX_ECHO = 1, BFX_REVERB = 2, BFX_FLANGER = 3, BFX_PHASER = 4, BFX_ROLL = 5, BFX_TRANS = 6, BFX_HELIX = 7, BFX_PINGPONG = 8;
    public static final int BFX_COUNT = 9;
    public static final String[] BFX_NAMES = {"DELAY", "ECHO", "REVERB", "FLANGER", "PHASER", "ROLL", "TRANS", "HELIX", "PING PONG"};
    public static final float[] BFX_BEAT_FRACTIONS = {0.125f, 0.25f, 0.5f, 0.75f, 1f, 2f, 4f, 8f, 16f};
    public static final String[] BFX_BEAT_LABELS = {"1/8", "1/4", "1/2", "3/4", "1", "2", "4", "8", "16"};

    private static final class Deck {
        ItemStack disc = ItemStack.EMPTY;
        boolean playing;
        /** Game time the song ends; -1 while stopped or when the length is unknown (Etched streams). */
        long endsAt = -1;
        float pitch = 1f;
        /** High, mid, low: 0 kill .. 0.5 flat .. 1 +6 dB. */
        final float[] eq = {0.5f, 0.5f, 0.5f};
        /** -1 low-pass .. 0 off .. 1 high-pass. */
        float filter;
        int fx;
        float fxAmount;
        /** Beats in the loop, 0 while not looping; the serial tells clients a new loop was set. */
        int loopBeats, loopSerial;

        // XDJ-AZ Channel Strip & Deck Controls
        float fader = 1f;
        float trim = 0.5f;
        int xfAssign = 0; // 0 = A, 1 = B, 2 = THRU
        int padMode = PAD_BEAT_LOOP;
        long mainCue = 0;
        final long[] hotCues = {-1, -1, -1, -1, -1, -1, -1, -1};
        boolean vinylMode = true;
        boolean slipMode = false;
        boolean reverse = false;
        boolean scratchHeld = false;
        long scratchStartTime = 0;
        boolean masterTempo = true;
        long playheadFrame = 0;
        int seekSerial = 0;
    }

    public static final int HIGH = 0, MID = 1, LOW = 2;
    /** The loop lengths on the mixer, in beats. */
    public static final int[] LOOPS = {1, 2, 4, 8, 16, 32};

    private final Deck[] decks = {new Deck(), new Deck(), new Deck(), new Deck()};
    /** The crossfader, 0 = only A .. 1 = only B, moving from {@code xfFrom} to {@code xfTo} over {@code xfTicks} from {@code xfStart}. */
    private float xfFrom = 0f, xfTo = 0f;
    private long xfStart;
    private int xfTicks;
    private boolean automix = true;
    private boolean autoDrop = true;
    private long lastDropTick = -100;
    /** The record crate's slot the last record was taken from: the next one is looked for after it. */
    private int crateSlot = -1;
    private int dropTicks = 0;

    // Sound Color FX Unit State
    private int activeColorFx = COLOR_FILTER;
    private float colorFxParam = 0.5f;

    // Beat FX Unit State
    private int beatFxType = BFX_ECHO;
    private float beatFxBeats = 1.0f;
    private int beatFxChannel = -1; // -1 = Master, 0..3 = Ch 1..4
    private boolean beatFxOn = false;
    private float beatFxDepth = 0.5f;

    public DjBoothBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        decks[A].xfAssign = 0;
        decks[B].xfAssign = 1;
        decks[C].xfAssign = 0;
        decks[D].xfAssign = 1;
    }

    // ---------------------------------------------------------------- where the decks sound

    /** Where deck {@code deck}'s song plays. */
    public BlockPos deckPos(int deck) {
        return switch (deck) {
            case B -> worldPosition.above();
            case C -> worldPosition.above(2);
            case D -> worldPosition.above(3);
            default -> worldPosition;
        };
    }

    /** Which deck plays from {@code soundBlock}: the booth itself is A, the block above it B, then C, D. */
    public int deckAt(BlockPos soundBlock) {
        if (soundBlock.getX() == worldPosition.getX() && soundBlock.getZ() == worldPosition.getZ()) {
            int diff = soundBlock.getY() - worldPosition.getY();
            if (diff >= 0 && diff < DECKS) return diff;
        }
        return A;
    }

    /** The booth whose deck plays from {@code soundBlock}, or null. */
    @Nullable
    public static DjBoothBlockEntity playingAt(Level level, BlockPos soundBlock) {
        for (int dy = 0; dy < DECKS; dy++) {
            if (level.getBlockEntity(soundBlock.below(dy)) instanceof DjBoothBlockEntity dj) return dj;
        }
        return null;
    }

    // ---------------------------------------------------------------- records

    public boolean insertDisc(ItemStack disc, boolean preferDeckB, Player player) {
        if (!DjBoothBlock.isMusicDisc(disc)) return false;
        int deck = preferDeckB
            ? (!decks[B].disc.isEmpty() ? (!decks[A].disc.isEmpty() ? (!decks[D].disc.isEmpty() ? C : D) : A) : B)
            : (!decks[A].disc.isEmpty() ? (!decks[B].disc.isEmpty() ? (!decks[C].disc.isEmpty() ? D : C) : B) : A);
        return insertDisc(disc, deck, player);
    }

    public boolean insertDisc(ItemStack disc, int targetDeck, Player player) {
        if (!DjBoothBlock.isMusicDisc(disc) || targetDeck < 0 || targetDeck >= DECKS) return false;
        if (!decks[targetDeck].disc.isEmpty()) return false;

        decks[targetDeck].disc = disc.copyWithCount(1);
        sync();
        if (level == null || level.isClientSide) return true;

        boolean anyPlaying = false;
        for (int i = 0; i < DECKS; i++) if (decks[i].playing) anyPlaying = true;

        if (!anyPlaying) {
            startDeck(targetDeck, player);
        } else if (player != null) {
            player.displayClientMessage(Component.translatable("createbrewery.dj.cued", name(targetDeck), title(decks[targetDeck].disc)), true);
        }
        return true;
    }

    /** Drops all records and stops the music; true if there was anything to drop. Server only. */
    boolean dropDecks() {
        if (level == null || level.isClientSide) return false;
        boolean hadAny = false;
        for (int deck = 0; deck < DECKS; deck++) {
            if (!decks[deck].disc.isEmpty()) hadAny = true;
            stopDeck(deck);
            ItemStack disc = decks[deck].disc;
            if (!disc.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, disc);
            decks[deck].disc = ItemStack.EMPTY;
            decks[deck].mainCue = 0;
            java.util.Arrays.fill(decks[deck].hotCues, -1L);
        }
        if (hadAny) sync();
        return hadAny;
    }

    public void ejectDiscs(Player player) {
        if (level == null || level.isClientSide) return;
        if (dropDecks()) {
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
            if (player != null) player.displayClientMessage(Component.translatable("createbrewery.dj.ejected"), true);
        }
    }

    // ---------------------------------------------------------------- playing

    /** Play/stop button of a deck. Server only. */
    public void toggleDeck(int deck, Player player) {
        if (decks[deck].playing) {
            stopDeck(deck);
            sync();
        } else {
            startDeck(deck, player);
        }
    }

    private void startDeck(int deck, Player player) {
        if (!(level instanceof ServerLevel server)) return;
        Deck d = decks[deck];
        if (d.disc.isEmpty()) {
            if (player != null) player.displayClientMessage(Component.translatable("createbrewery.dj.deck_empty", name(deck)), true);
            return;
        }
        stopDeck(deck);
        // A new song starts unlooped.
        d.loopBeats = 0;
        BlockPos at = deckPos(deck);

        boolean started;
        if (EtchedCompat.isEtchedDisc(d.disc)) {
            started = EtchedCompat.playEtchedDisc(server, at, d.disc);
        } else {
            started = JukeboxSong.fromStack(level.registryAccess(), d.disc).map(song -> {
                level.levelEvent(null, 1010, at, level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).getId(song.value()));
                // Pitched up, the record plays faster and ends sooner.
                d.endsAt = level.getGameTime() + (long) (song.value().lengthInTicks() / d.pitch);
                return true;
            }).orElse(false);
        }
        if (!started) return;

        d.playing = true;
        level.gameEvent(null, GameEvent.JUKEBOX_PLAY, at);
        // If only this song plays, pull the crossfader to its assigned side if assigned to A or B
        boolean otherPlaying = false;
        for (int i = 0; i < DECKS; i++) if (i != deck && decks[i].playing) otherPlaying = true;
        if (!otherPlaying) {
            if (d.xfAssign == 0) setCrossfader(0f);
            else if (d.xfAssign == 1) setCrossfader(1f);
        }
        if (player != null) player.displayClientMessage(Component.translatable("createbrewery.dj.playing", name(deck), title(d.disc)), true);
        sync();
    }

    private void stopDeck(int deck) {
        if (level == null || level.isClientSide || deck < 0 || deck >= DECKS) return;
        Deck d = decks[deck];
        if (d.playing) {
            // Stops Etched streams too: Etched keeps them in the same per-position jukebox map.
            level.levelEvent(1011, deckPos(deck), 0);
            level.gameEvent(null, GameEvent.JUKEBOX_STOP_PLAY, deckPos(deck));
        }
        d.playing = false;
        d.endsAt = -1;
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        // dropTicks is not saved: a booth reloaded mid-drop would keep its redstone signal for good.
        if (dropTicks > 0) dropTicks--;
        if (dropTicks == 0 && state.getValue(DjBoothBlock.POWERED)) {
            level.setBlock(pos, state.setValue(DjBoothBlock.POWERED, false), 3);
        }

        long now = level.getGameTime();
        for (int deck = 0; deck < DECKS; deck++) {
            Deck d = decks[deck];
            if (!d.playing || d.endsAt < 0) continue;
            if (d.scratchHeld && !d.slipMode) {
                d.endsAt++;
            }
            int nextDeck = (deck + 1) % DECKS;
            Deck other = decks[nextDeck];
            boolean cued = automix && !other.disc.isEmpty() && !other.playing;
            if (now >= d.endsAt) {
                d.playing = false;
                d.endsAt = -1;
                // Song over: keep the floor going with the cued record.
                if (cued) startDeck(nextDeck, null);
                // From the record crate: the played record goes back, the next one onto this deck
                if (automix && restock(deck) && !decks[nextDeck].playing) startDeck(deck, null);
                sync();
            } else if (cued && d.endsAt - now <= MIX_TICKS) {
                // Auto-mix: bring the cued record in and fade across while this one plays out.
                startDeck(nextDeck, null);
                fadeCrossfader(other.xfAssign == 1 ? 1f : 0f, (int) (d.endsAt - now));
            }
        }
    }

    /**
     * The record crate: a chest, barrel or any other container next to the booth (hoppers and
     * funnels can fill it), played in order. Null if there is none.
     */
    @Nullable
    public net.neoforged.neoforge.items.IItemHandler crate() {
        if (level == null) return null;
        for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[] {
            net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST,
            net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.DOWN}) {
            BlockPos at = worldPosition.relative(side);
            if (level.getBlockEntity(at) instanceof DjBoothBlockEntity) continue;
            var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, at, side.getOpposite());
            if (handler != null) return handler;
        }
        return null;
    }

    /**
     * Swaps the record on {@code deck} for the next one in the crate. A record is never lost: the
     * old one stays on the deck when there is no next one or no room for it. Server only.
     */
    boolean restock(int deck) {
        var crate = crate();
        if (crate == null) return false;
        int slots = crate.getSlots();
        for (int k = 1; k <= slots; k++) {
            int slot = Math.floorMod(crateSlot + k, slots);
            if (!DjBoothBlock.isMusicDisc(crate.getStackInSlot(slot))) continue;
            ItemStack next = crate.extractItem(slot, 1, false);
            if (next.isEmpty()) continue;
            ItemStack old = decks[deck].disc;
            if (!old.isEmpty()) {
                ItemStack left = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(crate, old, false);
                if (!left.isEmpty()) {
                    // No room for it: put the next one back, keep playing what we have.
                    ItemStack back = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(crate, next, false);
                    if (!back.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, back);
                    return false;
                }
            }
            crateSlot = slot;
            decks[deck].disc = next;
            decks[deck].mainCue = 0;
            java.util.Arrays.fill(decks[deck].hotCues, -1L);
            return true;
        }
        return false;
    }

    /** Loads a specific record from the adjacent crate slot directly onto {@code deck}. */
    public boolean loadFromCrate(int deck, int slot, Player player) {
        if (level == null || level.isClientSide || deck < 0 || deck >= DECKS) return false;
        var crate = crate();
        if (crate == null || slot < 0 || slot >= crate.getSlots()) return false;
        ItemStack stack = crate.getStackInSlot(slot);
        if (!DjBoothBlock.isMusicDisc(stack)) return false;

        ItemStack next = crate.extractItem(slot, 1, false);
        if (next.isEmpty()) return false;

        ItemStack old = decks[deck].disc;
        if (!old.isEmpty()) {
            stopDeck(deck);
            ItemStack left = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(crate, old, false);
            if (!left.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, left);
            }
        }
        decks[deck].disc = next;
        decks[deck].mainCue = 0;
        java.util.Arrays.fill(decks[deck].hotCues, -1L);
        sync();
        if (player != null) {
            player.displayClientMessage(Component.translatable("createbrewery.dj.cued", name(deck), title(next)), true);
        }
        return true;
    }

    /** Triggers performance pad action for a deck depending on its pad mode. */
    public void handlePad(int deck, int pad, Player player) {
        if (deck < 0 || deck >= DECKS || pad < 0 || pad >= 8) return;
        Deck d = decks[deck];
        // Hot cues are set by the screen in PCM frames (SET_HOT_CUE); a pad press has nothing to add to them.
        switch (d.padMode) {
            case PAD_BEAT_LOOP, PAD_SLIP_LOOP -> setLoop(deck, LOOPS[pad % LOOPS.length]);
            case PAD_BEAT_JUMP -> {
                int[] jumps = {-8, -4, -2, -1, 1, 2, 4, 8};
                // No measured tempo here: 120 BPM, 10 ticks a beat. The screen sends BEAT_JUMP with the real one.
                beatJump(deck, jumps[pad] * 10f);
            }
        }
    }

    // ---------------------------------------------------------------- mixer controls

    public void setCrossfader(float value) {
        xfFrom = xfTo = Mth.clamp(value, 0f, 1f);
        xfTicks = 0;
        sync();
    }

    private void fadeCrossfader(float to, int ticks) {
        xfFrom = crossfader(level.getGameTime());
        xfTo = Mth.clamp(to, 0f, 1f);
        xfStart = level.getGameTime();
        xfTicks = Math.max(1, ticks);
        sync();
    }

    /** The crossfader at {@code gameTime}, 0 = only A .. 1 = only B. */
    public float crossfader(long gameTime) {
        if (xfTicks <= 0) return xfTo;
        return Mth.lerp(Mth.clamp((gameTime - xfStart) / (float) xfTicks, 0f, 1f), xfFrom, xfTo);
    }

    /** How loud deck {@code deck} is at {@code gameTime}: routed by crossfader assignment (A, B, or THRU) and channel fader. */
    public float deckGain(int deck, long gameTime) {
        if (deck < 0 || deck >= DECKS) return 0f;
        float x = crossfader(gameTime) * Mth.HALF_PI;
        float xfGain = switch (decks[deck].xfAssign) {
            case 0 -> Mth.cos(x);
            case 1 -> Mth.sin(x);
            default -> 1.0f; // THRU
        };
        return xfGain * decks[deck].fader * (decks[deck].trim * 2.0f);
    }

    public boolean isScratchHeld(int deck) {
        return deck >= 0 && deck < DECKS && decks[deck].scratchHeld;
    }

    public void setScratchHeld(int deck, boolean held) {
        if (deck < 0 || deck >= DECKS) return;
        Deck d = decks[deck];
        if (d.scratchHeld == held) return;
        d.scratchHeld = held;
        if (held) {
            d.scratchStartTime = level != null ? level.getGameTime() : 0;
            if (level != null && d.playing && d.vinylMode) {
                BlockPos at = deckPos(deck);
                float vol = Math.max(0.3f, deckGain(deck, level.getGameTime()));
                level.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    ModSounds.DJ_SCRATCH_STOP.get(), SoundSource.BLOCKS, vol, 1.0f);
            }
        } else {
            if (!d.slipMode && d.playing && d.endsAt > 0 && level != null && d.scratchStartTime > 0) {
                long heldTicks = level.getGameTime() - d.scratchStartTime;
                if (heldTicks > 0) {
                    d.endsAt += heldTicks;
                }
            }
            d.scratchStartTime = 0;
        }
        sync();
    }

    /** Server: the tick each deck last played a scratch, so a spinning platter cannot flood the club with sounds. */
    private final long[] scratchedAt = new long[DECKS];

    public void jogScrub(int deck, float scrubAmount, @org.jetbrains.annotations.Nullable Player dj) {
        if (deck < 0 || deck >= DECKS || level == null) return;
        Deck d = decks[deck];
        if (!d.playing && d.disc.isEmpty()) return;

        BlockPos at = deckPos(deck);
        var sound = scrubAmount >= 0 ? ModSounds.DJ_SCRATCH_FWD : ModSounds.DJ_SCRATCH_BACK;
        float gain = deckGain(deck, level.getGameTime());
        float vol = Mth.clamp(gain * (0.6f + Math.min(0.6f, Math.abs(scrubAmount) * 2.5f)), 0.2f, 1.2f);
        float pitch = Mth.clamp(0.8f + Math.abs(scrubAmount) * 3.0f, 0.6f, 1.8f);

        // The DJ already hears it from the screen.
        if (level.getGameTime() - scratchedAt[deck] >= 2) {
            scratchedAt[deck] = level.getGameTime();
            level.playSound(dj, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                sound.get(), SoundSource.BLOCKS, vol, pitch);
        }

        if (!d.slipMode && d.playing && d.endsAt > 0) {
            long shift = (long) ((scrubAmount / (2.0 * Math.PI)) * 36.0f);
            d.endsAt = Math.max(level.getGameTime() + 1, d.endsAt - shift);
            sync();
        }
    }

    public void setPitch(int deck, float pitch) {
        if (deck < 0 || deck >= DECKS) return;
        Deck d = decks[deck];
        float p = Mth.clamp(pitch, 1f - PITCH_RANGE, 1f + PITCH_RANGE);
        // The rest of the song now plays at the new speed.
        if (d.playing && d.endsAt >= 0 && level != null) {
            long now = level.getGameTime();
            d.endsAt = now + Math.round((d.endsAt - now) * (double) d.pitch / p);
        }
        d.pitch = p;
        sync();
    }

    /**
     * The deck's knobs and switches back to how a new booth has them: pitch, EQ, filter, effect,
     * loop, channel fader, trim, crossfader side, pad mode, vinyl, slip, reverse and master tempo.
     * The record, its cues and the playback itself stay.
     */
    public void resetDeck(int deck) {
        if (deck < 0 || deck >= DECKS) return;
        setPitch(deck, 1f);
        Deck d = decks[deck];
        java.util.Arrays.fill(d.eq, 0.5f);
        d.filter = 0f;
        d.fx = 0;
        d.fxAmount = 0f;
        if (d.loopBeats > 0) {
            d.loopBeats = 0;
            d.loopSerial++;
        }
        d.fader = 1f;
        d.trim = 0.5f;
        d.xfAssign = deck % 2;
        d.padMode = PAD_BEAT_LOOP;
        d.vinylMode = true;
        d.slipMode = false;
        d.reverse = false;
        d.masterTempo = true;
        sync();
    }

    public void setEq(int deck, int band, float knob) {
        if (deck < 0 || deck >= DECKS || band < 0 || band >= 3) return;
        decks[deck].eq[band] = Mth.clamp(knob, 0f, 1f);
        sync();
    }

    public void setFilter(int deck, float value) {
        if (deck < 0 || deck >= DECKS) return;
        // Snaps to off in the middle, like the detent on a real filter knob.
        decks[deck].filter = Math.abs(value) < 0.03f ? 0f : Mth.clamp(value, -1f, 1f);
        sync();
    }

    public void setFx(int deck, int fx) {
        if (deck < 0 || deck >= DECKS) return;
        decks[deck].fx = Math.floorMod(fx, com.createbrewery.drunk.DeckFx.EFFECTS);
        sync();
    }

    public void setFxAmount(int deck, float amount) {
        if (deck < 0 || deck >= DECKS) return;
        decks[deck].fxAmount = Mth.clamp(amount, 0f, 1f);
        sync();
    }

    public static final int MAX_LOOP_BEATS = 64;

    /** Loops the last {@code beats} beats (slip mode: the song runs on underneath); the same length again lets go. Clamped to 1..64 beats. */
    public void setLoop(int deck, int beats) {
        if (deck < 0 || deck >= DECKS) return;
        Deck d = decks[deck];
        d.loopBeats = beats <= 0 || d.loopBeats == beats ? 0 : Math.min(MAX_LOOP_BEATS, beats);
        d.loopSerial++;
        sync();
    }

    public void setChannelFader(int deck, float fader) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].fader = Mth.clamp(fader, 0f, 1f);
            sync();
        }
    }

    public float getChannelFader(int deck) {
        return deck >= 0 && deck < DECKS ? decks[deck].fader : 1f;
    }

    public void setCrossfaderAssign(int deck, int assign) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].xfAssign = Math.floorMod(assign, 3);
            sync();
        }
    }

    public int getCrossfaderAssign(int deck) {
        return deck >= 0 && deck < DECKS ? decks[deck].xfAssign : 0;
    }

    public void setTrim(int deck, float trim) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].trim = Mth.clamp(trim, 0f, 1f);
            sync();
        }
    }

    public float getTrim(int deck) {
        return deck >= 0 && deck < DECKS ? decks[deck].trim : 0.5f;
    }

    public void setPadMode(int deck, int mode) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].padMode = Math.floorMod(mode, 4);
            sync();
        }
    }

    public int getPadMode(int deck) {
        return deck >= 0 && deck < DECKS ? decks[deck].padMode : PAD_BEAT_LOOP;
    }

    public void setVinylMode(int deck, boolean vinyl) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].vinylMode = vinyl;
            sync();
        }
    }

    public boolean isVinylMode(int deck) {
        return deck >= 0 && deck < DECKS && decks[deck].vinylMode;
    }

    public void setSlipMode(int deck, boolean slip) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].slipMode = slip;
            sync();
        }
    }

    public boolean isSlipMode(int deck) {
        return deck >= 0 && deck < DECKS && decks[deck].slipMode;
    }

    public void setReverse(int deck, boolean rev) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].reverse = rev;
            sync();
        }
    }

    public boolean isReverse(int deck) {
        return deck >= 0 && deck < DECKS && decks[deck].reverse;
    }

    public void setMasterTempo(int deck, boolean mt) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].masterTempo = mt;
            sync();
        }
    }

    public boolean isMasterTempo(int deck) {
        return deck >= 0 && deck < DECKS && decks[deck].masterTempo;
    }

    /** The song jumped {@code songTicks} (forward positive): it ends that much sooner, at the deck's pitch. */
    public void beatJump(int deck, float songTicks) {
        if (deck < 0 || deck >= DECKS || level == null) return;
        Deck d = decks[deck];
        if (d.playing && d.endsAt > 0) {
            // 8 beats at a slow 60 BPM at most.
            d.endsAt -= (long) (Mth.clamp(songTicks, -160f, 160f) / (double) d.pitch);
            sync();
        }
    }

    public void jumpPlayhead(int deck, long frame) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].playheadFrame = Math.max(0, frame);
            decks[deck].seekSerial++;
            sync();
        }
    }

    public long getPlayheadFrame(int deck) {
        return deck >= 0 && deck < DECKS ? decks[deck].playheadFrame : 0;
    }

    public int getSeekSerial(int deck) {
        return deck >= 0 && deck < DECKS ? decks[deck].seekSerial : 0;
    }

    // Sound Color FX Unit
    public int getActiveColorFx() {
        return activeColorFx;
    }

    public void setActiveColorFx(int type) {
        this.activeColorFx = Math.floorMod(type, COLOR_FX_COUNT);
        sync();
    }

    public float getColorFxParam() {
        return colorFxParam;
    }

    public void setColorFxParam(float param) {
        this.colorFxParam = Mth.clamp(param, 0f, 1f);
        sync();
    }

    // Beat FX Unit
    public int getBeatFxType() {
        return beatFxType;
    }

    public void setBeatFxType(int type) {
        this.beatFxType = Math.floorMod(type, BFX_COUNT);
        sync();
    }

    public float getBeatFxBeats() {
        return beatFxBeats;
    }

    public void setBeatFxBeats(float beats) {
        this.beatFxBeats = Mth.clamp(beats, BFX_BEAT_FRACTIONS[0], BFX_BEAT_FRACTIONS[BFX_BEAT_FRACTIONS.length - 1]);
        sync();
    }

    public int getBeatFxChannel() {
        return beatFxChannel;
    }

    public void setBeatFxChannel(int channel) {
        this.beatFxChannel = Mth.clamp(channel, -1, DECKS - 1);
        sync();
    }

    public boolean isBeatFxOn() {
        return beatFxOn;
    }

    public void setBeatFxOn(boolean on) {
        this.beatFxOn = on;
        sync();
    }

    public float getBeatFxDepth() {
        return beatFxDepth;
    }

    public void setBeatFxDepth(float depth) {
        this.beatFxDepth = Mth.clamp(depth, 0f, 1f);
        sync();
    }

    public void setAutomix(boolean on) {
        automix = on;
        sync();
    }

    public boolean isAutoDrop() {
        return autoDrop;
    }

    public void setAutoDrop(boolean on) {
        autoDrop = on;
        sync();
    }

    public void triggerDrop(@Nullable Player player) {
        long now = level != null ? level.getGameTime() : 0;
        if (now - lastDropTick < 40) return; // Debounce so multiple clients or echoes don't re-trigger
        lastDropTick = now;
        dropTicks = 25;
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (!state.getValue(DjBoothBlock.POWERED)) {
                level.setBlock(worldPosition, state.setValue(DjBoothBlock.POWERED, true), 3);
            }
            level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.9f, 1.4f);
            if (player != null) player.displayClientMessage(Component.translatable("createbrewery.dj.beat_drop"), true);
        }
    }

    // ---------------------------------------------------------------- state

    public ItemStack getDisc(int deck) { return deck >= 0 && deck < DECKS ? decks[deck].disc : ItemStack.EMPTY; }
    public boolean isPlaying(int deck) { return deck >= 0 && deck < DECKS && decks[deck].playing; }
    public float getPitch(int deck) { return deck >= 0 && deck < DECKS ? decks[deck].pitch : 1f; }
    public boolean isAutomix() { return automix; }
    public float getEq(int deck, int band) { return deck >= 0 && deck < DECKS && band >= 0 && band < 3 ? decks[deck].eq[band] : 0.5f; }
    public float getFilter(int deck) { return deck >= 0 && deck < DECKS ? decks[deck].filter : 0f; }
    public int getFx(int deck) { return deck >= 0 && deck < DECKS ? decks[deck].fx : 0; }
    public float getFxAmount(int deck) { return deck >= 0 && deck < DECKS ? decks[deck].fxAmount : 0f; }
    public int getLoopBeats(int deck) { return deck >= 0 && deck < DECKS ? decks[deck].loopBeats : 0; }
    public int getLoopSerial(int deck) { return deck >= 0 && deck < DECKS ? decks[deck].loopSerial : 0; }

    public long getMainCue(int deck) { return deck >= 0 && deck < DECKS ? decks[deck].mainCue : 0; }
    public void setMainCue(int deck, long cue) {
        if (deck >= 0 && deck < DECKS) {
            decks[deck].mainCue = Math.max(0, cue);
            decks[deck].playheadFrame = decks[deck].mainCue;
            decks[deck].seekSerial++;
            sync();
        }
    }

    public long getHotCue(int deck, int pad) {
        return deck >= 0 && deck < DECKS && pad >= 0 && pad < 8 ? decks[deck].hotCues[pad] : -1;
    }
    public void setHotCue(int deck, int pad, long frame) {
        if (deck >= 0 && deck < DECKS && pad >= 0 && pad < 8) {
            decks[deck].hotCues[pad] = frame;
            if (frame >= 0) {
                decks[deck].playheadFrame = frame;
                decks[deck].seekSerial++;
            }
            sync();
        }
    }

    /** Client: every loaded booth, for the lights and effects that follow whichever booth plays near them. */
    private static final java.util.Set<BlockPos> BOOTHS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Client, out of the world. */
    public static void clearClientBooths() {
        BOOTHS.clear();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) BOOTHS.remove(worldPosition);
    }

    /** What the mixer is doing to the music the crowd hears (client). */
    public ClubState.Mixer mixer(long gameTime) {
        boolean[] playing = new boolean[DECKS];
        float[] gain = new float[DECKS];
        float[] low = new float[DECKS];
        float[] filter = new float[DECKS];
        boolean[] loop = new boolean[DECKS];
        for (int i = 0; i < DECKS; i++) {
            playing[i] = isPlaying(i);
            gain[i] = deckGain(i, gameTime);
            low[i] = getEq(i, LOW);
            filter[i] = getFilter(i);
            loop[i] = getLoopBeats(i) > 0;
        }
        return ClubState.loudestMixer(playing, gain, low, filter, loop);
    }

    /** Client: the closest loaded booth within {@code reach} blocks of {@code pos} that is playing; ties go to the lower position so it never flips. */
    @Nullable
    public static DjBoothBlockEntity nearestPlaying(Level level, BlockPos pos, double reach) {
        DjBoothBlockEntity best = null;
        double bestDist = reach * reach;
        for (BlockPos p : BOOTHS) {
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof DjBoothBlockEntity dj)) continue;
            boolean anyPlay = false;
            for (int i = 0; i < DECKS; i++) if (dj.isPlaying(i)) anyPlay = true;
            if (!anyPlay) continue;
            double d = p.distSqr(pos);
            if (d < bestDist || (d == bestDist && best != null && p.compareTo(best.getBlockPos()) < 0)) {
                best = dj;
                bestDist = d;
            }
        }
        return best;
    }

    public static Component name(int deck) {
        return switch (deck) {
            case B -> Component.translatable("createbrewery.dj.deck_b");
            case C -> Component.translatable("createbrewery.dj.deck_c");
            case D -> Component.translatable("createbrewery.dj.deck_d");
            default -> Component.translatable("createbrewery.dj.deck_a");
        };
    }

    public Component title(ItemStack disc) {
        return EtchedCompat.isEtchedDisc(disc) && level != null
            ? EtchedCompat.getTrackDisplayName(disc, level.registryAccess())
            : disc.getHoverName();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        String[] keys = {"DeckA", "DeckB", "DeckC", "DeckD"};
        for (int deck = 0; deck < DECKS; deck++) {
            Deck d = decks[deck];
            if (!d.disc.isEmpty()) tag.put(keys[deck], d.disc.save(registries));
            tag.putBoolean(keys[deck] + "Playing", d.playing);
            tag.putLong(keys[deck] + "Ends", d.endsAt);
            tag.putFloat(keys[deck] + "Pitch", d.pitch);
            tag.putIntArray(keys[deck] + "Eq", new int[] {Math.round(d.eq[0] * 1000), Math.round(d.eq[1] * 1000), Math.round(d.eq[2] * 1000)});
            tag.putFloat(keys[deck] + "Filter", d.filter);
            tag.putInt(keys[deck] + "Fx", d.fx);
            tag.putFloat(keys[deck] + "FxAmount", d.fxAmount);
            tag.putInt(keys[deck] + "Loop", d.loopBeats);
            tag.putInt(keys[deck] + "LoopSerial", d.loopSerial);
            tag.putFloat(keys[deck] + "Fader", d.fader);
            tag.putInt(keys[deck] + "XfAssign", d.xfAssign);
            tag.putFloat(keys[deck] + "Trim", d.trim);
            tag.putInt(keys[deck] + "PadMode", d.padMode);
            tag.putBoolean(keys[deck] + "Vinyl", d.vinylMode);
            tag.putBoolean(keys[deck] + "Slip", d.slipMode);
            tag.putBoolean(keys[deck] + "Reverse", d.reverse);
            tag.putBoolean(keys[deck] + "ScratchHeld", d.scratchHeld);
            tag.putLong(keys[deck] + "MainCue", d.mainCue);
            tag.putLongArray(keys[deck] + "HotCues", d.hotCues);
            tag.putBoolean(keys[deck] + "MasterTempo", d.masterTempo);
            tag.putLong(keys[deck] + "Playhead", d.playheadFrame);
            tag.putInt(keys[deck] + "SeekSerial", d.seekSerial);
        }
        tag.putFloat("XfFrom", xfFrom);
        tag.putFloat("XfTo", xfTo);
        tag.putLong("XfStart", xfStart);
        tag.putInt("XfTicks", xfTicks);
        tag.putBoolean("Automix", automix);
        tag.putBoolean("AutoDrop", autoDrop);
        tag.putInt("CrateSlot", crateSlot);
        tag.putInt("ActiveColorFx", activeColorFx);
        tag.putFloat("ColorFxParam", colorFxParam);
        tag.putInt("BeatFxType", beatFxType);
        tag.putFloat("BeatFxBeats", beatFxBeats);
        tag.putInt("BeatFxChannel", beatFxChannel);
        tag.putBoolean("BeatFxOn", beatFxOn);
        tag.putFloat("BeatFxDepth", beatFxDepth);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String[] keys = {"DeckA", "DeckB", "DeckC", "DeckD"};
        for (int deck = 0; deck < DECKS; deck++) {
            Deck d = decks[deck];
            d.disc = tag.contains(keys[deck]) ? ItemStack.parse(registries, tag.getCompound(keys[deck])).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
            d.playing = tag.getBoolean(keys[deck] + "Playing");
            d.endsAt = tag.contains(keys[deck] + "Ends") ? tag.getLong(keys[deck] + "Ends") : -1;
            d.pitch = tag.contains(keys[deck] + "Pitch") ? tag.getFloat(keys[deck] + "Pitch") : 1f;
            int[] eq = tag.getIntArray(keys[deck] + "Eq");
            for (int band = 0; band < 3; band++) d.eq[band] = eq.length == 3 ? Mth.clamp(eq[band] / 1000f, 0f, 1f) : 0.5f;
            d.filter = Mth.clamp(tag.getFloat(keys[deck] + "Filter"), -1f, 1f);
            d.fx = Math.floorMod(tag.getInt(keys[deck] + "Fx"), com.createbrewery.drunk.DeckFx.EFFECTS);
            d.fxAmount = Mth.clamp(tag.getFloat(keys[deck] + "FxAmount"), 0f, 1f);
            d.loopBeats = tag.getInt(keys[deck] + "Loop");
            d.loopSerial = tag.getInt(keys[deck] + "LoopSerial");
            d.fader = tag.contains(keys[deck] + "Fader") ? tag.getFloat(keys[deck] + "Fader") : 1f;
            d.xfAssign = tag.contains(keys[deck] + "XfAssign") ? Math.floorMod(tag.getInt(keys[deck] + "XfAssign"), 3) : (deck % 2);
            d.trim = tag.contains(keys[deck] + "Trim") ? Mth.clamp(tag.getFloat(keys[deck] + "Trim"), 0f, 1f) : 0.5f;
            d.padMode = tag.contains(keys[deck] + "PadMode") ? Math.floorMod(tag.getInt(keys[deck] + "PadMode"), 4) : PAD_BEAT_LOOP;
            d.vinylMode = !tag.contains(keys[deck] + "Vinyl") || tag.getBoolean(keys[deck] + "Vinyl");
            d.slipMode = tag.getBoolean(keys[deck] + "Slip");
            d.reverse = tag.getBoolean(keys[deck] + "Reverse");
            d.scratchHeld = tag.getBoolean(keys[deck] + "ScratchHeld");
            d.mainCue = tag.getLong(keys[deck] + "MainCue");
            long[] loadedHot = tag.getLongArray(keys[deck] + "HotCues");
            if (loadedHot.length == 8) System.arraycopy(loadedHot, 0, d.hotCues, 0, 8);
            d.masterTempo = !tag.contains(keys[deck] + "MasterTempo") || tag.getBoolean(keys[deck] + "MasterTempo");
            d.playheadFrame = tag.getLong(keys[deck] + "Playhead");
            d.seekSerial = tag.getInt(keys[deck] + "SeekSerial");
        }
        xfFrom = tag.getFloat("XfFrom");
        xfTo = tag.getFloat("XfTo");
        xfStart = tag.getLong("XfStart");
        xfTicks = tag.getInt("XfTicks");
        automix = !tag.contains("Automix") || tag.getBoolean("Automix");
        autoDrop = !tag.contains("AutoDrop") || tag.getBoolean("AutoDrop");
        crateSlot = tag.contains("CrateSlot") ? tag.getInt("CrateSlot") : -1;
        if (tag.contains("ActiveColorFx")) activeColorFx = Math.floorMod(tag.getInt("ActiveColorFx"), COLOR_FX_COUNT);
        if (tag.contains("ColorFxParam")) colorFxParam = Mth.clamp(tag.getFloat("ColorFxParam"), 0f, 1f);
        if (tag.contains("BeatFxType")) beatFxType = Math.floorMod(tag.getInt("BeatFxType"), BFX_COUNT);
        if (tag.contains("BeatFxBeats")) beatFxBeats = tag.getFloat("BeatFxBeats");
        if (tag.contains("BeatFxChannel")) beatFxChannel = Mth.clamp(tag.getInt("BeatFxChannel"), -1, DECKS - 1);
        if (tag.contains("BeatFxOn")) beatFxOn = tag.getBoolean("BeatFxOn");
        if (tag.contains("BeatFxDepth")) beatFxDepth = tag.getFloat("BeatFxDepth");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) BOOTHS.add(worldPosition.immutable());
        for (Deck d : decks) d.scratchHeld = false;
        // The songs do not survive a reload; a saved "playing" would spin silent platters.
        if (level != null && !level.isClientSide) {
            for (Deck d : decks) {
                d.playing = false;
                d.endsAt = -1;
                d.loopBeats = 0;
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}
