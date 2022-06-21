package jpg.k.simplyimprovedterrain.mixinapi;

import net.minecraft.world.level.levelgen.synth.PerlinNoise;

public interface IMixinBlendedNoise {
    PerlinNoise minLimitNoise();
    PerlinNoise maxLimitNoise();
    PerlinNoise mainNoise();
    double xzScale();
    double yScale();
    double xzMainScale();
    double yMainScale();
    int cellWidth();
    int cellHeight();
}
