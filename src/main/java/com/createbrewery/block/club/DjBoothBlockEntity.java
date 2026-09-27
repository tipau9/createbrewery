package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DjBoothBlockEntity extends BlockEntity {

    private ItemStack deckA = ItemStack.EMPTY;
    private ItemStack deckB = ItemStack.EMPTY;
    private float crossfader = 0.5f;
    private float pitch = 1.0f;
    private int dropTicks = 0;

    public DjBoothBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean insertDisc(ItemStack disc, boolean preferDeckB, Player player) {
        if (!DjBoothBlock.isMusicDisc(disc)) return false;

        String deckName = null;
        if (!preferDeckB && deckA.isEmpty()) {
            deckA = disc.copyWithCount(1);
            onDiscInserted(deckA, true);
            deckName = "Deck A";
        } else if (deckB.isEmpty()) {
            deckB = disc.copyWithCount(1);
            onDiscInserted(deckB, false);
            deckName = "Deck B";
        } else if (deckA.isEmpty()) {
            deckA = disc.copyWithCount(1);
            onDiscInserted(deckA, true);
            deckName = "Deck A";
        }

        if (deckName != null) {
            setChanged();
            if (player != null && level != null && level.isClientSide) {
                player.displayClientMessage(Component.translatable("createbrewery.dj.deck_inserted", deckName, disc.getHoverName()), true);
            }
            return true;
        }
        return false;
    }

    private void onDiscInserted(ItemStack disc, boolean isDeckA) {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.levelEvent(null, 1010, worldPosition, Item.getId(disc.getItem()));
        }
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
            level.levelEvent(1011, worldPosition, 0); // Stop record
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void triggerDrop(Player player) {
        dropTicks = 25;
        if (level != null) {
            BlockState state = getBlockState();
            if (!state.getValue(DjBoothBlock.POWERED)) {
                level.setBlock(worldPosition, state.setValue(DjBoothBlock.POWERED, true), 3);
            }
            level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.9f, 1.4f);
            if (level.isClientSide) {
                MusicPulse.drop(); // Trigger beat drop on music track
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
    public float getCrossfader() { return crossfader; }
    public float getPitch() { return pitch; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!deckA.isEmpty()) tag.put("DeckA", deckA.save(registries));
        if (!deckB.isEmpty()) tag.put("DeckB", deckB.save(registries));
        tag.putFloat("Crossfader", crossfader);
        tag.putFloat("Pitch", pitch);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("DeckA")) deckA = ItemStack.parse(registries, tag.getCompound("DeckA")).orElse(ItemStack.EMPTY);
        if (tag.contains("DeckB")) deckB = ItemStack.parse(registries, tag.getCompound("DeckB")).orElse(ItemStack.EMPTY);
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
