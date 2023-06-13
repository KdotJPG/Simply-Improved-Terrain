package jpg.k.simplyimprovedterrain.terrain.customdensityfunctions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.Objects;

public final class SmoothRangeChoice implements DensityFunction {
    private final DensityFunction input;
    private final double minInclusive, maxExclusive;
    private final double smoothingFactor, inverseSquaredSmoothingFactor;
    private final DensityFunction whenInRange, whenOutOfRange;
    private final double minValue, maxValue;

    public static SmoothRangeChoice create(DensityFunction input, double minInclusive, double maxExclusive, double smoothingFactor, DensityFunction whenInRange, DensityFunction whenOutOfRange) {
        return new SmoothRangeChoice(input, minInclusive, maxExclusive, smoothingFactor, whenInRange, whenOutOfRange);
    }

    private SmoothRangeChoice(DensityFunction input, double minInclusive, double maxExclusive, double smoothingFactor, DensityFunction whenInRange, DensityFunction whenOutOfRange) {
        this.input = input;
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.smoothingFactor = smoothingFactor;
        this.inverseSquaredSmoothingFactor = 1.0 / (smoothingFactor * smoothingFactor);
        this.whenInRange = whenInRange;
        this.whenOutOfRange = whenOutOfRange;
        this.minValue = Math.min(whenInRange.minValue(), whenOutOfRange.minValue());
        this.maxValue = Math.max(whenInRange.maxValue(), whenOutOfRange.maxValue());
    }

    public double compute(FunctionContext functionContext) {
        double inputValue = this.input.compute(functionContext);
        return compute(functionContext, inputValue);
    }

    public void fillArray(double[] ds, ContextProvider contextProvider) {
        this.input.fillArray(ds, contextProvider);
        for(int i = 0; i < ds.length; ++i) {
            double inputValue = ds[i];
            ds[i] = compute(contextProvider.forIndex(i), inputValue);
        }
    }

    private double compute(FunctionContext functionContext, double inputValue) {
        if (inputValue < this.minInclusive || inputValue >= this.maxExclusive)
            return this.whenOutOfRange.compute(functionContext);
        else if (inputValue >= this.minInclusive + smoothingFactor || inputValue < this.maxExclusive - smoothingFactor)
            return this.whenInRange.compute(functionContext);
        else {
            double whenInRangeValue = this.whenInRange.compute(functionContext);
            double whenOutOfRangeValue = this.whenOutOfRange.compute(functionContext);

            double slide = Math.min(inputValue - this.minInclusive, smoothingFactor)
                    * Math.min(inputValue - this.maxExclusive, smoothingFactor);
            slide *= this.inverseSquaredSmoothingFactor;
            slide = slide * slide * (3 - 2 * slide);

            return Mth.lerp(slide, whenOutOfRangeValue, whenInRangeValue);
        }
    }

    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new SmoothRangeChoice(this.input.mapAll(visitor), this.minInclusive, this.maxExclusive, this.smoothingFactor, this.whenInRange.mapAll(visitor), this.whenOutOfRange.mapAll(visitor)));
    }

    public DensityFunction input() {
        return input;
    }

    public double minInclusive() {
        return minInclusive;
    }

    public double maxExclusive() {
        return maxExclusive;
    }

    public double smoothingFactor() {
        return smoothingFactor;
    }

    public DensityFunction whenInRange() {
        return whenInRange;
    }

    public DensityFunction whenOutOfRange() {
        return whenOutOfRange;
    }

    public double minValue() {
        return this.minValue;
    }

    public double maxValue() {
        return this.maxValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmoothRangeChoice that = (SmoothRangeChoice) o;
        return Double.compare(that.minInclusive, minInclusive) == 0 && Double.compare(that.maxExclusive, maxExclusive) == 0 && Double.compare(that.smoothingFactor, smoothingFactor) == 0 && input.equals(that.input) && whenInRange.equals(that.whenInRange) && whenOutOfRange.equals(that.whenOutOfRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(input, minInclusive, maxExclusive, smoothingFactor, whenInRange, whenOutOfRange);
    }

    public static final String SERIALIZED_NAME = "smooth_range_choice";
    public static final MapCodec<SmoothRangeChoice> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("function").forGetter(SmoothRangeChoice::input),
                Codec.doubleRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).fieldOf("min_inclusive").forGetter(SmoothRangeChoice::minInclusive),
                Codec.doubleRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).fieldOf("max_exclusive").forGetter(SmoothRangeChoice::maxExclusive),
                Codec.doubleRange(0, Double.POSITIVE_INFINITY).fieldOf("smoothing_factor").forGetter(SmoothRangeChoice::smoothingFactor),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_in_range").forGetter(SmoothRangeChoice::whenInRange),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_out_of_range").forGetter(SmoothRangeChoice::whenOutOfRange)
        ).apply(instance, SmoothRangeChoice::new);
    });
    public static final KeyDispatchDataCodec<SmoothRangeChoice> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
