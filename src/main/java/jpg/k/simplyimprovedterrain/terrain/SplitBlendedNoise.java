package jpg.k.simplyimprovedterrain.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinBlendedNoise;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinPerlinFractalNoise;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

import java.util.Objects;

import static net.minecraft.core.Registry.register;

public class SplitBlendedNoise {

    // Adapted from Vanilla
    private static final double MAIN_NOISE_DIVISOR = 20.0;
    private static final double MAIN_NOISE_MULTIPLIER = 1.0 / MAIN_NOISE_DIVISOR;
    private static final double FINAL_NOISE_DIVISOR = 65536.0;
    private static final double FINAL_NOISE_MULTIPLIER = 1.0 / FINAL_NOISE_DIVISOR;

    // Need to evaluate the noise slightly past the true useful range, so interpolation picks up what it needs to.
    // TODO update docs. This is use for range choice.
    private static final double NOISE_EVALUTION_BUFFER = 1.0;
    public static final double MAIN_NOISE_MAX_TO_EVALUATE_1ST_HALF = (NOISE_EVALUTION_BUFFER + 0.5) * MAIN_NOISE_DIVISOR;
    public static final double MAIN_NOISE_MIN_TO_EVALUATE_2ND_HALF = (-NOISE_EVALUTION_BUFFER - 0.5) * MAIN_NOISE_DIVISOR;

    public static class BlendedNoiseCombine implements DensityFunction.SimpleFunction {
        private final DensityFunction minLimitNoise;
        private final DensityFunction maxLimitNoise;
        private final DensityFunction mainNoise;
        private double minValue, maxValue;

        public static BlendedNoiseCombine create(DensityFunction minLimitNoise, DensityFunction maxLimitNoise, DensityFunction mainNoise) {
            return new BlendedNoiseCombine(minLimitNoise, maxLimitNoise, mainNoise);
        }

        protected BlendedNoiseCombine(DensityFunction minLimitNoise, DensityFunction maxLimitNoise, DensityFunction mainNoise) {
            this.minLimitNoise = minLimitNoise;
            this.maxLimitNoise = maxLimitNoise;
            this.mainNoise = mainNoise;
            this.minValue = Math.min(this.minLimitNoise.minValue(), this.maxLimitNoise.minValue());
            this.maxValue = Math.max(this.minLimitNoise.maxValue(), this.maxLimitNoise.maxValue());
        }

        @Override
        public double compute(FunctionContext functionContext) {
            return Mth.clampedLerp(
                    this.minLimitNoise.compute(functionContext),
                    this.maxLimitNoise.compute(functionContext),
                    this.mainNoise.compute(functionContext) * MAIN_NOISE_MULTIPLIER + 0.5
            ) * FINAL_NOISE_MULTIPLIER;
        }

        @Override
        public double minValue() {
            return this.minValue;
        }

        @Override
        public double maxValue() {
            return this.maxValue;
        }

        public DensityFunction minLimitNoise() {
            return minLimitNoise;
        }

        public DensityFunction maxLimitNoise() {
            return maxLimitNoise;
        }

        public DensityFunction mainNoise() {
            return mainNoise;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlendedNoiseCombine that = (BlendedNoiseCombine) o;
            return minLimitNoise.equals(that.minLimitNoise) && maxLimitNoise.equals(that.maxLimitNoise) && mainNoise.equals(that.mainNoise);
        }

        @Override
        public int hashCode() {
            return Objects.hash(minLimitNoise, maxLimitNoise, mainNoise);
        }

