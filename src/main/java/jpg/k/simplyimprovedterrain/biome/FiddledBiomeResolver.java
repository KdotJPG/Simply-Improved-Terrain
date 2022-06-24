package jpg.k.simplyimprovedterrain.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

public interface FiddledBiomeResolver {
    Holder<Biome> getFiddledNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler, long fiddleSeed);
}
