package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.drunk.ModAttachments;
import com.createbrewery.effect.ModEffects;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;

/**
 * For testing: jump straight into any state. {@code /brewery test <droge> [dosen] [phase]} takes
 * the doses and then sets the clock of the high to the phase; {@code /brewery clear} ends
 * everything. Operators only.
 */
public final class TestCommand {
    private TestCommand() {}

    private static final Map<String, DrugServer.Kind> DRUGS = Map.ofEntries(
        Map.entry("koks", DrugServer.Kind.COKE), Map.entry("keta", DrugServer.Kind.KETA), Map.entry("weed", DrugServer.Kind.WEED),
        Map.entry("lsd", DrugServer.Kind.LSD), Map.entry("pilze", DrugServer.Kind.SHROOMS), Map.entry("meskalin", DrugServer.Kind.MESCALINE),
        Map.entry("dmt", DrugServer.Kind.DMT), Map.entry("mdma", DrugServer.Kind.MDMA), Map.entry("meth", DrugServer.Kind.METH),
        Map.entry("heroin", DrugServer.Kind.HEROIN), Map.entry("xanax", DrugServer.Kind.XANAX), Map.entry("lachgas", DrugServer.Kind.LACHGAS));
    private static final List<String> PHASES = List.of("comeup", "peak", "fade", "crack", "waiting", "beyond", "descent");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("brewery").requires(s -> s.hasPermission(2))
            .then(Commands.literal("clear").executes(c -> clear(c.getSource().getPlayerOrException(), c.getSource())))
            .then(Commands.literal("test")
                .then(Commands.argument("droge", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(
                        java.util.stream.Stream.concat(DRUGS.keySet().stream(), java.util.stream.Stream.of("alkohol")), b))
                    .executes(c -> test(c, 1, "peak"))
                    .then(Commands.argument("dosen", IntegerArgumentType.integer(1, 4))
                        .executes(c -> test(c, IntegerArgumentType.getInteger(c, "dosen"), "peak"))
                        .then(Commands.argument("phase", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(PHASES, b))
                            .executes(c -> test(c, IntegerArgumentType.getInteger(c, "dosen"),
                                StringArgumentType.getString(c, "phase"))))))));
    }

    private static int test(CommandContext<CommandSourceStack> c, int doses, String phase) throws CommandSyntaxException {
        Player player = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "droge");
        if (name.equals("alkohol")) {
            // Doses as steps of 0.8 per-mille: tipsy-merry, drunk, wasted, blackout.
            DrunkState s = DrunkServer.state(player);
            s.blood = 0.8f * doses;
            s.peak = Math.max(s.peak, s.blood);
            player.syncData(ModAttachments.DRUNK);
            c.getSource().sendSuccess(() -> Component.literal(String.format("Alkohol: %.1f ‰", 0.8f * doses)), false);
            debugLog(player);
            return 1;
        }
        DrugServer.Kind kind = DRUGS.get(name);
        if (kind == null || !PHASES.contains(phase)) {
            c.getSource().sendFailure(Component.literal("Droge: " + String.join(", ", DRUGS.keySet()) + ", alkohol; Phase: " + PHASES));
            return 0;
        }
        // A joint is six hits.
        int takes = kind == DrugServer.Kind.WEED ? doses * DrugServer.HITS_PER_JOINT : doses;
        for (int i = 0; i < takes; i++) DrugServer.take(player, kind);
        setPhase(player, high(kind), phase);
        c.getSource().sendSuccess(() -> Component.literal(name + " × " + doses + ", " + phase), false);
        debugLog(player);
        return 1;
    }

    /** The client writes what its drug vision does into its log for the next 90 seconds (DrugDebug). */
    private static void debugLog(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer p) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new DrugDebug(90));
        }
    }

    /** The effect that carries the high of each drug. */
    static Holder<MobEffect> high(DrugServer.Kind kind) {
        return switch (kind) {
            case COKE -> ModEffects.COKE_HIGH;
            case KETA -> ModEffects.KETA_HIGH;
            case WEED -> ModEffects.WEED_HIGH;
            case LSD -> ModEffects.LSD_TRIP;
            case SHROOMS -> ModEffects.SHROOM_TRIP;
            case MESCALINE -> ModEffects.MESCALINE_TRIP;
            case DMT -> ModEffects.BREAKTHROUGH;
            case MDMA -> ModEffects.ROLLING;
            case METH -> ModEffects.TWEAK;
            case HEROIN -> ModEffects.NOD;
            case XANAX -> ModEffects.CALM;
            case LACHGAS -> ModEffects.WAH;
        };
    }

    /** Sets the clock of the high to the middle of the phase (DrugEffect's come-up, hold and fade; DMT's journey). */
    public static void setPhase(Player player, Holder<MobEffect> effect, String phase) {
        MobEffectInstance now = player.getEffect(effect);
        if (now == null || !(effect.value() instanceof DrugEffect drug) || drug.total() <= 0) return;
        int total = drug.total();
        int remaining = switch (phase) {
            case "comeup" -> total - drug.onset() / 2;
            case "fade" -> drug.fade() / 2;
            // DMT's phases, as fractions of the journey (see DmtClient).
            case "crack" -> (int) (total * 0.95f);
            case "waiting" -> (int) (total * 0.8f);
            case "beyond" -> (int) (total * 0.5f);
            case "descent" -> (int) (total * 0.12f);
            default -> total - (drug.onset() + total - drug.fade()) / 2;
        };
        player.removeEffect(effect);
        player.addEffect(new MobEffectInstance(effect, Math.max(20, remaining), now.getAmplifier(), false, false, true));
    }

    private static int clear(Player player, CommandSourceStack source) {
        for (MobEffectInstance instance : List.copyOf(player.getActiveEffects())) {
            if (instance.getEffect().unwrapKey().map(k -> k.location().getNamespace().equals(CreateBrewery.MOD_ID)).orElse(false)) {
                player.removeEffect(instance.getEffect());
            }
        }
        DrunkState s = DrunkServer.state(player);
        s.blood = s.stomach = s.peak = s.dependence = s.breathTolerance = s.benzo = s.b12 = s.bladder = 0f;
        s.heat = s.water = 0f;
        s.awake = 0;
        player.syncData(ModAttachments.DRUNK);
        source.sendSuccess(() -> Component.literal("Alles weg. Nüchtern."), false);
        return 1;
    }
}
