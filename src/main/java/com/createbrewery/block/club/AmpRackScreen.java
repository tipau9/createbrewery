package com.createbrewery.block.club;

import com.createbrewery.drunk.DeckFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The amp rack: the crossover's corner and the sub and top gains, and what the rack drives. Every
 * move goes to the server (see {@link AmpControl}) and is heard here at once. Client only.
 */
public class AmpRackScreen extends Screen {
    private static final int W = 220, H = 128;

    private final BlockPos pos;
    private int left, top;
    private Slider crossover, sub, tops;

    private AmpRackScreen(BlockPos pos) {
        super(Component.translatable("block.createbrewery.amp_rack"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new AmpRackScreen(pos));
    }

    private AmpRackBlockEntity rack() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof AmpRackBlockEntity r ? r : null;
    }

    /** 60..200 Hz on a log scale, as a slider 0..1. */
    private static double hz(double slider) {
        return AmpRackBlockEntity.MIN_CROSSOVER * Math.pow(AmpRackBlockEntity.MAX_CROSSOVER / AmpRackBlockEntity.MIN_CROSSOVER, slider);
    }

    private static double slider(double hz) {
        return Math.log(hz / AmpRackBlockEntity.MIN_CROSSOVER) / Math.log(AmpRackBlockEntity.MAX_CROSSOVER / AmpRackBlockEntity.MIN_CROSSOVER);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        AmpRackBlockEntity r = rack();
        if (r == null) return;
        crossover = addRenderableWidget(new Slider(top + 20, AmpControl.CROSSOVER, slider(r.getCrossover())));
        sub = addRenderableWidget(new Slider(top + 44, AmpControl.SUB_GAIN, r.getSubGain()));
        tops = addRenderableWidget(new Slider(top + 68, AmpControl.TOP_GAIN, r.getTopGain()));
    }

    @Override
    public void tick() {
        AmpRackBlockEntity r = rack();
        if (r == null || crossover == null || minecraft.player == null || !minecraft.player.canInteractWithBlock(pos, 1.0)) {
            onClose();
            return;
        }
        crossover.flush();
        sub.flush();
        tops.flush();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + H, 0xE0101014);
        g.renderOutline(left, top, W, H, 0xFF3A3A48);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + W / 2, top + 6, 0xFFFFFF);
        AmpRackBlockEntity r = rack();
        if (r == null) return;
        // What the rack drives, and whether it splits at all.
        Component status;
        int color = 0xAAAAAA;
        BlockPos booth = r.getBooth();
        if (booth == null) {
            status = Component.translatable("createbrewery.amp.unlinked");
            color = 0xFF6060;
        } else {
            int subs = 0, speakers = 0;
            for (SpeakerBlockEntity s : SpeakerBlockEntity.linked(minecraft.level, booth)) {
                if (s instanceof SubwooferBlockEntity sw) {
                    if (sw.isActive()) subs++;
                } else if (!(s instanceof AmpRackBlockEntity)) {
                    speakers++;
                }
            }
            status = Component.translatable("createbrewery.amp.drives", speakers, subs);
            if (subs == 0) {
                status = Component.translatable("createbrewery.amp.no_subs", speakers);
                color = 0xFFB040;
            }
        }
        g.drawCenteredString(font, status, left + W / 2, top + 96, color);
        g.drawCenteredString(font, Component.translatable("createbrewery.amp.hint"), left + W / 2, top + 110, 0x666666);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A slider that sends at most once a tick and applies its value here straight away. */
    private class Slider extends AbstractSliderButton {
        private final byte action;
        private boolean dirty;

        Slider(int y, byte action, double value) {
            super(left + 10, y, W - 20, 20, Component.empty(), value);
            this.action = action;
            updateMessage();
        }

        private float wire() {
            return action == AmpControl.CROSSOVER ? (float) hz(value) : (float) value;
        }

        @Override
        protected void updateMessage() {
            setMessage(switch (action) {
                case AmpControl.CROSSOVER -> Component.translatable("createbrewery.amp.crossover", Math.round(hz(value)));
                case AmpControl.SUB_GAIN -> Component.translatable("createbrewery.amp.sub_gain", db((float) value));
                default -> Component.translatable("createbrewery.amp.top_gain", db((float) value));
            });
        }

        private static String db(float knob) {
            float g = DeckFx.eqGain(knob);
            return g <= 0.001f ? "-inf" : String.format("%+.1f", 20 * Math.log10(g));
        }

        @Override
        protected void applyValue() {
            dirty = true;
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            float v = wire();
            AmpControl.send(pos, action, v);
            AmpRackBlockEntity r = rack();
            if (r != null) AmpControl.apply(r, action, v);
        }
    }
}
