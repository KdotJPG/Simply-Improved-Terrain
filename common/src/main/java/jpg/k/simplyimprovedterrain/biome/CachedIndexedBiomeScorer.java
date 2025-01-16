package jpg.k.simplyimprovedterrain.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.Array;
import java.util.Arrays;

public class CachedIndexedBiomeScorer {
    private static final int INITIAL_CAPACITY = 10;

    private static final int CACHE_DIMENSION_EXPONENT = 3;
    private static final int CACHE_DIMENSION = 1 << CACHE_DIMENSION_EXPONENT;
    private static final int CACHE_DIMENSION_MASK = CACHE_DIMENSION - 1;
    private static final int CACHE_ENTRY_SIZE = 4;
    private static final int CACHE_ENTRY_INDEX_X = 0;
    private static final int CACHE_ENTRY_INDEX_Y = 1;
    private static final int CACHE_ENTRY_INDEX_Z = 2;
    private static final int CACHE_ENTRY_INDEX_BIOME_INDEX = 3;
    private static final int CACHE_SIZE_TOTAL = CACHE_ENTRY_SIZE << (3 * CACHE_DIMENSION_EXPONENT);

    private int entryCount;
    @SuppressWarnings("unchecked") private Holder<Biome>[] indexedBiomes = (Holder<Biome>[]) new Holder<?>[INITIAL_CAPACITY];
    private int[] biomeUsageCounts = new int[INITIAL_CAPACITY];
    private double[] biomeWeights = new double[INITIAL_CAPACITY];
    private final int[] biomeSpatialCache = new int[CACHE_SIZE_TOTAL];

    public CachedIndexedBiomeScorer() {
        Arrays.fill(biomeSpatialCache, -1);
    }

    public int getCachedBiomeIndex(int x, int y, int z, BiomeSampler biomeSampler) {

        // If this position is already cached, return that.
        int cacheIndexBase = indexSpatialCache(x, y, z);
        int xCached = biomeSpatialCache[cacheIndexBase + CACHE_ENTRY_INDEX_X];
        int yCached = biomeSpatialCache[cacheIndexBase + CACHE_ENTRY_INDEX_Y];
        int zCached = biomeSpatialCache[cacheIndexBase + CACHE_ENTRY_INDEX_Z];
        int iBiomeCached = biomeSpatialCache[cacheIndexBase + CACHE_ENTRY_INDEX_BIOME_INDEX];
        if (xCached == x && yCached == y && zCached == z && iBiomeCached >= 0) return iBiomeCached;

        // Otherwise, sample the biome and replace the cached entry.
        Holder<Biome> biome = biomeSampler.sample(x, y, z);
        int iBiome = iBiomeCached;
        if (!(iBiomeCached >= 0 && biome == indexedBiomes[iBiomeCached])) {
            iBiome = getOrAddBiomeIndex(biome);
            if (iBiomeCached >= 0) unmarkBiomeUsage(iBiomeCached);
        }
        biomeSpatialCache[cacheIndexBase + CACHE_ENTRY_INDEX_X] = x;
        biomeSpatialCache[cacheIndexBase + CACHE_ENTRY_INDEX_Y] = y;
        biomeSpatialCache[cacheIndexBase + CACHE_ENTRY_INDEX_Z] = z;
        biomeSpatialCache[cacheIndexBase + CACHE_ENTRY_INDEX_BIOME_INDEX] = iBiome;
        return iBiome;
    }

    private int capacity() { return indexedBiomes.length; }

    private static int indexSpatialCache(int x, int y, int z) {
        x &= CACHE_DIMENSION_MASK;
        y &= CACHE_DIMENSION_MASK;
        z &= CACHE_DIMENSION_MASK;
        return CACHE_ENTRY_SIZE * (x | (z << CACHE_DIMENSION_EXPONENT) | (y << (2 * CACHE_DIMENSION_EXPONENT)));
    }

    private int getOrAddBiomeIndex(Holder<Biome> biome) {
        int iBiomeTarget = -1, iBiome = 0;
        for (int biomeCount = 0; biomeCount < entryCount && iBiome < capacity(); iBiome++) {
            Holder<Biome> biomeHere = indexedBiomes[iBiome];
            if (biomeHere == biome) {
                biomeUsageCounts[iBiome] += 1;
                return iBiome;
            }
            else if (biomeHere != null) biomeCount++; // We can exit the loop early if this reaches entryCount.
            else if (iBiomeTarget < 0) iBiomeTarget = iBiome; // Target first null entry if no match found.
        }
        if (iBiomeTarget < 0) iBiomeTarget = iBiome; // Target first null entry if no match found.
        if (iBiomeTarget >= capacity()) grow();

        indexedBiomes[iBiomeTarget] = biome;
        biomeUsageCounts[iBiomeTarget] = 1;
        entryCount++;
        return iBiomeTarget;
    }

    private void unmarkBiomeUsage(int iBiome) {
        biomeUsageCounts[iBiome] -= 1;
        if (biomeUsageCounts[iBiome] > 0) return;
        indexedBiomes[iBiome] = null;
        entryCount--;
    }

    private void grow() {
        int newCapacity = capacity() * 3 / 2 + 1;
        @SuppressWarnings("unchecked") Holder<Biome>[] indexedBiomesNew = (Holder<Biome>[]) new Holder<?>[newCapacity];
        int[] biomeCacheCountsNew = new int[newCapacity];
        double[] biomeWeightsNew = new double[newCapacity];
        System.arraycopy(indexedBiomes, 0, indexedBiomesNew, 0, capacity());
        System.arraycopy(biomeUsageCounts, 0, biomeCacheCountsNew, 0, capacity());
        System.arraycopy(biomeWeights, 0, biomeWeightsNew, 0, capacity());
        indexedBiomes = indexedBiomesNew;
        biomeUsageCounts = biomeCacheCountsNew;
        biomeWeights = biomeWeightsNew;
    }

    public void addWeight(int iBiome, double weight) {
        biomeWeights[iBiome] += weight;
    }

    public Holder<Biome> chooseWinner() {
        double weightMax = Float.NEGATIVE_INFINITY;
        Holder<Biome> winner = null;
        for (int iBiome = 0, biomeCount = 0; iBiome < capacity() && biomeCount < entryCount; iBiome++) {
            if (indexedBiomes[iBiome] == null) continue;
            biomeCount++;
            double weightHere = biomeWeights[iBiome];
            if (weightHere < weightMax) continue;
            weightMax = weightHere;
            winner = indexedBiomes[iBiome];
        }
        return winner;
    }

    public void clearWeights() {
        for (int iBiome = 0, biomeCount = 0; iBiome < capacity() && biomeCount < entryCount; iBiome++) {
            if (indexedBiomes[iBiome] == null) continue;
            biomeWeights[iBiome] = 0;
            biomeCount++;
        }
    }

    @FunctionalInterface
    public interface BiomeSampler {
        Holder<Biome> sample(int x, int y, int z);
    }
}
