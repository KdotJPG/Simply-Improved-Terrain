package jpg.k.simplyimprovedterrain.mixinapi;

import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.function.Function;

public interface IMixinClimateSampler {
    Climate.TargetPoint sampleGranular(int blockX, int blockY, int blockZ);
    Climate.Sampler transformAll(Function<DensityFunction, DensityFunction> transformer);
}
