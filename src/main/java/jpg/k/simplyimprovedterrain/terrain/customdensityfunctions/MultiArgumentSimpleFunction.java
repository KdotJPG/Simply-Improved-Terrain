package jpg.k.simplyimprovedterrain.terrain.customdensityfunctions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.List;
import java.util.stream.Collectors;

public record MultiArgumentSimpleFunction(DensityFunctions.TwoArgumentSimpleFunction.Type type, List<DensityFunction> arguments, double minValue, double maxValue) implements DensityFunction.SimpleFunction {

    public static DensityFunction createSimplfied(DensityFunctions.TwoArgumentSimpleFunction.Type type, List<DensityFunction> arguments) {
        return switch (arguments.size()) {
            case 0 -> DensityFunctions.constant(getIdentityForType(type));
            case 1 -> arguments.get(0);
            case 2 -> DensityFunctions.TwoArgumentSimpleFunction.create(type, arguments.get(0), arguments.get(1));
            default -> create(type, arguments);
        };
    }

    public static MultiArgumentSimpleFunction create(DensityFunctions.TwoArgumentSimpleFunction.Type type, List<DensityFunction> arguments) {
        double minValue, maxValue;
        minValue = maxValue = getIdentityForType(type);
        if (arguments.size() >= 2) {
            switch (type) {
                case MIN:
                    minValue = arguments.stream().mapToDouble(function -> function.minValue()).min().getAsDouble();
                    maxValue = arguments.stream().mapToDouble(function -> function.maxValue()).min().getAsDouble();
                    break;
                case MAX:
                    minValue = arguments.stream().mapToDouble(function -> function.minValue()).max().getAsDouble();
                    maxValue = arguments.stream().mapToDouble(function -> function.maxValue()).max().getAsDouble();
                    break;
                case ADD:
                    minValue = arguments.stream().mapToDouble(function -> function.minValue()).sum();
                    maxValue = arguments.stream().mapToDouble(function -> function.maxValue()).sum();
                    break;
                case MUL:
                    for (DensityFunction function : arguments) {
                        double minValueHere = function.minValue();
                        double maxValueHere = function.maxValue();
                        double minValueNow, maxValueNow;
                        if (minValue >= 0 && minValueHere >= 0) {
                            minValueNow = minValue * minValueHere;
                            maxValueNow = maxValue * maxValueHere;
                        } else if (maxValue <= 0 && maxValueHere <= 0) {
                            minValueNow = maxValue * maxValueHere;
                            maxValueNow = minValue * minValueHere;
                        } else {
                            minValueNow = Math.min(minValue * maxValueHere, maxValue * minValueHere);
                            maxValueNow = Math.max(minValue * minValueHere, maxValue * maxValueHere);
                        }
                        minValue = minValueNow;
                        maxValue = maxValueNow;
                    }
                    break;
            }
        }

        return new MultiArgumentSimpleFunction(type, arguments, minValue, maxValue);
    }

    public static MultiArgumentSimpleFunction create(String type, List<DensityFunction> arguments) {
        return create(DensityFunctions.TwoArgumentSimpleFunction.Type.valueOf(type), arguments);
    }

    public double compute(FunctionContext context) {
        double value = getIdentityForType(type);
        for (DensityFunction function : arguments) {
            value = compute(type, value, function.compute(context));
        }
        return value;
    }

    public MultiArgumentSimpleFunction mapAll(Visitor visitor) {
        return create(type, arguments.stream().map(function -> function.mapAll(visitor)).collect(Collectors.toList()));
    }

    public static final double compute(DensityFunctions.TwoArgumentSimpleFunction.Type type, double a, double b) {
        return switch (type) {
            case ADD -> a + b;
            case MUL -> a * b;
            case MAX -> Math.max(a, b);
            case MIN -> Math.min(a, b);
        };
    }

    public static final double getIdentityForType(DensityFunctions.TwoArgumentSimpleFunction.Type type) {
        return switch (type) {
            case ADD -> 0;
            case MUL -> 1;
            case MAX -> Double.NEGATIVE_INFINITY;
            case MIN -> Double.POSITIVE_INFINITY;
        };
    }

    public static final String SERIALIZED_NAME = "multi_arg_simple_function";
    public static final KeyDispatchDataCodec<MultiArgumentSimpleFunction> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(
                Codec.STRING.fieldOf("type").forGetter((MultiArgumentSimpleFunction f) -> f.type().toString()),
                Codec.list(DensityFunction.HOLDER_HELPER_CODEC).fieldOf("arguments").forGetter(MultiArgumentSimpleFunction::arguments)
        ).apply(instance, MultiArgumentSimpleFunction::create);
    }));

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
