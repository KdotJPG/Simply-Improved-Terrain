package jpg.k.simplyimprovedterrain.mixinapi;

import net.minecraft.world.level.levelgen.synth.PerlinNoise;

public interface IMixinBlendedNoise {
    PerlinNoise minLimitNoise();
    PerlinNoise maxLimitNoise();
    PerlinNoise mainNoise();
    double xzMultiplier();
    double yMultiplier();
    double xzFactor();
    double yFactor();
    double smearScaleMultiplier();
}
