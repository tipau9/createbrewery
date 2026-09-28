package com.createbrewery.block.club;

import com.createbrewery.compat.EtchedCompat;
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

    public static final int A = 0, B = 1;
    /** A turntable's pitch fader: +-8 %. */
    public static final float PITCH_RANGE = 0.08f;
    /** Auto-mix starts the cued deck this long before the playing song ends and crossfades over it. */
    static final int MIX_TICKS = 200;

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
    }

    public static final int HIGH = 0, MID = 1, LOW = 2;
    /** The loop lengths on the mixer, in beats. */
    public static final int[] LOOPS = {1, 2, 4, 8};

    private final Deck[] decks = {new Deck(), new Deck()};
    /** The crossfader, 0 = only A .. 1 = only B, moving from {@code xfFrom} to {@code xfTo} over {@code xfTicks} from {@code xfStart}. */
    private float xfFrom = 0f, xfTo = 0f;
    private long xfStart;
    private int xfTicks;
    private boolean automix = true;
    /** The record crate's slot the last record was taken from: the next one is looked for after it. */
    private int crateSlot = -1;
    private int dropTicks = 0;

    public DjBoothBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---------------------------------------------------------------- where the decks sound

    /** Where deck {@code deck}'s song plays. */
    public BlockPos deckPos(int deck) {
        return deck == A ? worldPosition : worldPosition.above();
    }

    /** Which deck plays from {@code soundBlock}: the booth itself is A, the block above it B. */
    public int deckAt(BlockPos soundBlock) {
        return soundBlock.equals(worldPosition) ? A : B;
    }

    /** The booth whose deck plays from {@code soundBlock}, or null. */
    @Nullable
    public static DjBoothBlockEntity playingAt(Level level, BlockPos soundBlock) {
        if (level.getBlockEntity(soundBlock) instanceof DjBoothBlockEntity dj) return dj;
        return level.getBlockEntity(soundBlock.below()) instanceof DjBoothBlockEntity dj ? dj : null;
    }

    // ---------------------------------------------------------------- records

    public boolean insertDisc(ItemStack disc, boolean preferDeckB, Player player) {
        if (!DjBoothBlock.isMusicDisc(disc)) return false;
        int deck = preferDeckB ? (decks[B].disc.isEmpty() ? B : A) : (decks[A].disc.isEmpty() ? A : B);
        if (!decks[deck].disc.isEmpty()) return false;

        decks[deck].disc = disc.copyWithCount(1);
        sync();
        if (level == null || level.isClientSide) return true;

        // Nothing playing: start right away. Otherwise cue it, like a DJ lining up the next record.
        if (!decks[A].playing && !decks[B].playing) {
            startDeck(deck, player);
        } else if (player != null) {
            player.displayClientMessage(Component.translatable("createbrewery.dj.cued", name(deck), title(decks[deck].disc)), true);
        }
        return true;
    }

    /** Drops both records and stops the music; true if there was anything to drop. Server only. */
    boolean dropDecks() {
        if (level == null || level.isClientSide || (decks[A].disc.isEmpty() && decks[B].disc.isEmpty())) return false;
        for (int deck = A; deck <= B; deck++) {
            stopDeck(deck);
            ItemStack disc = decks[deck].disc;
            if (!disc.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, disc);
            decks[deck].disc = ItemStack.EMPTY;
        }
        sync();
        return true;
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
        // The only song: pull the crossfader over to it, so a single record plays at full volume.
        if (!decks[1 - deck].playing) setCrossfader(deck == A ? 0f : 1f);
        if (player != null) player.displayClientMessage(Component.translatable("createbrewery.dj.playing", name(deck), title(d.disc)), true);
        sync();
    }

    private void stopDeck(int deck) {
        if (level == null || level.isClientSide) return;
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
        if (dropTicks > 0 && --dropTicks == 0 && state.getValue(DjBoothBlock.POWERED)) {
            level.setBlock(pos, state.setValue(DjBoothBlock.POWERED, false), 3);
        }

        long now = level.getGameTime();
        for (int deck = A; deck <= B; deck++) {
            Deck d = decks[deck], other = decks[1 - deck];
            if (!d.playing || d.endsAt < 0) continue;
            boolean cued = automix && !other.disc.isEmpty() && !other.playing;
            if (now >= d.endsAt) {
                d.playing = false;
                d.endsAt = -1;
                // Song over: keep the floor going with the cued record.
                if (cued) startDeck(1 - deck, null);
                // From the record crate: the played record goes back, the next one onto this deck -
                // cued for the next mix, or straight on if nothing else plays.
                if (automix && restock(deck) && !decks[1 - deck].playing) startDeck(deck, null);
                sync();
            } else if (cued && d.endsAt - now <= MIX_TICKS) {
                // Auto-mix: bring the cued record in and fade across while this one plays out.
                startDeck(1 - deck, null);
                fadeCrossfader(deck == A ? 1f : 0f, (int) (d.endsAt - now));
            }
        }
    }

    /**
     * The record crate: a chest, barrel or any other container next to the booth (hoppers and
     * funnels can fill it), played in order. Null if there is none.
     */
    @Nullable
    private net.neoforged.neoforge.items.IItemHandler crate() {
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
            return true;
        }
        return false;
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

    /** How loud deck {@code deck} is at {@code gameTime}: an equal-power crossfade, so the mix does not dip in the middle. */
    public float deckGain(int deck, long gameTime) {
        float x = crossfader(gameTime) * Mth.HALF_PI;
        return deck == A ? Mth.cos(x) : Mth.sin(x);
    }

    public void setPitch(int deck, float pitch) {
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

    public void setEq(int deck, int band, float knob) {
        decks[deck].eq[band] = Mth.clamp(knob, 0f, 1f);
        sync();
    }

    public void setFilter(int deck, float value) {
        // Snaps to off in the middle, like the detent on a real filter knob.
        decks[deck].filter = Math.abs(value) < 0.03f ? 0f : Mth.clamp(value, -1f, 1f);
        sync();
    }

    public void setFx(int deck, int fx) {
        decks[deck].fx = Math.floorMod(fx, com.createbrewery.drunk.DeckFx.EFFECTS);
        sync();
    }

    public void setFxAmount(int deck, float amount) {
        decks[deck].fxAmount = Mth.clamp(amount, 0f, 1f);
        sync();
    }

    /** Loops the last {@code beats} beats (slip mode: the song runs on underneath); the same length again lets go. */
    public void setLoop(int deck, int beats) {
        Deck d = decks[deck];
        boolean valid = false;
        for (int l : LOOPS) valid |= l == beats;
        d.loopBeats = !valid || d.loopBeats == beats ? 0 : beats;
        d.loopSerial++;
        sync();
    }

    public void setAutomix(boolean on) {
        automix = on;
        sync();
    }

    public void triggerDrop(Player player) {
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

    public ItemStack getDisc(int deck) { return decks[deck].disc; }
    public boolean isPlaying(int deck) { return decks[deck].playing; }
    public float getPitch(int deck) { return decks[deck].pitch; }
    public boolean isAutomix() { return automix; }
    public float getEq(int deck, int band) { return decks[deck].eq[band]; }
    public float getFilter(int deck) { return decks[deck].filter; }
    public int getFx(int deck) { return decks[deck].fx; }
    public float getFxAmount(int deck) { return decks[deck].fxAmount; }
    public int getLoopBeats(int deck) { return decks[deck].loopBeats; }
    public int getLoopSerial(int deck) { return decks[deck].loopSerial; }

    public static Component name(int deck) {
        return Component.translatable(deck == A ? "createbrewery.dj.deck_a" : "createbrewery.dj.deck_b");
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
        String[] keys = {"DeckA", "DeckB"};
        for (int deck = A; deck <= B; deck++) {
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
        }
        tag.putFloat("XfFrom", xfFrom);
        tag.putFloat("XfTo", xfTo);
        tag.putLong("XfStart", xfStart);
        tag.putInt("XfTicks", xfTicks);
        tag.putBoolean("Automix", automix);
        tag.putInt("CrateSlot", crateSlot);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String[] keys = {"DeckA", "DeckB"};
        for (int deck = A; deck <= B; deck++) {
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
        }
        xfFrom = tag.getFloat("XfFrom");
        xfTo = tag.getFloat("XfTo");
        xfStart = tag.getLong("XfStart");
        xfTicks = tag.getInt("XfTicks");
        automix = !tag.contains("Automix") || tag.getBoolean("Automix");
        crateSlot = tag.contains("CrateSlot") ? tag.getInt("CrateSlot") : -1;
    }

    @Override
    public void onLoad() {
        super.onLoad();
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
