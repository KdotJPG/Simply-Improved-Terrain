package jpg.k.simplyimprovedterrain.terrain;

import com.mojang.datafixers.types.Func;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.apache.http.cookie.SM;

import java.util.Objects;

public class CustomMathDensityFunctions {

    public static class SmoothMin implements DensityFunction.SimpleFunction {
        private final DensityFunction inputA, inputB;
        private final double smoothingFactor, inverseSmoothingFactor;
        private final double minValue, maxValue;

        public static SmoothMin create(DensityFunction inputA, DensityFunction inputB, double smoothingFactor) {
            return new SmoothMin(inputA, inputB, smoothingFactor);
        }

        protected SmoothMin(DensityFunction inputA, DensityFunction inputB, double smoothingFactor) {
            this.inputA = inputA;
            this.inputB = inputB;
            this.smoothingFactor = smoothingFactor;
            this.inverseSmoothingFactor = 1.0 / smoothingFactor;
            this.minValue = Math.min(inputA.minValue(), inputB.minValue());
            this.maxValue = Math.min(
                    Math.min(inputA.maxValue(), inputB.maxValue()) + smoothingFactor, // TODO verify
                    Math.max(inputA.maxValue(), inputB.maxValue())
            );
        }

        @Override
        public double compute(FunctionContext functionContext) {
            // https://iquilezles.org/articles/smin/
            double a = inputA.compute(functionContext);
            double b = inputB.compute(functionContext);
            double delta = a - b;
            if (delta <= -smoothingFactor) return a;
            else if (delta >= smoothingFactor) return b;
            else {
                double h = smoothingFactor - Math.abs(delta);
                if (h <= 0) h = 0;
                else h *= inverseSmoothingFactor;
                return Math.min(a, b) - h * h * smoothingFactor * 0.25f;
            }
        }

        @Override
        public double minValue() {
            return minValue;
        }

        @Override
        public double maxValue() {
            return maxValue;
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new SmoothMin(this.inputA.mapAll(visitor), this.inputB.mapAll(visitor), this.smoothingFactor));
        }

        public DensityFunction inputA() {
            return inputA;
        }

        public DensityFunction inputB() {
            return inputB;
        }

        public double smoothingFactor() {
            return smoothingFactor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SmoothMin smoothMin = (SmoothMin) o;
            return Double.compare(smoothMin.smoothingFactor, smoothingFactor) == 0 && inputA.equals(smoothMin.inputA) && inputB.equals(smoothMin.inputB);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inputA, inputB, smoothingFactor);
        }

        public static final Codec<SmoothMin> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input_a").forGetter(SmoothMin::inputA),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input_b").forGetter(SmoothMin::inputB),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("smoothing_factor").forGetter(SmoothMin::smoothingFactor)
            ).apply(instance, SmoothMin::new);
        });
        @Override
        public Codec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    public static final class SmoothRangeChoice implements DensityFunction {
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
            return visitor.apply(new CustomMathDensityFunctions.SmoothRangeChoice(this.input.mapAll(visitor), this.minInclusive, this.maxExclusive, this.smoothingFactor, this.whenInRange.mapAll(visitor), this.whenOutOfRange.mapAll(visitor)));
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

        public static final MapCodec<CustomMathDensityFunctions.SmoothRangeChoice> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(CustomMathDensityFunctions.SmoothRangeChoice::input),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("min_inclusive").forGetter(CustomMathDensityFunctions.SmoothRangeChoice::minInclusive),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("max_exclusive").forGetter(CustomMathDensityFunctions.SmoothRangeChoice::maxExclusive),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("smoothing_factor").forGetter(CustomMathDensityFunctions.SmoothRangeChoice::smoothingFactor),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_in_range").forGetter(CustomMathDensityFunctions.SmoothRangeChoice::whenInRange),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_out_of_range").forGetter(CustomMathDensityFunctions.SmoothRangeChoice::whenOutOfRange)
            ).apply(instance, CustomMathDensityFunctions.SmoothRangeChoice::new);
        });
        public static final Codec<CustomMathDensityFunctions.SmoothRangeChoice> CODEC = DATA_CODEC.codec();
        public Codec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

}
