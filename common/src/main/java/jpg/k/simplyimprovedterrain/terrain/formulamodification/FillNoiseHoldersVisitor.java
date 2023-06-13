package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public record FillNoiseHoldersVisitor(HolderGetter<NormalNoise.NoiseParameters> noiseParametersRegistry) implements DensityFunction.Visitor {

    private static final long STANDIN_NOISE_SEED = 0;

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
        Holder<NormalNoise.NoiseParameters> noiseDataHolder = noiseHolder.noiseData();
        NormalNoise normalNoise;

        normalNoise = Noises.instantiate(noiseParametersRegistry, new XoroshiroRandomSource(STANDIN_NOISE_SEED).forkPositional(), (ResourceKey)noiseDataHolder.unwrapKey().orElseThrow());
        return new DensityFunction.NoiseHolder(noiseDataHolder, normalNoise);
    }

    public DensityFunction apply(DensityFunction function) {
        return function;
    }

}
