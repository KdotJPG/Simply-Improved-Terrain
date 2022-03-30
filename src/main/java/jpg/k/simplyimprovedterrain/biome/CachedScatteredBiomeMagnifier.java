package jpg.k.simplyimprovedterrain.biome;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.IBiomeMagnifier;
import jpg.k.simplyimprovedterrain.biome.blending.LinkedBiomeWeightMap;
import jpg.k.simplyimprovedterrain.biome.blending.ScatteredBiomeBlender;
import jpg.k.simplyimprovedterrain.util.LinkedHashCache;

public enum CachedScatteredBiomeMagnifier implements IBiomeMagnifier {
    INSTANCE;

    private static final int N_ACTIVEMOST_NODES = 8;
    private static final int CACHE_MAX_SIZE = 24;

    private static final double SCATTERED_BLENDER_FREQUENCY = 0.078125;
    private static final double SCATTERED_BLENDER_PADDING = 4.0;

    private static final ScatteredBiomeBlender scatteredBiomeBlender = new ScatteredBiomeBlender(SCATTERED_BLENDER_FREQUENCY, SCATTERED_BLENDER_PADDING, 16);
    private static final int gridPadding = (int)Math.ceil(scatteredBiomeBlender.getInternalBlendRadius() * 0.25) + 3; // 3 could be more than needed. Might revisit.
    private static final int paddedGridWidth = 5 + 2 * gridPadding; // 1/4 chunk width, +1 for end bounds, + gridPadding on each side
    private static final int paddedGridWidthSq = paddedGridWidth * paddedGridWidth;

    private static LinkedHashCache<ProviderCoordinate, Biome[]> cache = new LinkedHashCache<>(N_ACTIVEMOST_NODES, CACHE_MAX_SIZE);

    private void CachedScatteredBiomeMagnifier() { }

    public Biome getBiome(long seed, int x, int y, int z, BiomeManager.IBiomeReader biomeReader) {
        ProviderCoordinate key = new ProviderCoordinate(biomeReader, seed, x & (int)0xFFFFFFF0, z & (int)0xFFFFFFF0);
        Biome[] biomes = cache.get(key, CachedScatteredBiomeMagnifier::generateBiomes);
        Biome biome = biomes.length != 1 ? biomes[((z & 0xF) << 4) | (x & 0xF)] : biomes[0];
        return biome;
    }

    private static Biome[] generateBiomes(ProviderCoordinate key) {
        LinkedBiomeWeightMap startEntry = generateBiomeBlending(key.biomeReader, key.seed, key.x, key.z);
        return generateBiomes(startEntry);
    }

    private static Biome[] generateBiomes(LinkedBiomeWeightMap startEntry) {
        if (startEntry.getNext() != null) {
            Biome[] biomes = new Biome[256];
            for (int i = 0; i < 256; i++) {
                double bestWeight = Double.NEGATIVE_INFINITY;
                Biome bestBiome = null;
                for (LinkedBiomeWeightMap entry = startEntry; entry != null; entry = entry.getNext()) {
                    double thisWeight = entry.getWeights()[i];
                    if (thisWeight > bestWeight) {
                        bestWeight = thisWeight;
                        bestBiome = entry.getBiome();
                    }
                }
                biomes[i] = bestBiome;
            }
            return biomes;
        } else {
            return new Biome[] { startEntry.getBiome() };
        }
    }

    private static LinkedBiomeWeightMap generateBiomeBlending(BiomeManager.IBiomeReader biomeReader, long seed, int worldChunkX, int worldChunkZ) {
        int worldChunkXScaled = worldChunkX >> 2;
        int worldChunkZScaled = worldChunkZ >> 2;

        Biome[] lookupGrid = new Biome[paddedGridWidthSq];
        for (int z = 0; z < paddedGridWidth; z++) {
            for (int x = 0; x < paddedGridWidth; x++) {
                lookupGrid[z * paddedGridWidth + x] = biomeReader.getNoiseBiome(x + (worldChunkXScaled - gridPadding), 0, z + (worldChunkZScaled - gridPadding));
            }
        }

        return scatteredBiomeBlender.getBlendForChunk(0, worldChunkX, worldChunkZ, (double x, double z) -> {
            x *= 0.25;
            z *= 0.25;

            int xRound = (int)(x > 0 ? x + 0.5 : x - 0.5);
            int zRound = (int)(z > 0 ? z + 0.5 : z - 0.5);

            int lxRound = xRound + (gridPadding - worldChunkXScaled);
            int lzRound = zRound + (gridPadding - worldChunkZScaled);
            Biome biome = lookupGrid[lzRound * paddedGridWidth + lxRound];

            // Give rivers a bigger area.
            double closestRiverTileDistSq = Double.POSITIVE_INFINITY;
            for (int rz = zRound - 2; rz <= zRound + 2; rz++) {
                int b = rz > zRound - 2 && rz < zRound + 2 ? 2 : 1; // Check a rounded 5x5 not a full 5x5
                for (int rx = xRound - b; rx <= xRound + b; rx++) {
                    double distSq = (rz - z) * (rz - z) + (rx - x) * (rx - x);
                    if (distSq >= closestRiverTileDistSq) continue;
                    Biome thisBiome = lookupGrid[(rz + (gridPadding - worldChunkZScaled)) * paddedGridWidth + (rx + (gridPadding - worldChunkXScaled))];
                    if (thisBiome.getBiomeCategory() != Biome.Category.RIVER) continue;
                    biome = thisBiome;
                    closestRiverTileDistSq = distSq;
                }
            }

            return biome;
        });
    }

    public static LinkedBiomeWeightMap generateBiomeBlendingAndCacheMap(BiomeManager.IBiomeReader biomeReader, long seed, int worldChunkX, int worldChunkZ) {
        LinkedBiomeWeightMap startEntry = generateBiomeBlending(biomeReader, seed, worldChunkX, worldChunkZ);
        ProviderCoordinate key = new ProviderCoordinate(biomeReader, seed, worldChunkX, worldChunkZ);
        cache.get(key, (k) -> generateBiomes(startEntry));
        return startEntry;
    }

    private static class ProviderCoordinate {
        BiomeManager.IBiomeReader biomeReader;
        long seed;
        int x, z;

        public ProviderCoordinate(BiomeManager.IBiomeReader biomeReader, long seed, int x, int z) {
            this.biomeReader = biomeReader;
            this.seed = seed;
            this.x = x;
            this.z = z;
        }

        public int hashCode() {
            return Long.hashCode(seed) ^ (x * 509) ^ (z * 2591) ^ (System.identityHashCode(biomeReader) * 515153); 
        }

        public boolean equals(Object other) {
            if (!(other instanceof ProviderCoordinate)) return false;
            ProviderCoordinate o = (ProviderCoordinate) other;
            return this.biomeReader == o.biomeReader && this.seed == o.seed && this.x == o.x && this.z == o.z;
        }
    }

}
