package jpg.k.simplyimprovedterrain.mixinapi;

import net.minecraft.world.level.biome.Climate;

public interface IMixinClimateSampler {
    Climate.TargetPoint sampleGranular(int blockX, int blockY, int blockZ);
}
