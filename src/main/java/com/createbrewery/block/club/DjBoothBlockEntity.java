package com.createbrewery.block.club;

import com.createbrewery.compat.EtchedCompat;
import com.createbrewery.drunk.MusicPulse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class DjBoothBlockEntity extends BlockEntity {

    private ItemStack deckA = ItemStack.EMPTY;
    private ItemStack deckB = ItemStack.EMPTY;
    private int activeDeck = 0; // 0 = none, 1 = Deck A, 2 = Deck B
    private boolean isPlaying = false;
    private float crossfader = 0.5f;
    private float pitch = 1.0f;
    private int dropTicks = 0;

    public DjBoothBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean insertDisc(ItemStack disc, boolean preferDeckB, Player player) {
        if (!DjBoothBlock.isMusicDisc(disc)) return false;

        String deckName = null;
        boolean insertedDeckA = false;

        if (preferDeckB) {
            if (deckB.isEmpty()) {
                deckB = disc.copyWithCount(1);
                deckName = "Deck B";
                insertedDeckA = false;
            } else if (deckA.isEmpty()) {
                deckA = disc.copyWithCount(1);
                deckName = "Deck A";
                insertedDeckA = true;
            }
        } else {
            if (deckA.isEmpty()) {
                deckA = disc.copyWithCount(1);
                deckName = "Deck A";
                insertedDeckA = true;
            } else if (deckB.isEmpty()) {
                deckB = disc.copyWithCount(1);
                deckName = "Deck B";
                insertedDeckA = false;
            }
        }

        if (deckName != null) {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            ItemStack inserted = insertedDeckA ? deckA : deckB;
            onDiscInserted(inserted, insertedDeckA, player);

            if (player != null && level != null) {
                Component trackTitle = EtchedCompat.isEtchedDisc(inserted)
                    ? EtchedCompat.getTrackDisplayName(inserted, level.registryAccess())
                    : inserted.getHoverName();
                player.displayClientMessage(Component.translatable("createbrewery.dj.deck_inserted", deckName, trackTitle), true);
            }
            return true;
        }
        return false;
    }

    private void onDiscInserted(ItemStack disc, boolean isDeckA, Player player) {
        if (level != null && !level.isClientSide) {
            playDeck(isDeckA, player);
        }
    }

    public void playDeck(boolean isDeckA, Player player) {
        if (level == null || level.isClientSide) return;

        ItemStack disc = isDeckA ? deckA : deckB;
        String deckName = isDeckA ? "Deck A" : "Deck B";
        if (disc.isEmpty()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("createbrewery.dj.deck_empty", deckName), true);
            }
            return;
        }

        // Stop any previous playing track
        stopMusic();

        activeDeck = isDeckA ? 1 : 2;
        isPlaying = true;

        if (EtchedCompat.isEtchedDisc(disc)) {
            // Etched Mod Custom Vinyl Streaming
            boolean started = EtchedCompat.playEtchedDisc((ServerLevel) level, worldPosition, disc);
            if (started) {
                Component title = EtchedCompat.getTrackDisplayName(disc, level.registryAccess());
                if (player != null) {
                    player.displayClientMessage(Component.translatable("createbrewery.dj.playing", deckName, title), true);
                }
                level.gameEvent(null, GameEvent.JUKEBOX_PLAY, worldPosition);
            }
        } else {
            // Vanilla or standard JukeboxSong
            JukeboxSong.fromStack(level.registryAccess(), disc).ifPresentOrElse(songHolder -> {
                int songId = level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).getId(songHolder.value());
                level.levelEvent(null, 1010, worldPosition, songId);
                level.playSound(null, worldPosition, songHolder.value().soundEvent().value(), SoundSource.RECORDS, 3.0f, pitch);
                level.gameEvent(null, GameEvent.JUKEBOX_PLAY, worldPosition);
                if (player != null) {
                    player.displayClientMessage(Component.translatable("createbrewery.dj.playing", deckName, disc.getHoverName()), true);
                }
            }, () -> {
                // Fallback attempt via Etched or play hover name
                if (EtchedCompat.isLoaded()) {
                    EtchedCompat.playEtchedDisc((ServerLevel) level, worldPosition, disc);
                    level.gameEvent(null, GameEvent.JUKEBOX_PLAY, worldPosition);
                }
                if (player != null) {
                    player.displayClientMessage(Component.translatable("createbrewery.dj.playing", deckName, disc.getHoverName()), true);
                }
            });
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void stopMusic() {
        if (level == null || level.isClientSide) return;

        level.levelEvent(1011, worldPosition, 0);
        if (level instanceof ServerLevel sl) {
            EtchedCompat.stopEtchedDisc(sl, worldPosition);
        }
        level.gameEvent(null, GameEvent.JUKEBOX_STOP_PLAY, worldPosition);
        isPlaying = false;
    }

    public void ejectDiscs(Player player) {
        if (level == null || level.isClientSide) return;
        boolean ejected = false;
        if (!deckA.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, deckA);
            deckA = ItemStack.EMPTY;
            ejected = true;
        }
        if (!deckB.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, deckB);
            deckB = ItemStack.EMPTY;
            ejected = true;
        }
        if (ejected) {
            stopMusic();
            activeDeck = 0;
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
            if (player != null) {
                player.displayClientMessage(Component.translatable("createbrewery.dj.ejected"), true);
            }
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void triggerDrop(Player player) {
        dropTicks = 25;
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (!state.getValue(DjBoothBlock.POWERED)) {
                level.setBlock(worldPosition, state.setValue(DjBoothBlock.POWERED, true), 3);
            }
            level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.9f, 1.4f);
            if (player != null) {
                player.displayClientMessage(Component.translatable("createbrewery.dj.beat_drop"), true);
            }
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (dropTicks > 0) {
            dropTicks--;
            if (dropTicks == 0 && state.getValue(DjBoothBlock.POWERED)) {
                level.setBlock(pos, state.setValue(DjBoothBlock.POWERED, false), 3);
            }
        }
    }

    public ItemStack getDeckA() { return deckA; }
    public ItemStack getDeckB() { return deckB; }
    public int getActiveDeck() { return activeDeck; }
    public boolean isPlaying() { return isPlaying; }
    public float getCrossfader() { return crossfader; }
    public float getPitch() { return pitch; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!deckA.isEmpty()) tag.put("DeckA", deckA.save(registries));
        if (!deckB.isEmpty()) tag.put("DeckB", deckB.save(registries));
        tag.putInt("ActiveDeck", activeDeck);
        tag.putBoolean("IsPlaying", isPlaying);
        tag.putFloat("Crossfader", crossfader);
        tag.putFloat("Pitch", pitch);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("DeckA")) deckA = ItemStack.parse(registries, tag.getCompound("DeckA")).orElse(ItemStack.EMPTY);
        if (tag.contains("DeckB")) deckB = ItemStack.parse(registries, tag.getCompound("DeckB")).orElse(ItemStack.EMPTY);
        if (tag.contains("ActiveDeck")) activeDeck = tag.getInt("ActiveDeck");
        if (tag.contains("IsPlaying")) isPlaying = tag.getBoolean("IsPlaying");
        if (tag.contains("Crossfader")) crossfader = tag.getFloat("Crossfader");
        if (tag.contains("Pitch")) pitch = tag.getFloat("Pitch");
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
