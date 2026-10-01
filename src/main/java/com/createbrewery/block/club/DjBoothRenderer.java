package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the AlphaTheta XDJ-AZ console:
 * - Spinning vinyl / jogwheels on Deck 1 and Deck 2 with glowing cue position markers
 * - Illuminated tilted 10.1" central screen with live glowing waveforms, BPM readouts, and model branding
 */
public class DjBoothRenderer implements BlockEntityRenderer<DjBoothBlockEntity> {

    private final ItemRenderer itemRenderer;
    private final Font font;

    public DjBoothRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
        this.font = context.getFont();
    }

    @Override
    public void render(DjBoothBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(DjBoothBlock.FACING);

        pose.pushPose();
        // Move to centre of the block
        pose.translate(0.5, 0.5, 0.5);

        // Rotate matching blockstate model orientation
        float yRot = switch (facing) {
            case EAST -> -90f;
            case SOUTH -> -180f;
            case WEST -> -270f;
            default -> 0f; // NORTH
        };
        pose.mulPose(Axis.YP.rotationDegrees(yRot));

        float gameTime = be.getLevel() != null ? be.getLevel().getGameTime() + partialTick : 0f;

        // ---------------------------------------------------- LEFT PLATTER (Deck 1 / Deck 3)
        int leftDeck = be.isPlaying(DjBoothBlockEntity.C) || be.getDisc(DjBoothBlockEntity.A).isEmpty() ? DjBoothBlockEntity.C : DjBoothBlockEntity.A;
        ItemStack discLeft = be.getDisc(leftDeck);
        if (!discLeft.isEmpty()) {
            pose.pushPose();
            pose.translate(-0.25, 0.395, 0.0);
            pose.mulPose(Axis.XP.rotationDegrees(90f));

            if (be.isPlaying(leftDeck)) {
                float spin = gameTime * 14.0f * be.getPitch(leftDeck);
                pose.mulPose(Axis.ZP.rotationDegrees(spin));
            }

            pose.scale(0.42f, 0.42f, 0.42f);
            itemRenderer.renderStatic(discLeft, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        // ---------------------------------------------------- RIGHT PLATTER (Deck 2 / Deck 4)
        int rightDeck = be.isPlaying(DjBoothBlockEntity.D) || be.getDisc(DjBoothBlockEntity.B).isEmpty() ? DjBoothBlockEntity.D : DjBoothBlockEntity.B;
        ItemStack discRight = be.getDisc(rightDeck);
        if (!discRight.isEmpty()) {
            pose.pushPose();
            pose.translate(0.25, 0.395, 0.0);
            pose.mulPose(Axis.XP.rotationDegrees(90f));

            if (be.isPlaying(rightDeck)) {
                float spin = gameTime * 14.0f * be.getPitch(rightDeck);
                pose.mulPose(Axis.ZP.rotationDegrees(spin));
            }

            pose.scale(0.42f, 0.42f, 0.42f);
            itemRenderer.renderStatic(discRight, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        // ---------------------------------------------------- XDJ-AZ TILTED 10.1" DISPLAY SCREEN
        renderTiltedScreen(be, pose, buffers, gameTime, leftDeck, rightDeck);

        pose.popPose();
    }

    private void renderTiltedScreen(DjBoothBlockEntity be, PoseStack pose, MultiBufferSource buffers, float gameTime, int leftDeck, int rightDeck) {
        pose.pushPose();

        // Origin matches screen_display rotation pivot in dj_booth.json: [8.0, 14.8, 2.0]
        pose.translate(0.0, 0.425, -0.375);
        pose.mulPose(Axis.XP.rotationDegrees(-22.5f));

        // Move to front face of the tilted monitor
        pose.translate(0.0, 0.14, 0.056);
        pose.scale(0.0055f, -0.0055f, 0.0055f);

        int fullBright = 0x00F000F0;

        // Top Model Badge
        String brand = "AlphaTheta XDJ-AZ";
        font.drawInBatch(brand, -font.width(brand) / 2f, -20, 0xFF88AAFF, false,
            pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, fullBright);

        // Left Deck Status
        boolean playLeft = be.isPlaying(leftDeck);
        double periodLeft = MusicPulse.beatPeriodAt(be.deckPos(leftDeck));
        String bpmLeft = playLeft && periodLeft > 0 ? String.format("%.1f", 60.0 / periodLeft) : "--";
        int colLeft = leftDeck == DjBoothBlockEntity.A ? 0xFF00E5FF : 0xFFA833FF;
        String statusLeft = (leftDeck == DjBoothBlockEntity.A ? "1: " : "3: ") + bpmLeft;
        font.drawInBatch(statusLeft, -32, -9, playLeft ? colLeft : 0xFF558899, false,
            pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, fullBright);

        // Right Deck Status
        boolean playRight = be.isPlaying(rightDeck);
        double periodRight = MusicPulse.beatPeriodAt(be.deckPos(rightDeck));
        String bpmRight = playRight && periodRight > 0 ? String.format("%.1f", 60.0 / periodRight) : "--";
        int colRight = rightDeck == DjBoothBlockEntity.B ? 0xFFFF9900 : 0xFF00FF66;
        String statusRight = (rightDeck == DjBoothBlockEntity.B ? "2: " : "4: ") + bpmRight;
        font.drawInBatch(statusRight, 2, -9, playRight ? colRight : 0xFF996644, false,
            pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, fullBright);

        // Decoded 3-Band Audio Waveforms on screen
        renderDeckWaveformSlice(pose, buffers, font, fullBright, be, leftDeck, playLeft, 1f, colLeft);
        renderDeckWaveformSlice(pose, buffers, font, fullBright, be, rightDeck, playRight, 11f, colRight);

        pose.popPose();
    }

    private void renderTiltedScreen(DjBoothBlockEntity be, PoseStack pose, MultiBufferSource buffers, float gameTime) {
        pose.pushPose();

        // Origin matches screen_display rotation pivot in dj_booth.json: [8.0, 14.8, 2.0]
        pose.translate(0.0, 0.425, -0.375);
        pose.mulPose(Axis.XP.rotationDegrees(-22.5f));

        // Move to front face of the tilted monitor
        pose.translate(0.0, 0.14, 0.056);
        pose.scale(0.0055f, -0.0055f, 0.0055f);

        int fullBright = 0x00F000F0;

        // Top Model Badge
        String brand = "AlphaTheta XDJ-AZ";
        font.drawInBatch(brand, -font.width(brand) / 2f, -20, 0xFF88AAFF, false,
            pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, fullBright);

        // Deck 1 Status (Cyan)
        boolean playA = be.isPlaying(DjBoothBlockEntity.A);
        double periodA = MusicPulse.beatPeriodAt(be.deckPos(DjBoothBlockEntity.A));
        String bpmA = playA && periodA > 0 ? String.format("%.1f", 60.0 / periodA) : "--";
        String statusA = "1: " + bpmA + " BPM";
        font.drawInBatch(statusA, -32, -9, playA ? 0xFF00E5FF : 0xFF558899, false,
            pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, fullBright);

        // Deck 2 Status (Orange)
        boolean playB = be.isPlaying(DjBoothBlockEntity.B);
        double periodB = MusicPulse.beatPeriodAt(be.deckPos(DjBoothBlockEntity.B));
        String bpmB = playB && periodB > 0 ? String.format("%.1f", 60.0 / periodB) : "--";
        String statusB = "2: " + bpmB + " BPM";
        font.drawInBatch(statusB, 2, -9, playB ? 0xFFFF9900 : 0xFF996644, false,
            pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, fullBright);

        // Decoded 3-Band Audio Waveforms on screen
        renderDeckWaveformSlice(pose, buffers, font, fullBright, be, DjBoothBlockEntity.A, playA, 1f, 0xFF00E5FF);
        renderDeckWaveformSlice(pose, buffers, font, fullBright, be, DjBoothBlockEntity.B, playB, 11f, 0xFFFF8800);

        pose.popPose();
    }

    private void renderDeckWaveformSlice(PoseStack pose, MultiBufferSource buffers, Font font, int fullBright,
                                         DjBoothBlockEntity be, int deck, boolean playing, float baseY, int defaultColor) {
        MusicPulse.Track track = MusicPulse.trackFor(be.getBlockPos(), deck);
        int bars = 16;
        for (int i = 0; i < bars; i++) {
            float x = -30f + i * 3.8f;
            float wave = 1.0f;
            int col = defaultColor;

            if (playing && track != null && track.rate > 0) {
                long sFrame = track.deckFrame + (long) ((i - bars / 2) * (track.rate * 0.08f));
                float[] slice = track.getWaveformSlice(sFrame);
                if (slice != null) {
                    float bass = slice[0];
                    float loud = slice[1];
                    float high = slice[2];
                    float kick = slice[3];
                    wave = Math.max(1.0f, loud * 5.0f + bass * 3.5f + kick * 2.0f);
                    // Pioneer 3-band colors
                    if (bass > 0.35f || kick > 0.4f) {
                        col = 0xFF00AAFF; // Electric Blue / Kick
                    } else if (loud > 0.3f) {
                        col = 0xFFFFAA00; // Amber / Mid
                    } else {
                        col = 0xFFFFFFFF; // White / High
                    }
                } else {
                    float pulse = MusicPulse.kickNear(be.getBlockPos());
                    wave = 2.0f + pulse * 2.0f;
                }
            } else if (!playing) {
                wave = 0.5f;
                col = 0xFF202835;
            }

            font.drawInBatch("·", x, baseY - wave / 2f, col, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, fullBright);
            if (wave > 2.2f) {
                font.drawInBatch("·", x, baseY + wave / 2f - 2f, col, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, fullBright);
            }
        }
    }
}
