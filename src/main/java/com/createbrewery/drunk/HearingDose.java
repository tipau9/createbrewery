package com.createbrewery.drunk;

import com.createbrewery.CreateBrewery;
import com.createbrewery.compat.CuriosCompat;
import com.createbrewery.effect.ModEffects;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The bass a client heard in the last second, sent to the server, which keeps the player's dose
 * ({@link ModAttachments#HEARING}) and rings their ears once it is past the limit. Only the client
 * knows the music, so it measures; the server bounds what it is told, heals the dose and owns the
 * effect. Earplugs (checked here, not trusted from the client) keep every bit of it out.
 */
public record HearingDose(float taken) implements CustomPacketPayload {
    public static final Type<HearingDose> TYPE = new Type<>(CreateBrewery.ID("hearing_dose"));
    public static final StreamCodec<ByteBuf, HearingDose> CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, HearingDose::taken, HearingDose::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(TYPE, CODEC, HearingDose::handle);
    }

    private static void handle(HearingDose d, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !Float.isFinite(d.taken) || CuriosCompat.earplugs(player)) return;
        // Healing is the tick's job; here only what was taken, bounded.
        float dose = player.getData(ModAttachments.HEARING) + Math.max(0f, Math.min(Hearing.MAX_PER_SECOND, d.taken));
        if (dose >= Hearing.LIMIT) {
            player.addEffect(new MobEffectInstance(ModEffects.TINNITUS, Hearing.TINNITUS_TICKS, 0, false, false, true));
            // Half spent: staying on means it comes back sooner.
            dose = Hearing.LIMIT / 2;
        }
        player.setData(ModAttachments.HEARING, dose);
    }

    /** Once a second the ears heal a little. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !player.hasData(ModAttachments.HEARING)) return;
        float dose = Hearing.step(player.getData(ModAttachments.HEARING), 0f);
        if (dose > 0f) player.setData(ModAttachments.HEARING, dose);
        else player.removeData(ModAttachments.HEARING);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
