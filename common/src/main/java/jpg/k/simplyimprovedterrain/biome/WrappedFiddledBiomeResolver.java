package jpg.k.simplyimprovedterrain.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;

public record WrappedFiddledBiomeResolver(FiddledBiomeResolver fiddledBiomeResolver, long fiddleSeed) implements BiomeResolver {

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return this.fiddledBiomeResolver.getFiddledNoiseBiome(quartX, quartY, quartZ, sampler, this.fiddleSeed);
    }
}
