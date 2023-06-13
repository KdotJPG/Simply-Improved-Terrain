package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.biome.BiomeFiddleHelper;
import jpg.k.simplyimprovedterrain.biome.FiddledBiomeResolver;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinClimateSampler;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MixinMultiNoiseBiomeSource implements FiddledBiomeResolver {

    @Shadow abstract Holder<Biome> getNoiseBiome(Climate.TargetPoint targetPoint);

    public Holder<Biome> getFiddledNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler, long fiddleSeed) {
        return this.getNoiseBiome(BiomeFiddleHelper.sampleFiddled(quartX, quartY, quartZ, fiddleSeed, ((IMixinClimateSampler)(Object)sampler)::sampleGranular));
    }

}
