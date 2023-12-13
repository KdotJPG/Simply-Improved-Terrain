package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.biome.BiomeFiddleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BiomeManager.class, priority = 250)
public class MixinBiomeManager {

    private static final double SQRT3 = Math.sqrt(3.0);
    private static final int SEARCH_RADIUS_INT = Mth.ceil(SQRT3 * QuartPos.SIZE);
    private static final int MAX_SEARCH_WIDTH = Mth.ceil((SEARCH_RADIUS_INT * 2 + QuartPos.SIZE - 1) * (1.0 / QuartPos.SIZE));
    private static final int MAX_SEARCH_SIZE = MAX_SEARCH_WIDTH * MAX_SEARCH_WIDTH * MAX_SEARCH_WIDTH;

    @Shadow @Final private long biomeZoomSeed;
    @Shadow @Final private BiomeManager.NoiseBiomeSource noiseBiomeSource;

    private static final ThreadLocal<BiomeFiddleHelper.BiomeAndWeight[]> biomeAndWeightPairsThreadLocal
            = ThreadLocal.withInitial(() -> new BiomeFiddleHelper.BiomeAndWeight[MAX_SEARCH_SIZE]);

    /**
     * @author K.jpg
     * @reason increase Voronoi search range to mitigate visible interval bias; aggregate falloff weights to create clean border curves.
     */
    @Overwrite
    public Holder<Biome> getBiome(BlockPos blockPos) {
        int offsetX = blockPos.getX() - BiomeFiddleHelper.BLOCK_XYZ_OFFSET;
        int offsetY = blockPos.getY() - BiomeFiddleHelper.BLOCK_XYZ_OFFSET;
        int offsetZ = blockPos.getZ() - BiomeFiddleHelper.BLOCK_XYZ_OFFSET;

        int xStart = (offsetX - SEARCH_RADIUS_INT) >> QuartPos.BITS;
        int yStart = (offsetY - SEARCH_RADIUS_INT) >> QuartPos.BITS;
        int zStart = (offsetZ - SEARCH_RADIUS_INT) >> QuartPos.BITS;
        int xEnd = (offsetX + SEARCH_RADIUS_INT) >> QuartPos.BITS;
        int yEnd = (offsetY + SEARCH_RADIUS_INT) >> QuartPos.BITS;
        int zEnd = (offsetZ + SEARCH_RADIUS_INT) >> QuartPos.BITS;
        int xCount = xEnd - xStart + 1;
        int yCount = yEnd - yStart + 1;
        int zCount = zEnd - zStart + 1;

        double xDelta = (offsetX - (xStart << QuartPos.BITS)) * (1.0 / QuartPos.SIZE);
        double yDelta = (offsetY - (yStart << QuartPos.BITS)) * (1.0 / QuartPos.SIZE);
        double zDelta = (offsetZ - (zStart << QuartPos.BITS)) * (1.0 / QuartPos.SIZE);

        BiomeFiddleHelper.BiomeAndWeight[] biomeAndWeightPairs = biomeAndWeightPairsThreadLocal.get();
        int nUniqueBiomesFound = 0;

        for (int cz = 0, cy = 0, cx = 0;;) {
            double fiddledDistanceSquared = BiomeFiddleHelper.getFiddledDistance(this.biomeZoomSeed, cx + xStart, cy + yStart, cz + zStart, xDelta - cx, yDelta - cy, zDelta - cz);

            if (fiddledDistanceSquared < 3) {
                double falloff = 3 - fiddledDistanceSquared;
                falloff *= falloff * falloff;

                Holder<Biome> biomeHolderHere = this.noiseBiomeSource.getNoiseBiome(cx + xStart, cy + yStart, cz + zStart);
                Biome biomeHere = biomeHolderHere.value();
                int i = 0;
                for (; i < nUniqueBiomesFound; i++) {
                    BiomeFiddleHelper.BiomeAndWeight biomeAndWeightPair = biomeAndWeightPairs[i];
                    if (biomeHere.equals(biomeAndWeightPair.biome())) {
                        biomeAndWeightPairs[i] = new BiomeFiddleHelper.BiomeAndWeight(biomeHere, biomeHolderHere, biomeAndWeightPair.weight() + falloff);
                        break;
                    }
                }
                if (i == nUniqueBiomesFound) {
                    biomeAndWeightPairs[i] = new BiomeFiddleHelper.BiomeAndWeight(biomeHere, biomeHolderHere, falloff);
                    nUniqueBiomesFound++;
                }

            }

            cz++;
            if (cz < zCount) continue;
            cz = 0;
            cy++;
            if (cy < yCount) continue;
            cy = 0;
            cx++;
            if (cx >= xCount) break;
        }

        double bestWeight = Double.NEGATIVE_INFINITY;
        Holder<Biome> bestBiomeHolder = null;
        for (int i = 0; i < nUniqueBiomesFound; i++) {
            BiomeFiddleHelper.BiomeAndWeight biomeAndWeightPair = biomeAndWeightPairs[i];
            if (biomeAndWeightPair.weight() > bestWeight) {
                bestWeight = biomeAndWeightPair.weight();
                bestBiomeHolder = biomeAndWeightPair.holder();
            }
        }

        return bestBiomeHolder;
    }

}
