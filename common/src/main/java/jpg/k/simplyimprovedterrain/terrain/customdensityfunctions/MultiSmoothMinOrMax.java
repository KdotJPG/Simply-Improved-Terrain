package jpg.k.simplyimprovedterrain.terrain.customdensityfunctions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jpg.k.simplyimprovedterrain.math.ExtraMath;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public record MultiSmoothMinOrMax(List<MultiSmoothMinOrMax.Entry> entries, boolean isMin, double minValue, double maxValue, double maxSmoothingFactor) implements DensityFunction.SimpleFunction {

    private static final int INDEX_VALUE = 0;
    private static final int INDEX_SMOOTHING_FACTOR = 1;
    private static final int INDEX_WEIGHT = 2;
    private static final int STRIDE = 3;

    private static ThreadLocal<double[]> dataThreadLocal = new ThreadLocal<double[]>();
    private static final double[] getDataArray(int length) {
        double[] data = dataThreadLocal.get();
        if (data == null || data.length < length) {
            data = new double[length];
            dataThreadLocal.set(data);
        }
        return data;
    }

    public static MultiSmoothMinOrMax create(List<MultiSmoothMinOrMax.Entry> entries, boolean isMin) {
        double maxSmoothingFactor = entries.stream().mapToDouble(Entry::smoothingFactor).max().orElse(0);
        double minValue = compute(entries, isMin, DensityFunction::minValue, maxSmoothingFactor);
        double maxValue = compute(entries, isMin, DensityFunction::maxValue, maxSmoothingFactor);
        return new MultiSmoothMinOrMax(entries, isMin, minValue, maxValue, maxSmoothingFactor);
    }

    public double compute(FunctionContext functionContext) {
        return compute(entries, isMin, function -> function.compute(functionContext), maxSmoothingFactor);
    }

    private static double compute(List<MultiSmoothMinOrMax.Entry> entries, boolean isMin, ToDoubleFunction<DensityFunction> valueGetter, double maxSmoothingFactor) {

        if (entries.size() != 2) {
            throw new IllegalArgumentException("MultiSmoothMinOrMax does not support numbers of entries other than two yet.");
        } else {
            double a = valueGetter.applyAsDouble(entries.get(0).function());
            double b = valueGetter.applyAsDouble(entries.get(1).function());
            double smoothingFactor = entries.get(0).smoothingFactor() + entries.get(1).smoothingFactor();
            if (isMin) smoothingFactor = -smoothingFactor;

            return ExtraMath.smoothMax(a, b, smoothingFactor, 1 / smoothingFactor);
        }

        // TODO debug and reenable.

        /*int dataSizeTotal = entries.size() * STRIDE;
        double[] data = getDataArray(dataSizeTotal);
        double unsmoothedValue = Double.NEGATIVE_INFINITY;

        // Evaluate all functions and get the proper max value.
        {
            int iData = 0;
            for (Entry entry : entries) {
                double valueHere = valueGetter.applyAsDouble(entry.function());
                if (isMin) valueHere = -valueHere;
                data[iData + INDEX_VALUE] = valueHere;
                data[iData + INDEX_SMOOTHING_FACTOR] = entry.smoothingFactor();
                unsmoothedValue = Math.max(unsmoothedValue, valueHere);
                iData += STRIDE;
            }
        }

        // Whittle down the values to those in range to affect the result.
        // This is both for efficiency and to prevent infinities/NaNs from messing up the result.
        for (int i = 0; i < dataSizeTotal; i += STRIDE) {
            double valueHere = data[i + INDEX_VALUE];
            double smoothingFactorHere = data[i + INDEX_SMOOTHING_FACTOR];
            if (!(valueHere + maxSmoothingFactor > unsmoothedValue)) {
                dataSizeTotal -= STRIDE;
                data[i + INDEX_VALUE] = data[dataSizeTotal + INDEX_VALUE];
                data[i + INDEX_SMOOTHING_FACTOR] = data[dataSizeTotal + INDEX_SMOOTHING_FACTOR];
                i -= STRIDE;
                continue;
            }
            data[i + INDEX_WEIGHT] = 1.0;
        }

        // If there aren't multiple values contributing to the result, the result will be the true min/max.
        if (dataSizeTotal <= STRIDE) return isMin ? -unsmoothedValue : unsmoothedValue;

        // Yeah, it's θ(N²).
        // Maybe some day I'll find a linear-time order-independent polynomial-based smooth min/max.
        for (int i = 0; i < dataSizeTotal; i += STRIDE) {
            double valueI = data[i + INDEX_VALUE];
            double smoothingFactorI = data[i + INDEX_SMOOTHING_FACTOR];
            for (int j = i + STRIDE; j < dataSizeTotal; j += STRIDE) {
                double valueJ = data[j + INDEX_VALUE];
                double smoothingFactorJ = data[j + INDEX_SMOOTHING_FACTOR];
                double mixSlide = CustomMath.clampedFadeWithSymmetricDomainAndUnitRange((valueJ - valueI) / (smoothingFactorJ + smoothingFactorI));
                data[i + INDEX_WEIGHT] *= 1.0 - mixSlide;
                data[j + INDEX_WEIGHT] *= mixSlide;
            }
        }

        // Find total weight for normalization
        double totalWeight = 0.0;
        for (int i = 0; i < dataSizeTotal; i += STRIDE) {
            totalWeight += data[i + INDEX_WEIGHT];
        }
        double weightNormalizer = 1.0 / totalWeight;

        // Apply weights to values.
        double value = 0.0;
        for (int i = 0; i < dataSizeTotal; i += STRIDE) {
            double normalizedWeightHere = data[i + INDEX_WEIGHT] * weightNormalizer;
            double valueHere = data[i + INDEX_VALUE];
            double smoothingFactorHere = data[i + INDEX_SMOOTHING_FACTOR];
            double monotonicityCorrection = smoothingFactorHere * (1.0 - normalizedWeightHere);
            value += normalizedWeightHere * (valueHere + monotonicityCorrection);
        }

        return (isMin ? -value : value);*/
    }


    // TODO
    /*public void fillArray(double[] ds, ContextProvider contextProvider) {
        throw new NotImplementedException();
    }*/

    public DensityFunction mapAll(Visitor visitor) {
        List<MultiSmoothMinOrMax.Entry> newEntries = new ArrayList<>(this.entries.size());
        for (MultiSmoothMinOrMax.Entry entry : this.entries) {
            newEntries.add(new MultiSmoothMinOrMax.Entry(entry.function().mapAll(visitor), entry.smoothingFactor));
        }
        return visitor.apply(MultiSmoothMinOrMax.create(newEntries, this.isMin));
    }

    public static final String SERIALIZED_NAME = "multi_smooth_min_or_max";
    public static final KeyDispatchDataCodec<MultiSmoothMinOrMax> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(
                Codec.list(MultiSmoothMinOrMax.Entry.CODEC.codec()).fieldOf("entries").forGetter(MultiSmoothMinOrMax::entries),
                Codec.BOOL.fieldOf("is_min").forGetter(MultiSmoothMinOrMax::isMin)
        ).apply(instance, MultiSmoothMinOrMax::create);
    }));

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    public record Entry(DensityFunction function, double smoothingFactor) {
        public static final KeyDispatchDataCodec<MultiSmoothMinOrMax.Entry> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("function").forGetter(MultiSmoothMinOrMax.Entry::function),
                    Codec.doubleRange(0, Double.POSITIVE_INFINITY).fieldOf("smoothing_factor").forGetter(MultiSmoothMinOrMax.Entry::smoothingFactor)
            ).apply(instance, MultiSmoothMinOrMax.Entry::new);
        }));
    }
}
