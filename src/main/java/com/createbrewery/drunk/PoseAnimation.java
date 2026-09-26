package com.createbrewery.drunk;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drugs.DrugPose;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * A player's body on drugs, drawn through playerAnimator (only loaded when it is installed):
 * dancing with arms up on MDMA, the chin sinking to the chest on the nod (mostly the real pitch,
 * which NodClient already lowers; this only adds a little), slumped and lopsided in
 * a K-hole, doubled over with laughter when stoned. Off in first person.
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
    }

    @Override
    public boolean isActive() {
        return shown[DrugPose.DANCE] + shown[DrugPose.NOD] + shown[DrugPose.SLUMP] + shown[DrugPose.LAUGH] > 0.01f;
    }

    @Override
    public void setupAnim(float tickDelta) {}

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value) {
        float t = (player.tickCount + tickDelta) / 20f;
        float dance = shown[DrugPose.DANCE], nod = shown[DrugPose.NOD], slump = shown[DrugPose.SLUMP], laugh = shown[DrugPose.LAUGH];
        float beat = (float) Math.sin(t * BEAT);
        float x = 0f, y = 0f, z = 0f;
        if (type == TransformType.POSITION) {
            if (!part.equals("torso")) return value;
            // Bouncing on the beat; sagging in the K-hole.
            y = 1.5f * (0.5f + 0.5f * (float) Math.cos(t * BEAT)) * dance + 1.5f * slump;
        } else if (type == TransformType.ROTATION) {
            switch (part) {
                case "head" -> {
                    x = 0.25f * beat * dance + 0.15f * nod + 0.5f * slump + 0.12f * (float) Math.sin(t * 25f) * laugh;
                    z = 0.35f * slump;
                }
                case "body" -> {
                    x = 0.2f * nod + (0.35f + 0.08f * (float) Math.sin(t * 25f)) * laugh;
                    z = 0.12f * slump;
                }
                // Hands in the air, swaying in turn.
                case "rightArm" -> {
                    x = (-2.8f + 0.3f * beat - value.getX()) * dance + 0.2f * nod - 0.5f * laugh;
                    z = -0.3f * dance + 0.25f * slump;
                }
                case "leftArm" -> {
                    x = (-2.8f - 0.3f * beat - value.getX()) * dance + 0.2f * nod - 0.5f * laugh;
                    z = 0.3f * dance - 0.25f * slump;
                }
                case "rightLeg" -> x = 0.15f * beat * dance;
                case "leftLeg" -> x = -0.15f * beat * dance;
                default -> {
                    return value;
                }
            }
        } else {
            return value;
        }
        return new Vec3f(value.getX() + x, value.getY() + y, value.getZ() + z);
    }
}
