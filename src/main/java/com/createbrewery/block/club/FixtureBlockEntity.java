package com.createbrewery.block.club;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A club fixture: the console it is linked to and its group there (saved), and on the client what
 * it shows this tick - level, colour, where a moving head points and how far its beam reaches.
 */
public class FixtureBlockEntity extends BlockEntity {
    /** How far a beam reaches through the haze. */
    static final double RANGE = 24;
    /** Tungsten: blinders are warm white whatever the console says. */
    static final int TUNGSTEN = 0xFFB060;

    @Nullable
    private BlockPos console;
    private int group;

    // Client only.
    private DmxProgram own;
    private DmxProgram.Settings ownSettings;
    private float lit, prevLit;
    private final FixtureResponse response = new FixtureResponse();
    private int color = 0xFFFFFF;
    private final float[] pixels = new float[8];
    private float pan, tilt, prevPan, prevTilt;
    private float beam = (float) RANGE;
    @Nullable
    private BlockHitResult hit;
    /** The Veil room light (a {@code LightRenderHandle}), typed Object so Veil stays optional. */
    private Object roomLight;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean veil = ModList.get().isLoaded("veil");

    public FixtureBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private static FixtureResponse.Lamp lampOf(FixtureBlock.Kind kind) {
        return switch (kind) {
            case LED_BAR -> FixtureResponse.Lamp.LED;
            case PAR -> FixtureResponse.Lamp.PAR;
            case MOVING_HEAD -> FixtureResponse.Lamp.HEAD;
            case BLINDER -> FixtureResponse.Lamp.TUNGSTEN;
        };
    }

    FixtureBlock.Kind kind() {
        return ((FixtureBlock) getBlockState().getBlock()).kind;
    }

    @Nullable
    public BlockPos getConsole() {
        return console;
    }

    void setConsole(@Nullable BlockPos console) {
        this.console = console == null ? null : console.immutable();
        sync();
    }

    public int getGroup() {
        return group;
    }

    void setGroup(int g) {
        group = Math.floorMod(g, DmxProgram.GROUPS);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ---------------------------------------------------------------- client

    void clientTick() {
        if (level == null) return;
        DmxProgram program;
        DmxProgram.Settings settings;
        if (console != null && level.isLoaded(console) && level.getBlockEntity(console) instanceof DmxConsoleBlockEntity dmx) {
            program = dmx.output();
            settings = dmx.settings;
        } else {
            // On its own: the console's auto program, from the music heard here.
            if (own == null) {
                own = new DmxProgram();
                ownSettings = new DmxProgram.Settings();
            }
            program = own;
            settings = ownSettings;
            program.update(settings, level.getGameTime(), 0.05f, ClubStates.at(level, worldPosition, null));
        }
        FixtureBlock.Kind kind = kind();
        prevLit = lit;
        lit = response.dimmer(lampOf(kind), program.level[group]);
        int targetColor = kind == FixtureBlock.Kind.BLINDER ? TUNGSTEN : program.color[group];
        color = response.color(targetColor, kind == FixtureBlock.Kind.BLINDER ? 0f : kind == FixtureBlock.Kind.MOVING_HEAD ? 5f : 2f);
        for (int i = 0; i < pixels.length; i++) pixels[i] = program.pixel(settings, i, pixels.length);

        Direction facing = getBlockState().getValue(FixtureBlock.FACING);
        prevPan = pan;
        prevTilt = tilt;
        if (kind == FixtureBlock.Kind.MOVING_HEAD) {
            // Motors with a top speed and a limit on how hard they speed up and brake, like a real head.
            float[] aim = program.aim(settings, group);
            response.motor(aim[0], aim[1]);
            pan = response.pan;
            tilt = response.tilt;
        }
        if (lit > 0.02f) castBeam(facing);

        if (kind == FixtureBlock.Kind.BLINDER && lit > 0.3f) {
            // A blinder is meant to blind: glare like a strobe, softer while it only holds.
            boolean steady = Math.abs(lit - prevLit) < 0.2f;
            StrobeFlash.offer(level, worldPosition, facing, lit, 4, steady);
        }
        if (veil && kind != FixtureBlock.Kind.MOVING_HEAD) {
            try {
                float brightness = lit * (kind == FixtureBlock.Kind.BLINDER ? 1.6f : kind == FixtureBlock.Kind.PAR ? 1f : 0.6f);
                roomLight = StrobeRoomLight.update(roomLight, worldPosition, facing, brightness, true, color);
            } catch (RuntimeException | LinkageError e) {
                veil = false;
                LOGGER.warn("Veil fixture light unavailable", e);
            }
        }
    }

    private void castBeam(Direction facing) {
        Vec3 dir = direction(facing, 1f);
        Vec3 from = lens(facing);
        hit = level.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        beam = hit.getType() == HitResult.Type.MISS ? (float) RANGE : (float) hit.getLocation().distanceTo(from);
        if (hit.getType() == HitResult.Type.MISS) hit = null;
    }

    Vec3 lens(Direction facing) {
        return Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.4));
    }

    /** Which way the beam goes: the facing, panned and tilted for a moving head. */
    Vec3 direction(Direction facing, float partialTick) {
        if (kind() != FixtureBlock.Kind.MOVING_HEAD) return Vec3.atLowerCornerOf(facing.getNormal());
        double[] d = LaserBeams.direction(facing.getStepX(), facing.getStepY(), facing.getStepZ(),
            prevPan + (pan - prevPan) * partialTick, prevTilt + (tilt - prevTilt) * partialTick);
        return new Vec3(d[0], d[1], d[2]);
    }

    float level(float partialTick) {
        return prevLit + (lit - prevLit) * partialTick;
    }

    int color() {
        return color;
    }

    float pixel(int i) {
        return pixels[i];
    }

    float beam() {
        return beam;
    }

    @Nullable
    BlockHitResult hit() {
        return hit;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (roomLight != null) {
            StrobeRoomLight.free(roomLight);
            roomLight = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (console != null) tag.put("Console", NbtUtils.writeBlockPos(console));
        tag.putInt("Group", group);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        console = NbtUtils.readBlockPos(tag, "Console").orElse(null);
        group = Math.floorMod(tag.getInt("Group"), DmxProgram.GROUPS);
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
