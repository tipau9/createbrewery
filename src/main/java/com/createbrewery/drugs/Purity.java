package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Street drugs are never what they say: every batch (stack) is cut or strong, and heroin or a
 * pressed Xanax bar may hold fentanyl. Nobody knows until it is tested ({@link TestKitItem}) or
 * taken. Rolled on the server when a stack is first used or tested, so untested stacks still
 * stack.
 *
 * @param strength how many normal doses one of these is, on average (random rounding, see
 *                 {@link Pharmacology#doses})
 * @param fentanyl laced: far stronger on the breath than anything it looks like
 * @param tested   whether a test kit has shown what is in it
 */
public record Purity(float strength, boolean fentanyl, boolean tested) {
    public static final Codec<Purity> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.FLOAT.fieldOf("strength").forGetter(Purity::strength),
        Codec.BOOL.optionalFieldOf("fentanyl", false).forGetter(Purity::fentanyl),
        Codec.BOOL.optionalFieldOf("tested", false).forGetter(Purity::tested)
    ).apply(i, Purity::new));
    public static final StreamCodec<ByteBuf, Purity> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, Purity::strength, ByteBufCodecs.BOOL, Purity::fentanyl, ByteBufCodecs.BOOL, Purity::tested, Purity::new);

    private static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateBrewery.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Purity>> PURITY =
        COMPONENTS.registerComponentType("purity", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC));

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    /** This stack's purity, rolled and written onto it the first time (server only). */
    public static Purity of(ItemStack stack, DrugServer.Kind kind, RandomSource random) {
        Purity purity = stack.get(PURITY.get());
        if (purity == null) {
            purity = roll(kind, random);
            stack.set(PURITY.get(), purity);
        }
        return purity;
    }

    /**
     * What street batches are like, in normal doses: Koks mostly cut, pills anything from weak to
     * a dangerous "super pill", mushrooms as varied as nature, pharmacy-made things steady.
     */
    static Purity roll(DrugServer.Kind kind, RandomSource random) {
        float[] range = switch (kind) {
            case COKE -> random.nextFloat() < 0.05f ? new float[] {1.3f, 1.6f} : new float[] {0.25f, 1.0f};
            case KETA -> new float[] {0.6f, 1.2f};
            case WEED -> new float[] {0.7f, 1.4f};
            case LSD -> new float[] {0.6f, 1.5f};
            case SHROOMS -> new float[] {0.5f, 1.8f};
            case MESCALINE -> new float[] {0.7f, 1.3f};
            case DMT -> new float[] {0.8f, 1.2f};
            case MDMA -> new float[] {0.6f, 1.9f};
            case METH -> new float[] {0.7f, 1.4f};
            case HEROIN -> new float[] {0.4f, 1.2f};
            case XANAX -> new float[] {0.9f, 1.0f};
            default -> new float[] {1f, 1f};
        };
        float strength = range[0] + (range[1] - range[0]) * random.nextFloat();
        boolean fentanyl = kind == DrugServer.Kind.HEROIN && random.nextFloat() < 0.12f
            || kind == DrugServer.Kind.XANAX && random.nextFloat() < 0.08f;
        return new Purity(strength, fentanyl, false);
    }
}
