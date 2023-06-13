package jpg.k.simplyimprovedterrain.terrain.customdensityfunctions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jpg.k.simplyimprovedterrain.math.ExtraMath;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public record SmoothMapped(DensityFunctions.Mapped.Type type, DensityFunction input, double smoothingFactor, double inverseSmoothingFactor, double minValue, double maxValue)
        implements DensityFunction.SimpleFunction {

    public static SmoothMapped create(DensityFunctions.Mapped.Type type, DensityFunction input, double smoothingFactor) {
        double inputMinValue = input.minValue();
        double inputMaxValue = input.minValue();
        double minValueTransformed = compute(type, inputMinValue, smoothingFactor, 1.0 / smoothingFactor);
        double maxValueTransformed = compute(type, inputMaxValue, smoothingFactor, 1.0 / smoothingFactor);

        double minValue, maxValue;
        if (type == DensityFunctions.Mapped.Type.SQUARE) {
            minValue = Math.max(0.0, inputMinValue);
            maxValue = Math.max(minValueTransformed, maxValueTransformed);
        } else {
            minValue = minValueTransformed;
            maxValue = maxValueTransformed;
        }

        return new SmoothMapped(type, input, smoothingFactor, 1.0 / smoothingFactor, minValue, maxValue);
    }

    public static SmoothMapped create(String type, DensityFunction input, double smoothingFactor) {
        return create(DensityFunctions.Mapped.Type.valueOf(type), input, smoothingFactor);
    }

    public double compute(FunctionContext context) {
        double value = input.compute(context);
        return compute(type, value, smoothingFactor, inverseSmoothingFactor);
    }

    public static double compute(DensityFunctions.Mapped.Type type, double value, double smoothingFactor, double inverseSmoothingFactor) {
        return switch (type) {
            case ABS -> ExtraMath.smoothMax(-value, value, smoothingFactor, inverseSmoothingFactor);
            case SQUARE -> value * value; // Nothing to smooth
            case CUBE -> value * value * value; // Nothing to smooth
            case HALF_NEGATIVE -> ExtraMath.smoothMax(0.5 * value, value, smoothingFactor, inverseSmoothingFactor);
            case QUARTER_NEGATIVE -> ExtraMath.smoothMax(0.25 * value, value, smoothingFactor, inverseSmoothingFactor);
            case SQUEEZE -> {
                double t = SmoothClamp.compute(value, -1.0, 1.0, inverseSmoothingFactor);
                yield t * (1.0 / 2.0 + t * t * (-1.0 / 24.0));
            }
            default -> throw new IncompatibleClassChangeError();
        };
    }

    public static final String SERIALIZED_NAME = "smooth_mapped";
    public static final KeyDispatchDataCodec<SmoothMapped> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(
                Codec.STRING.fieldOf("type").forGetter((SmoothMapped f) -> f.type().toString()),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(SmoothMapped::input),
                Codec.doubleRange(0, Double.POSITIVE_INFINITY).fieldOf("smoothing_factor").forGetter(SmoothMapped::smoothingFactor)
        ).apply(instance, SmoothMapped::create);
    }));

    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
