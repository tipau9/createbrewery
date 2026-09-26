package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugPose;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Works out what your own body is doing on drugs and tells the server, so others see it (and you
 * do, in third person or out of your body in a K-hole). Drawn by {@link PoseAnimation} through
 * playerAnimator, when that is installed.
 */
final class PoseClient {
    private PoseClient() {}

    private static int sentKind = -1, sentAmount, nextSend;

    static void init() {
        if (ModList.get().isLoaded("playeranimator")) PoseAnimation.register();
    }

    static void tick(LocalPlayer player) {
        float[] strength = new float[5];
        strength[DrugPose.DANCE] = RollClient.beat * DrunkClient.rolling;
        strength[DrugPose.NOD] = NodClient.drooped / 30f;
        strength[DrugPose.SLUMP] = KetaClient.hole;
        strength[DrugPose.LAUGH] = WeedClient.laugh;
        int kind = DrugPose.NONE;
        for (int i = 1; i < strength.length; i++) if (strength[i] > 0.05f && strength[i] > strength[kind]) kind = i;
        int amount = kind == DrugPose.NONE ? 0 : Math.round(Math.min(1f, strength[kind]) * 255f);
        DrugPose pose = new DrugPose(player.getId(), (byte) kind, (byte) amount);
        DrugPose.SEEN.put(player.getId(), pose);
        // When it changed noticeably (at most four times a second), and every two seconds anyway
        // while posing, so someone who only just came near sees it too.
        boolean changed = kind != sentKind || Math.abs(amount - sentAmount) >= 24;
        if ((changed && player.tickCount >= nextSend || kind != DrugPose.NONE && player.tickCount >= nextSend + 35)
            && player.connection.hasChannel(DrugPose.TYPE)) {
            PacketDistributor.sendToServer(pose);
            sentKind = kind;
            sentAmount = amount;
            nextSend = player.tickCount + 5;
        }
    }
}
