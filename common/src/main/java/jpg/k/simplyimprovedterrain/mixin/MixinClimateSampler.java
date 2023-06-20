package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.mixinapi.IMixinClimateSampler;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Climate.Sampler.class)
public class MixinClimateSampler implements IMixinClimateSampler {
    @Shadow @Final private DensityFunction temperature;
    @Shadow @Final private DensityFunction humidity;
    @Shadow @Final private DensityFunction continentalness;
    @Shadow @Final private DensityFunction erosion;
    @Shadow @Final private DensityFunction depth;
    @Shadow @Final private DensityFunction weirdness;
    
    public Climate.TargetPoint sampleGranular(int blockX, int blockY, int blockZ) {
        DensityFunction.SinglePointContext singlePointContext = new DensityFunction.SinglePointContext(blockX, blockY, blockZ);
        return Climate.target(
                (float)this.temperature.compute(singlePointContext),
                (float)this.humidity.compute(singlePointContext),
                (float)this.continentalness.compute(singlePointContext),
                (float)this.erosion.compute(singlePointContext),
                (float)this.depth.compute(singlePointContext),
                (float)this.weirdness.compute(singlePointContext)
        );
    }
}
