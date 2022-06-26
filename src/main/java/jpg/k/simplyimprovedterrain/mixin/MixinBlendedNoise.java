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

    @Shadow @Final private double xzMultiplier;
    @Shadow @Final private double yMultiplier;
    @Shadow @Final private double xzFactor;
    @Shadow @Final private double yFactor;
    @Shadow @Final private double smearScaleMultiplier;

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
    public double xzMultiplier() {
        return this.xzMultiplier;
    }

    @Override
    public double yMultiplier() {
        return this.yMultiplier;
    }

    @Override
    public double xzFactor() {
        return this.xzFactor;
    }

    @Override
    public double yFactor() {
        return this.yFactor;
    }

    public double smearScaleMultiplier() {
        return this.smearScaleMultiplier;
    }
}
