package com.createbrewery.entity;

import com.createbrewery.ModItems;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.effect.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Techno Club Bouncer (Berghain style door policy).
 * Inspects player sobriety, checks guestlist, collects entry fee,
 * applies hand stamps for re-entry, and tapes smartphone/spyglass cameras.
 */
public class BouncerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> ARMS_CROSSED =
        SynchedEntityData.defineId(BouncerEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos guardPos;
    private Direction guardFacing;
    private UUID ownerUUID;
    private int entryFee = 15;
    private int collectedEmeralds = 0;
    private final Set<String> guestList = new HashSet<>();
    private int admitTicks = 0;

    public BouncerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 100.0)
            .add(Attributes.MOVEMENT_SPEED, 0.22)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ARMOR, 10.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ARMS_CROSSED, true);
    }

    public boolean isArmsCrossed() {
        return entityData.get(ARMS_CROSSED);
    }

    public void setArmsCrossed(boolean crossed) {
        entityData.set(ARMS_CROSSED, crossed);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GuardDoorGoal());
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (guardPos == null && isAlive()) {
            guardPos = blockPosition();
            guardFacing = getDirection();
        }

        if (admitTicks > 0) {
            admitTicks--;
            if (admitTicks == 0 && !level().isClientSide) {
                closeDoors();
                setArmsCrossed(true);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && !player.getAbilities().instabuild) {
            // Push aggressive player away
            pushAway(player, 1.2f);
            say(player, "§cKeine Schlägereien vor der Tür!");
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;

        ItemStack held = player.getItemInHand(hand);

        // --- Club Owner / Admin Management (Sneak + Right-Click) ---
        boolean isOwner = ownerUUID == null || ownerUUID.equals(player.getUUID()) || player.getAbilities().instabuild;
        if (player.isShiftKeyDown() && isOwner) {
            if (ownerUUID == null) ownerUUID = player.getUUID();

            // Book: update guest list
            if (held.is(Items.WRITABLE_BOOK) || held.is(Items.WRITTEN_BOOK)) {
                int count = readGuestListBook(held);
                say(player, "§aGästeliste aktualisiert: " + count + " Gäste eingetragen.");
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0f, 1.2f);
                return InteractionResult.SUCCESS;
            }

            // Emerald: configure entry fee
            if (held.is(Items.EMERALD)) {
                entryFee = Math.max(1, held.getCount());
                say(player, "§aEintrittspreis auf " + entryFee + " Smaragde gesetzt.");
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 1.0f);
                return InteractionResult.SUCCESS;
            }

            // Empty hand: collect cashbox & status
            if (held.isEmpty()) {
                if (collectedEmeralds > 0) {
                    ItemStack cash = new ItemStack(Items.EMERALD, collectedEmeralds);
                    if (!player.addItem(cash)) player.drop(cash, false);
                    say(player, "§aKasse geleert: " + collectedEmeralds + " Smaragde entnommen.");
                    collectedEmeralds = 0;
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 1.0f, 1.0f);
                } else {
                    say(player, "§fStatus: Eintritt: " + entryFee + " Smaragde | Kasse: 0 Smaragde | Gästeliste: " + guestList.size() + " Gäste.");
                }
                return InteractionResult.SUCCESS;
            }
        }

        // --- Door Policy Check ---
        evaluateEntry(player, held);
        return InteractionResult.SUCCESS;
    }

    private void evaluateEntry(Player player, ItemStack held) {
        // 1. Club-Stempel Check (Wiedereinlass)
        long stampExpiry = player.getPersistentData().getLong("createbrewery:club_stamp");
        if (stampExpiry > level().getGameTime()) {
            say(player, "§aStempel passt. Geh wieder rein.");
            admit(player);
            return;
        }

        // 2. Zustand-Check (Pegel & Drogen)
        DrunkState state = DrunkServer.state(player);
        if (state.blood >= 0.8f || player.hasEffect(ModEffects.BLACKOUT)
            || player.hasEffect(ModEffects.POISONING) || player.hasEffect(ModEffects.HANGOVER)) {
            say(player, "§cHeute leider nicht. Trink erst mal ein Wasser draußen, du schwankst ja schon.");
            reject(player);
            return;
        }
        if (player.hasEffect(ModEffects.PSYCHOSIS) || player.hasEffect(ModEffects.BAD_TRIP)
            || player.hasEffect(ModEffects.WITHDRAWAL)) {
            say(player, "§cKomm erst mal runter, nicht in dem Zustand.");
            reject(player);
            return;
        }

        // 3. Gästeliste Check
        String playerName = player.getName().getString().toLowerCase();
        if (guestList.contains(playerName) || guestList.contains(player.getStringUUID().toLowerCase())) {
            say(player, "§aStehst auf der Gästeliste. Viel Spaß drin!");
            tapeCameras(player);
            stamp(player);
            admit(player);
            return;
        }

        // 4. Eintritt (Cover Charge) Check
        if (held.is(Items.EMERALD)) {
            if (held.getCount() >= entryFee) {
                held.shrink(entryFee);
                collectedEmeralds += entryFee;
                say(player, "§aAlles klar, " + entryFee + " Smaragde. Viel Spaß drin! Garderobe ist links.");
                tapeCameras(player);
                stamp(player);
                admit(player);
            } else {
                say(player, "§cEintritt kostet " + entryFee + " Smaragde. Du hast nur " + held.getCount() + " in der Hand.");
                reject(player);
            }
        } else {
            say(player, "§fEinlass nur mit Gästeliste oder " + entryFee + " Smaragden Eintritt.");
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 0.9f);
        }
    }

    private void admit(Player player) {
        admitTicks = 60; // 3 seconds open
        setArmsCrossed(false);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 0.85f);
        openDoors();
    }

    private void reject(Player player) {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 0.8f);
        pushAway(player, 0.6f);
    }

    private void pushAway(Player player, float power) {
        Vec3 dir = player.position().subtract(position()).normalize().scale(power);
        player.setDeltaMovement(dir.x, 0.2, dir.z);
        player.hurtMarked = true;
    }

    private void tapeCameras(Player player) {
        boolean taped = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.SPYGLASS)) {
                ItemStack tapedSpyglass = new ItemStack(ModItems.TAPED_SPYGLASS.get(), stack.getCount());
                if (stack.has(DataComponents.CUSTOM_NAME)) {
                    tapedSpyglass.set(DataComponents.CUSTOM_NAME, stack.get(DataComponents.CUSTOM_NAME));
                }
                player.getInventory().setItem(i, tapedSpyglass);
                taped = true;
            }
        }
        if (taped) {
            level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.4f);
            player.displayClientMessage(Component.literal("§e[Türsteher] Handy und Kamera werden abgeklebt. Keine Fotos im Club!"), false);
        }
    }

    private void stamp(Player player) {
        player.getPersistentData().putLong("createbrewery:club_stamp", level().getGameTime() + 24000L);
        level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.0f, 1.5f);
        player.displayClientMessage(Component.literal("§6[Club] Du hast den Club-Stempel erhalten (Wiedereinlass aktiv)!"), false);
    }

    private void openDoors() {
        BlockPos center = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 2, 3))) {
            BlockState state = level().getBlockState(p);
            if (state.getBlock() instanceof DoorBlock && !state.getValue(DoorBlock.OPEN)) {
                level().setBlock(p, state.setValue(DoorBlock.OPEN, true), 3);
                level().playSound(null, p, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 1f);
            } else if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                level().setBlock(p, state.setValue(FenceGateBlock.OPEN, true), 3);
                level().playSound(null, p, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1f, 1f);
            }
        }
    }

    private void closeDoors() {
        BlockPos center = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 2, 3))) {
            BlockState state = level().getBlockState(p);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) {
                level().setBlock(p, state.setValue(DoorBlock.OPEN, false), 3);
                level().playSound(null, p, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 1f);
            } else if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) {
                level().setBlock(p, state.setValue(FenceGateBlock.OPEN, false), 3);
                level().playSound(null, p, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1f, 1f);
            }
        }
    }

    private int readGuestListBook(ItemStack book) {
        guestList.clear();
        if (book.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (content != null) {
                for (var page : content.pages()) {
                    addNames(page.raw());
                }
            }
        } else if (book.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null) {
                for (var page : content.pages()) {
                    addNames(page.raw().getString());
                }
            }
        }
        return guestList.size();
    }

    private void addNames(String text) {
        GuestList.add(guestList, text);
    }

    private void say(Player player, String message) {
        player.displayClientMessage(Component.literal("§8[§6Türsteher§8] §f" + message), false);
    }

    /** The cash box goes with him: entry fees not yet collected drop where he falls. */
    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int left = collectedEmeralds; left > 0; left -= 64) spawnAtLocation(new ItemStack(Items.EMERALD, Math.min(64, left)));
        collectedEmeralds = 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (guardPos != null) {
            tag.putInt("GuardX", guardPos.getX());
            tag.putInt("GuardY", guardPos.getY());
            tag.putInt("GuardZ", guardPos.getZ());
        }
        if (guardFacing != null) tag.putString("GuardFacing", guardFacing.getName());
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        tag.putInt("EntryFee", entryFee);
        tag.putInt("CollectedEmeralds", collectedEmeralds);
        tag.putInt("AdmitTicks", admitTicks);

        ListTag list = new ListTag();
        for (String name : guestList) list.add(StringTag.valueOf(name));
        tag.put("GuestList", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("GuardX")) {
            guardPos = new BlockPos(tag.getInt("GuardX"), tag.getInt("GuardY"), tag.getInt("GuardZ"));
        }
        if (tag.contains("GuardFacing")) guardFacing = Direction.byName(tag.getString("GuardFacing"));
        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        if (tag.contains("EntryFee")) entryFee = tag.getInt("EntryFee");
        if (tag.contains("CollectedEmeralds")) collectedEmeralds = tag.getInt("CollectedEmeralds");
        // Doors opened for a guest close on time even if the chunk unloaded in between.
        admitTicks = tag.getInt("AdmitTicks");

        guestList.clear();
        if (tag.contains("GuestList", 9)) {
            ListTag list = tag.getList("GuestList", 8);
            for (int i = 0; i < list.size(); i++) {
                guestList.add(list.getString(i).toLowerCase());
            }
        }
    }

    /** AI goal: keeps bouncer standing firmly at guard position. */
    private class GuardDoorGoal extends Goal {
        GuardDoorGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return guardPos != null && !isLeashed() && distanceToSqr(Vec3.atCenterOf(guardPos)) > 1.5;
        }

        @Override
        public void tick() {
            if (guardPos != null) {
                getNavigation().moveTo(guardPos.getX() + 0.5, guardPos.getY(), guardPos.getZ() + 0.5, 1.0);
            }
        }
    }
}
