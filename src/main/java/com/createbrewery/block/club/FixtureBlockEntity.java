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
    private int fan;

    // Client only.
    private DmxProgram own;
    private DmxProgram.Settings ownSettings;
    private float lit, prevLit;
    private final FixtureResponse response = new FixtureResponse();
    private int color = 0xFFFFFF;
    private final float[] pixels = new float[8];
    private float pan, tilt, prevPan, prevTilt;
    private final int[] pixelColors = new int[8];
    private float glideLevel;
    private int zoom = 1, gobo;
    private boolean prism;
    private float goboRot;
    private final float[] beamLen = new float[3];
    private final BlockHitResult[] beamHits = new BlockHitResult[3];
    private int beams = 1;

    {
        beamLen[0] = (float) RANGE;
    }
    /** The Veil room light (a {@code LightRenderHandle}), typed Object so Veil stays optional. */
    private Object roomLight;
    /** Which moving heads may cast a room light: the six nearest to the camera. */
    private static final LightBudget<FixtureBlockEntity> HEAD_LIGHTS = new LightBudget<>(6);
    private static long headLightsTick = Long.MIN_VALUE;
    /** A moving head's Veil light where its beam lands (same typing as {@code roomLight}). */
    private Object headLight;

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

    public int getFan() {
        return fan;
    }

    void setFan(int f) {
        fan = Math.floorMod(f, 8);
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
        float target;
        if (fan == 0) {
            target = program.level[group];
        } else {
            target = program.levelFor(settings, group, fan);
            // The group's own level already glides; a fanned fixture takes the raw target, so it glides here.
            if (program.noFlashing()) target = FixtureResponse.glide(glideLevel, target);
            glideLevel = target;
        }
        lit = response.dimmer(lampOf(kind), target);
        int targetColor = kind == FixtureBlock.Kind.BLINDER ? TUNGSTEN : fan == 0 ? program.color[group] : program.colorFor(settings, group, fan);
        color = response.color(targetColor, kind == FixtureBlock.Kind.BLINDER ? 0f : kind == FixtureBlock.Kind.MOVING_HEAD ? 5f : 2f);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = program.pixel(settings, i, pixels.length);
            pixelColors[i] = program.pixelColor(settings, i, pixels.length, group, fan);
        }
        zoom = settings.zoom;
        gobo = settings.gobo;
        prism = settings.prism && kind == FixtureBlock.Kind.MOVING_HEAD;
        // Under one turn a second at any tempo.
        goboRot = program.movePhase * 0.5f;

        Direction facing = getBlockState().getValue(FixtureBlock.FACING);
        prevPan = pan;
        prevTilt = tilt;
        if (kind == FixtureBlock.Kind.MOVING_HEAD) {
            // Motors with a top speed and a limit on how hard they speed up and brake, like a real head.
            float[] aim = program.aim(settings, group, fan);
            response.motor(aim[0], aim[1]);
            pan = response.pan;
            tilt = response.tilt;
        }
        if (lit > 0.02f) castBeams(facing);

        if (kind == FixtureBlock.Kind.BLINDER && lit > 0.3f) {
            // A blinder is meant to blind: glare like a strobe, softer while it only holds.
            boolean steady = Math.abs(lit - prevLit) < 0.2f;
            StrobeFlash.offer(level, worldPosition, facing, lit, 4, steady);
        }
        if (veil && com.createbrewery.Config.ENABLE_VEIL_LIGHTS.get() && kind != FixtureBlock.Kind.MOVING_HEAD) {
            try {
                float brightness = lit * (kind == FixtureBlock.Kind.BLINDER ? 1.6f : kind == FixtureBlock.Kind.PAR ? 1f : 0.6f);
                roomLight = StrobeRoomLight.update(roomLight, worldPosition, facing, brightness, true, color);
            } catch (RuntimeException | LinkageError e) {
                veil = false;
                LOGGER.warn("Veil fixture light unavailable", e);
            }
        }
        if (veil && com.createbrewery.Config.ENABLE_VEIL_LIGHTS.get() && kind == FixtureBlock.Kind.MOVING_HEAD) {
            try {
                long now = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
                if (now != headLightsTick) {
                    headLightsTick = now;
                    HEAD_LIGHTS.endTick();
                }
                BlockHitResult spot = beamHit(0);
                boolean wantsLight = lit > 0.2f && spot != null;
                if (wantsLight) {
                    HEAD_LIGHTS.offer(this, net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(Vec3.atCenterOf(worldPosition)));
                }
                // Never allocated while dark or over the budget; freed the moment it loses its slot.
                if (wantsLight && HEAD_LIGHTS.allowed(this)) {
                    Vec3 at = spot.getLocation().add(Vec3.atLowerCornerOf(spot.getDirection().getNormal()).scale(0.5));
                    headLight = StrobeRoomLight.updateAt(headLight, at, lit, color);
                } else if (headLight != null) {
                    StrobeRoomLight.free(headLight);
                    headLight = null;
                }
            } catch (RuntimeException | LinkageError e) {
                veil = false;
                LOGGER.warn("Veil head light unavailable", e);
            }
        }
    }

    private void castBeams(Direction facing) {
        beams = prism ? 3 : 1;
        Vec3 from = lens(facing);
        for (int b = 0; b < beams; b++) {
            Vec3 dir = beamDirection(b, 1f);
            BlockHitResult h = level.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            beamLen[b] = h.getType() == HitResult.Type.MISS ? (float) RANGE : (float) h.getLocation().distanceTo(from);
            beamHits[b] = h.getType() == HitResult.Type.MISS ? null : h;
        }
    }

    int beamCount() {
        return beams;
    }

    /** Beam {@code b}: 0 is the head's own, 1 and 2 the prism's, six degrees either side in pan. */
    Vec3 beamDirection(int b, float partialTick) {
        Direction facing = getBlockState().getValue(FixtureBlock.FACING);
        if (b == 0 || kind() != FixtureBlock.Kind.MOVING_HEAD) return direction(facing, partialTick);
        double[] d = LaserBeams.direction(facing.getStepX(), facing.getStepY(), facing.getStepZ(),
            prevPan + (pan - prevPan) * partialTick + (b == 1 ? 6 : -6), prevTilt + (tilt - prevTilt) * partialTick);
        return new Vec3(d[0], d[1], d[2]);
    }

    float beamLength(int b) {
        return beamLen[b];
    }

    @Nullable
    BlockHitResult beamHit(int b) {
        return beamHits[b];
    }

    int zoom() {
        return zoom;
    }

    int gobo() {
        return gobo;
    }

    boolean prism() {
        return prism;
    }

    float goboRotation() {
        return goboRot;
    }

    int pixelColor(int i) {
        return pixelColors[i];
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
        return beamLen[0];
    }

    @Nullable
    BlockHitResult hit() {
        return beamHits[0];
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (roomLight != null) {
            StrobeRoomLight.free(roomLight);
            roomLight = null;
        }
        if (headLight != null) {
            StrobeRoomLight.free(headLight);
            headLight = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (console != null) tag.put("Console", NbtUtils.writeBlockPos(console));
        tag.putInt("Group", group);
        tag.putInt("Fan", fan);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        console = NbtUtils.readBlockPos(tag, "Console").orElse(null);
        group = Math.floorMod(tag.getInt("Group"), DmxProgram.GROUPS);
        fan = Math.floorMod(tag.getInt("Fan"), 8);
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
