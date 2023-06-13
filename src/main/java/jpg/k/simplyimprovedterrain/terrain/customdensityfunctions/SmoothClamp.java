package jpg.k.simplyimprovedterrain.terrain.customdensityfunctions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jpg.k.simplyimprovedterrain.math.ExtraMath;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record SmoothClamp(DensityFunction input, double clampMinValue, double clampMaxValue, double minValue, double maxValue, double smoothingFactor, double inverseSmoothingFactor)
        implements DensityFunction.SimpleFunction {

    public static SmoothClamp create(DensityFunction input, double clampMinValue, double clampMaxValue, double smoothingFactor) {
        double minValue = Math.min(clampMinValue, input.minValue());
        double maxValue = Math.max(clampMaxValue, input.maxValue());
        return new SmoothClamp(input, clampMinValue, clampMaxValue, minValue, maxValue, smoothingFactor, 1.0 / smoothingFactor);
    }

    public double compute(FunctionContext context) {
        double value = input.compute(context);
        return compute(value, clampMinValue, clampMaxValue, inverseSmoothingFactor);
    }

    public static double compute(double value, double clampMinValue, double clampMaxValue, double inverseSmoothingFactor) {
        double fadeInFromMin = ExtraMath.clampedFadeWithSymmetricDomainAndUnitRange((value - clampMinValue) * inverseSmoothingFactor);
        double fadeInFromMax = ExtraMath.clampedFadeWithSymmetricDomainAndUnitRange((clampMaxValue - value) * inverseSmoothingFactor);
        return fadeInFromMin * fadeInFromMax * value + (1 - fadeInFromMin) * clampMinValue + (1 - fadeInFromMax) * clampMaxValue;
    }

    public static final String SERIALIZED_NAME = "smooth_clamp";
    public static final KeyDispatchDataCodec<SmoothClamp> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(SmoothClamp::input),
                Codec.doubleRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).fieldOf("min").forGetter(SmoothClamp::clampMinValue),
                Codec.doubleRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).fieldOf("max").forGetter(SmoothClamp::clampMaxValue),
                Codec.doubleRange(0, Double.POSITIVE_INFINITY).fieldOf("smoothing_factor").forGetter(SmoothClamp::smoothingFactor)
        ).apply(instance, SmoothClamp::create);
    }));

    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