        public static final KeyDispatchDataCodec<BlendedNoiseCombine> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("min_limit_noise").forGetter(BlendedNoiseCombine::minLimitNoise),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("max_limit_noise").forGetter(BlendedNoiseCombine::maxLimitNoise),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("main_noise").forGetter(BlendedNoiseCombine::mainNoise)
            ).apply(instance, BlendedNoiseCombine::new);
        }));
        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new BlendedNoiseCombine(minLimitNoise.mapAll(visitor), maxLimitNoise.mapAll(visitor), mainNoise.mapAll(visitor)));
        }
    }

    public static class BlendedNoisePart implements DensityFunction.SimpleFunction {

        // It's up to another mixin to domain-rotate this and reimplement the shelves. Always domain-rotate your Perlin, folks.
        // Also PerlinNoise.class is fractal noise on Perlin. ImprovedNoise.class is Perlin.
        private final PerlinNoise noise;

        private final double xzMultiplier, yMultiplier, yShelfScale;
        private final double maxValue;
        private final int octaveCount;
        private final BlendedNoise blendedNoise;
        private final Type type;

        public enum Type implements StringRepresentable {
            MIN_LIMIT_NOISE("min_limit_noise"),
            MAX_LIMIT_NOISE("max_limit_noise"),
            MAIN_NOISE("main_noise");

            private final String name;
            Type(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }

            public final KeyDispatchDataCodec<BlendedNoisePart> codec = KeyDispatchDataCodec.of(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("blended_noise").xmap((densityFunction) -> {
                return BlendedNoisePart.create(this, densityFunction);
            }, BlendedNoisePart::blendedNoise));
        }

        public static BlendedNoisePart create(Type type, DensityFunction blendedNoise) {
            return new BlendedNoisePart(type, blendedNoise);
        }

        protected BlendedNoisePart(Type type, DensityFunction blendedNoise) {
            this(type, blendedNoise instanceof BlendedNoise ?
                    (BlendedNoise)blendedNoise :
                    BlendedNoise.createUnseeded(0.25D, 0.25D, 80.0D, 160.0D, 4.0D) // Needs to be a valid config for some intermediate stage.
            );
        }

        protected BlendedNoisePart(Type type, BlendedNoise blendedNoise) {
            IMixinBlendedNoise mixinBlendedNoise = (IMixinBlendedNoise)blendedNoise;
            this.blendedNoise = blendedNoise;
            this.type = type;
            switch (type) {
                case MIN_LIMIT_NOISE:
                    this.noise = mixinBlendedNoise.minLimitNoise();
                    this.xzMultiplier = mixinBlendedNoise.xzMultiplier();
                    this.yMultiplier = mixinBlendedNoise.yMultiplier();
                    this.yShelfScale = mixinBlendedNoise.yMultiplier() * mixinBlendedNoise.smearScaleMultiplier();
                    this.maxValue = noise.maxBrokenValue(this.yMultiplier);
                    this.octaveCount = ((IMixinPerlinFractalNoise)this.noise).octaveCount();
                    break;
                case MAX_LIMIT_NOISE:
                    this.noise = mixinBlendedNoise.maxLimitNoise();
                    this.xzMultiplier = mixinBlendedNoise.xzMultiplier();
                    this.yMultiplier = mixinBlendedNoise.yMultiplier();
                    this.yShelfScale = mixinBlendedNoise.yMultiplier() * mixinBlendedNoise.smearScaleMultiplier();
                    this.maxValue = noise.maxBrokenValue(this.yMultiplier);
                    this.octaveCount = ((IMixinPerlinFractalNoise)this.noise).octaveCount();
                    break;
                default:
                case MAIN_NOISE:
                    this.noise = mixinBlendedNoise.mainNoise();
                    this.xzMultiplier = mixinBlendedNoise.xzMultiplier() / mixinBlendedNoise.xzFactor();
                    this.yMultiplier = mixinBlendedNoise.yMultiplier() / mixinBlendedNoise.yFactor();
                    this.yShelfScale = mixinBlendedNoise.yMultiplier() * mixinBlendedNoise.smearScaleMultiplier() / mixinBlendedNoise.yFactor();
                    this.maxValue = noise.maxBrokenValue(this.yMultiplier);
                    this.octaveCount = ((IMixinPerlinFractalNoise)this.noise).octaveCount();
                    break;
            }
        }

        @Override
        public double compute(FunctionContext functionContext) {
            int x = functionContext.blockX();
            int y = functionContext.blockY();
            int z = functionContext.blockZ();

            double value = 0;
            double relativeFrequency = 1.0;
            for (int octaveIndex = 0; octaveIndex < this.octaveCount; octaveIndex++) {
                ImprovedNoise improvedNoise = this.noise.getOctaveNoise(octaveIndex);
                if (improvedNoise != null) {
                    value += improvedNoise.noise(
                            PerlinNoise.wrap(x * this.xzMultiplier * relativeFrequency),
                            PerlinNoise.wrap(y * this.yMultiplier * relativeFrequency),
                            PerlinNoise.wrap(z * this.xzMultiplier * relativeFrequency),
                            this.yShelfScale * relativeFrequency,
                            y * this.yMultiplier * relativeFrequency) / relativeFrequency;
                }
                relativeFrequency *= 0.5;
            }

            return value;
        }

        @Override
        public double minValue() {
            return -this.maxValue;
        }

        @Override
        public double maxValue() {
            return this.maxValue;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type.codec;
        }

        public Type type() { return this.type; }

        public BlendedNoise blendedNoise() { return this.blendedNoise; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlendedNoisePart that = (BlendedNoisePart) o;
            return Double.compare(that.xzMultiplier, xzMultiplier) == 0 && Double.compare(that.yMultiplier, yMultiplier) == 0 && Double.compare(that.yShelfScale, yShelfScale) == 0 && Double.compare(that.maxValue, maxValue) == 0 && noise.equals(that.noise) && blendedNoise.equals(that.blendedNoise) && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(noise, xzMultiplier, yMultiplier, yShelfScale, maxValue, blendedNoise, type);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            DensityFunction mappedBlendedNoiseFunction = this.blendedNoise.mapAll(visitor);
            BlendedNoise mappedBlendedNoise = mappedBlendedNoiseFunction instanceof BlendedNoise ?
                    (BlendedNoise)mappedBlendedNoiseFunction : this.blendedNoise;
            return visitor.apply(new BlendedNoisePart(this.type, mappedBlendedNoise));
        }
    }

}
