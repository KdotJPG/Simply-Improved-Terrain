package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.mixinapi.IMixinBlendedNoise;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlendedNoise.class)
public class MixinBlendedNoise implements IMixinBlendedNoise {

    @Shadow @Final private PerlinNoise minLimitNoise;
    @Shadow @Final private PerlinNoise maxLimitNoise;
    @Shadow @Final private PerlinNoise mainNoise;

    @Shadow @Final private double xzScale;
    @Shadow @Final private double yScale;
    @Shadow @Final private double xzMainScale;
    @Shadow @Final private double yMainScale;
    @Shadow @Final private int cellWidth;
    @Shadow @Final private int cellHeight;

    /**
     * @author K.jpg
     * @reason true divisiom
     */
    @Overwrite
    public double compute(DensityFunction.FunctionContext functionContext) {

        // TODO inject constructor and set inverseWidth inverseHeight so we can use cheaper multiplication
        double i = functionContext.blockX() / (double)cellWidth;
        double j = functionContext.blockY() / (double)cellHeight;
        double k = functionContext.blockZ() / (double)cellWidth;

        double d = 0.0D;
        double e = 0.0D;
        double f = 0.0D;
        boolean bl = true;
        double g = 1.0D;

        for(int l = 0; l < 8; ++l) {
            ImprovedNoise improvedNoise = this.mainNoise.getOctaveNoise(l);
            if (improvedNoise != null) {
                f += improvedNoise.noise(PerlinNoise.wrap((double)i * this.xzMainScale * g), PerlinNoise.wrap((double)j * this.yMainScale * g), PerlinNoise.wrap((double)k * this.xzMainScale * g), this.yMainScale * g, (double)j * this.yMainScale * g) / g;
            }

            g /= 2.0D;
        }

        double h = (f / 10.0D + 1.0D) / 2.0D;
        boolean bl2 = h >= 1.0D;
        boolean bl3 = h <= 0.0D;
        g = 1.0D;

        for(int m = 0; m < 16; ++m) {
            double n = PerlinNoise.wrap((double)i * this.xzScale * g);
            double o = PerlinNoise.wrap((double)j * this.yScale * g);
            double p = PerlinNoise.wrap((double)k * this.xzScale * g);
            double q = this.yScale * g;
            ImprovedNoise improvedNoise2;
            if (!bl2) {
                improvedNoise2 = this.minLimitNoise.getOctaveNoise(m);
                if (improvedNoise2 != null) {
                    d += improvedNoise2.noise(n, o, p, q, (double)j * q) / g;
                }
            }

            if (!bl3) {
                improvedNoise2 = this.maxLimitNoise.getOctaveNoise(m);
                if (improvedNoise2 != null) {
                    e += improvedNoise2.noise(n, o, p, q, (double)j * q) / g;
                }
            }

            g /= 2.0D;
        }

        return Mth.clampedLerp(d / 512.0D, e / 512.0D, h) / 128.0D;
    }


    @Override
    public PerlinNoise minLimitNoise() {
        return this.minLimitNoise;
    }

    @Override
    public PerlinNoise maxLimitNoise() {
        return this.maxLimitNoise;
    }

    @Override
    public PerlinNoise mainNoise() {
        return this.mainNoise;
    }

    @Override
    public double xzScale() {
        return this.xzScale;
    }

    @Override
    public double yScale() {
        return this.yScale;
    }

    @Override
    public double xzMainScale() {
        return this.xzMainScale;
    }

    @Override
    public double yMainScale() {
        return this.yMainScale;
    }

    @Override
    public int cellWidth() {
        return this.cellWidth;
    }

    @Override
    public int cellHeight() {
        return this.cellHeight;
    }
}
