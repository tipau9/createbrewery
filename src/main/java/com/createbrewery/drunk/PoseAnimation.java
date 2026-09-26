package com.createbrewery.drunk;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drugs.DrugPose;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * A player's body on drugs, drawn through playerAnimator (only loaded when it is installed). Off
 * in first person. In playerAnimator "body" is the whole player (position in blocks, turning
 * about the hips); "torso" is only the trunk's box - moving it tears it off the head, arms and
 * legs without bendy-lib - so it is never used here.
 * <ul>
 *   <li>Lasting poses: dancing on MDMA (each player has one of three styles - hands in the air, the
 *       shuffle, head-banging), the chin sinking to the chest on the nod (mostly the real pitch,
 *       which NodClient already lowers; this only adds a little), slumped and lopsided in a K-hole,
 *       doubled over with laughter when stoned.</li>
 *   <li>One-off actions, sent by the server: doubled over throwing up, convulsing, a line to the
 *       nose, a drag (joint, pipe or balloon), a shot into the arm, collapsing to the ground.</li>
 * </ul>
 */
final class PoseAnimation implements IAnimation {
    /** 128 beats a minute, in radians a second. */
    private static final float BEAT = (float) (Math.PI * 2 * 128 / 60);

    static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(CreateBrewery.ID("drug_pose"), 1500, PoseAnimation::new);
    }

    private final AbstractClientPlayer player;
    private final float[] shown = new float[5];

    private PoseAnimation(AbstractClientPlayer player) {
        this.player = player;
    }

    @Override
    public void tick() {
        DrugPose pose = DrugPose.SEEN.get(player.getId());
        for (int i = 1; i < shown.length; i++) {
            float target = pose != null && pose.kind() == i ? pose.strength() : 0f;
            shown[i] += (target - shown[i]) * 0.15f;
        }
        DrugPose.Action action = DrugPose.ACTING.get(player.getId());
        if (action != null && System.currentTimeMillis() > action.end()) DrugPose.ACTING.remove(player.getId(), action);
    }

    @Override
    public boolean isActive() {
        return shown[DrugPose.DANCE] + shown[DrugPose.NOD] + shown[DrugPose.SLUMP] + shown[DrugPose.LAUGH] > 0.01f
            || DrugPose.ACTING.containsKey(player.getId());
    }

    @Override
    public void setupAnim(float tickDelta) {}

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value) {
        if (type != TransformType.POSITION && type != TransformType.ROTATION) return value;
        boolean position = type == TransformType.POSITION;
        float t = (player.tickCount + tickDelta) / 20f;
        float[] d = new float[3];
        pose(part, position, t, value, d);
        DrugPose.Action action = DrugPose.ACTING.get(player.getId());
        if (action != null) {
            // An action takes over the body, easing in and out over its first and last tenth.
            float p = action.progress(System.currentTimeMillis());
            float w = Mth.clamp(Math.min(p, 1f - p) * 10f, 0f, 1f);
            float[] a = new float[3];
            action(action.kind(), p, part, position, t, value, a);
            for (int i = 0; i < 3; i++) d[i] = d[i] * (1f - w) + a[i] * w;
        }
        return new Vec3f(value.getX() + d[0], value.getY() + d[1], value.getZ() + d[2]);
    }

    /** The lasting poses, as offsets from the vanilla pose. */
    private void pose(String part, boolean position, float t, Vec3f value, float[] d) {
        float dance = shown[DrugPose.DANCE], nod = shown[DrugPose.NOD], slump = shown[DrugPose.SLUMP], laugh = shown[DrugPose.LAUGH];
        float beat = (float) Math.sin(t * BEAT), bounce = 0.5f + 0.5f * (float) Math.cos(t * BEAT);
        float jerk = (float) Math.sin(t * 25f);
        int style = Math.floorMod(player.getUUID().hashCode(), 3);
        if (position) {
            // Bouncing on the beat; sagging in the K-hole.
            if (part.equals("body")) d[1] = -(style == 1 ? 0.05f : 0.09f) * bounce * dance - 0.09f * slump;
            return;
        }
        switch (part) {
            case "head" -> {
                d[0] = (style == 2 ? 0.7f * bounce : 0.25f * beat) * dance + 0.15f * nod + 0.5f * slump + 0.12f * jerk * laugh;
                d[2] = 0.35f * slump;
            }
            case "body" -> {
                d[0] = (style == 2 ? 0.12f * bounce : 0f) * dance + 0.2f * nod + (0.35f + 0.08f * jerk) * laugh;
                d[1] = (style == 1 ? 0.3f * beat : 0f) * dance;
                d[2] = 0.12f * slump;
            }
            case "rightArm", "leftArm" -> {
                float side = part.equals("rightArm") ? 1f : -1f;
                float armDance = switch (style) {
                    // Hands in the air, swaying in turn.
                    case 0 -> -2.8f + 0.3f * beat * side - value.getX();
                    // The shuffle: arms low, pumping in turn.
                    case 1 -> -0.6f * side * beat;
                    // Head-banging: fists forward, pumping with the head.
                    default -> -1.2f - 0.5f * bounce;
                };
                d[0] = armDance * dance + 0.2f * nod - 0.5f * laugh;
                d[2] = -side * (style == 0 ? 0.3f : 0.1f) * dance + side * 0.25f * slump;
            }
            case "rightLeg", "leftLeg" -> {
                float side = part.equals("rightLeg") ? 1f : -1f;
                d[0] = side * (style == 1 ? 0.5f : 0.15f) * beat * dance;
            }
            default -> {}
        }
    }

    /** One-off actions at progress {@code p} (0..1), as offsets from the vanilla pose. */
    private static void action(int kind, float p, String part, boolean position, float t, Vec3f value, float[] d) {
        boolean right = part.equals("rightArm"), left = part.equals("leftArm");
        switch (kind) {
            case DrugPose.VOMIT -> {
                // Doubled over, hands on the knees, heaving.
                if (position) return;
                float heave = 0.15f * (float) Math.sin(t * 18f);
                if (part.equals("body")) d[0] = 0.45f + 0.5f * heave;
                if (part.equals("head")) d[0] = 0.5f + heave;
                if (right || left) { d[0] = -0.7f - value.getX(); d[2] = right ? 0.15f : -0.15f; }
            }
            case DrugPose.SEIZURE -> {
                // Stiff and shaking all over; each limb out of step with the others.
                float phase = part.hashCode() * 0.37f;
                float shake = 0.35f * (float) Math.sin(t * 45f + phase) + 0.2f * (float) Math.sin(t * 31f + 2f * phase);
                if (position) { if (part.equals("body")) d[1] = 0.03f * shake; return; }
                float amount = part.equals("body") ? 0.15f : 1f;
                d[0] = amount * shake + (right || left ? -0.4f : 0f);
                d[2] = amount * 0.5f * shake;
            }
            case DrugPose.SNIFF -> {
                // A hand up to the nose, head bowed to it, then snapping back.
                if (position) return;
                if (right) { d[0] = -1.9f - value.getX(); d[2] = 0.45f; }
                if (part.equals("head")) d[0] = p < 0.6f ? 0.35f : -0.3f;
            }
            case DrugPose.SMOKE, DrugPose.INHALE -> {
                // Hand to the mouth, a long drag, then head back to breathe out.
                if (position) return;
                if (right) { d[0] = (p < 0.6f ? -1.75f : -0.6f) - value.getX(); d[2] = 0.35f; }
                if (part.equals("head")) d[0] = p < 0.6f ? 0f : -0.35f;
            }
            case DrugPose.INJECT -> {
                // Left arm held out, the right hand at its crook, eyes on the vein.
                if (position) return;
                if (left) { d[0] = -1.2f - value.getX(); d[1] = 0.3f; }
                if (right) { d[0] = -1.1f - value.getX(); d[1] = -0.5f; d[2] = -0.2f; }
                if (part.equals("head")) { d[0] = 0.45f; d[1] = -0.25f; }
            }
            case DrugPose.COLLAPSE -> {
                // Knees give, then face down on the ground, limbs slack. The whole body turns
                // about the hips (0.7 blocks up), so it also drops to lie on the ground.
                // ponytail: lies flat on open ground; on slopes or slabs it clips a little.
                float down = Mth.clamp(p * 6f, 0f, 1f);
                down *= down;
                if (position) { if (part.equals("body")) d[1] = -0.55f * down; return; }
                if (part.equals("body")) d[0] = 1.5f * down;
                if (part.equals("head")) d[2] = 0.4f * down;
                if (right || left) d[2] = (right ? 0.5f : -0.5f) * down;
            }
            default -> {}
        }
    }
}
