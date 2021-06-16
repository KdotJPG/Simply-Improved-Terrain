package jpg.k.simplyimprovedterrain.terrain;

import jpg.k.simplyimprovedterrain.biome.CachedScatteredBiomeAccessType;
import jpg.k.simplyimprovedterrain.biome.blending.LinkedBiomeWeightMap;
import jpg.k.simplyimprovedterrain.util.noise.MetaballEndIslandNoise;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;

/**
 * Created by K.jpg on 6/12/2021.
 */
public class ChunkLocalTerrainContext {

    private int chunkWorldX, chunkWorldZ;
    private double[] biomeDepthAndInverseScaleBlending;

    public ChunkLocalTerrainContext(int chunkWorldX, int chunkWorldZ, long worldSeed, BiomeSource biomeSource, GenerationShapeConfig config, int[] endIslandNoisePermutationTable) {
        this.chunkWorldX = chunkWorldX;
        this.chunkWorldZ = chunkWorldZ;

        this.biomeDepthAndInverseScaleBlending = new double[16 * 16 * 2];

        if (endIslandNoisePermutationTable != null) {

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    double depth = MetaballEndIslandNoise.INSTANCE.getNoise(endIslandNoisePermutationTable, chunkWorldX + x, chunkWorldZ + z);

                    biomeDepthAndInverseScaleBlending[(((z * 16) | x) * 2) | 0] = depth;
                    biomeDepthAndInverseScaleBlending[(((z * 16) | x) * 2) | 1] = depth > 0 ? 0.25 : 1;
                }
            }

            // TODO rewrite this to more efficiently generate the entire chunk at once, a la ChunkPointGatherer.

        } else {

            LinkedBiomeWeightMap weightMap = CachedScatteredBiomeAccessType.generateBiomeBlendingAndCacheMap(biomeSource, BiomeAccess.hashSeed(worldSeed), chunkWorldX, chunkWorldZ);

            if (weightMap.getWeights() == null) {

                Biome biome = weightMap.getBiome();
                float biomeDepth = biome.getDepth();
                float biomeScale = biome.getScale();
                if (config.isAmplified() && biomeDepth > 0.0f) {
                    biomeDepth = 1.0f + biomeDepth * 2.0f;
                    biomeScale = 1.0f + biomeScale * 4.0f;
                }
                double effectiveDepth = (biomeDepth * 0.5f - 0.125f) * 0.265625;
                double effectiveInverseScale = 96.0 / (biomeScale * 0.9f + 0.1f); // Inverse for threshold formula

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        biomeDepthAndInverseScaleBlending[(((z * 16) | x) * 2) | 0] = effectiveDepth;
                        biomeDepthAndInverseScaleBlending[(((z * 16) | x) * 2) | 1] = effectiveInverseScale;
                    }
                }

                // TODO this can be rewritten to be an array size two, then move the efficiency special case into the column sampler.

            } else {

                do {

                    Biome biome = weightMap.getBiome();
                    float biomeDepth = biome.getDepth();
                    float biomeScale = biome.getScale();
                    if (config.isAmplified() && biomeDepth > 0.0f) {
                        biomeDepth = 1.0f + biomeDepth * 2.0f;
                        biomeScale = 1.0f + biomeScale * 4.0f;
                    }
                    double effectiveDepth = (biomeDepth * 0.5f - 0.125f) * 0.265625;
                    double effectiveInverseScale = 96.0 / (biomeScale * 0.9f + 0.1f); // Inverse for threshold formula
                    effectiveDepth *= effectiveInverseScale; // Bias blend towards biomes with less height variation

                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            double weight = weightMap.getWeights()[(z * 16) | x];
                            biomeDepthAndInverseScaleBlending[(((z * 16) | x) * 2) | 0] += weight * effectiveDepth;
                            biomeDepthAndInverseScaleBlending[(((z * 16) | x) * 2) | 1] += weight * effectiveInverseScale;
                        }
                    }

                    weightMap = weightMap.getNext();
                } while (weightMap != null);

                // Undo the division by scale
                for (int i = 0; i < 16*16*2; i += 2) {
                    biomeDepthAndInverseScaleBlending[i | 0] /= biomeDepthAndInverseScaleBlending[i | 1];
                }
            }

        }

    }

    public double[] getBiomeDepthAndInverseScaleBlending() {
        return biomeDepthAndInverseScaleBlending;
    }

    public int getChunkWorldX() {
        return chunkWorldX;
    }

    public int getChunkWorldZ() {
        return chunkWorldZ;
    }

    public double getDepth(int xLocal, int zLocal) {
        return this.biomeDepthAndInverseScaleBlending[(((zLocal * 16) | xLocal) * 2) | 0];
    }

    public double getInverseScale(int xLocal, int zLocal) {
        return this.biomeDepthAndInverseScaleBlending[(((zLocal * 16) | xLocal) * 2) | 1];
    }

}
