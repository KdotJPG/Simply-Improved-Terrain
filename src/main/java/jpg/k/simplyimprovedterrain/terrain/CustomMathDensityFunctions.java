package jpg.k.simplyimprovedterrain.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

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

}
