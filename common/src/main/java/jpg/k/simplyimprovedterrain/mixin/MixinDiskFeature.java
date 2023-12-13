package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.terrain.OpenSimplex2S;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Adds noise to circular deposits.
 * Visual impact: ★★★★★
 */
@Mixin(value = DiskFeature.class, priority = 250)
public abstract class MixinDiskFeature {

    private static final double NOISE_FREQUENCY_XZ = 0.155;
    private static final double NOISE_FREQUENCY_Y = 0.155;
    private static final float RADIUS_MIN_RATIO_TO_CONFIG = 0.6f;
    private static final float RADIUS_MAX_RATIO_TO_CONFIG = 1.55f;
    private static final long NOISE_SEED_FLIP_MASK = -0x2E64A9478DE1EB71L;

    /**
     * @author K.jpg
     * @reason Noise!
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<DiskConfiguration> featurePlaceContext) {
        DiskConfiguration diskConfiguration = featurePlaceContext.config();
        BlockPos center = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        RandomSource random = featurePlaceContext.random();
        long noiseSeed = featurePlaceContext.level().getSeed() ^ NOISE_SEED_FLIP_MASK;

        // Height range configuration
        int worldY = center.getY();
        int yMax = worldY + diskConfiguration.halfHeight();
        int yMin = worldY - diskConfiguration.halfHeight() - 1;

        // Set up radius variation.
        float minNoisedRadius, maxNoisedRadius;
        int configuredRadius = diskConfiguration.radius().sample(random);
        if (configuredRadius == 1) {
            minNoisedRadius = maxNoisedRadius = 1; // Some mods use radius=1 for explicitly-single-block placements.
        } else {
            maxNoisedRadius = Math.min(LevelChunkSection.SECTION_WIDTH, configuredRadius * RADIUS_MAX_RATIO_TO_CONFIG);
            minNoisedRadius = maxNoisedRadius * (RADIUS_MIN_RATIO_TO_CONFIG / RADIUS_MAX_RATIO_TO_CONFIG);
        }
        float falloffAtMinRadius = Mth.square(maxNoisedRadius * maxNoisedRadius - minNoisedRadius * minNoisedRadius);

        // Make the loop nicer.
        int maxNoisedRadiusSquaredTruncated = (int)(maxNoisedRadius * maxNoisedRadius);
        int minNoisedRadiusSquaredTruncated = (int)(minNoisedRadius * minNoisedRadius);
        int radiusBound = (int)maxNoisedRadius;

        boolean placedSomething = false;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (BlockPos current : BlockPos.betweenClosed(center.offset(-radiusBound, 0, -radiusBound), center.offset(radiusBound, 0, radiusBound))) {
            int worldX = current.getX(), worldZ = current.getZ();
            int dx = worldX - center.getX(), dz = worldZ - center.getZ();
            int distanceSquared = dx * dx + dz * dz;

            // No blocks will be placed outside thw maximum radius.
            if (distanceSquared >= maxNoisedRadiusSquaredTruncated) continue;

            // Always place blocks within the minimum radius.
            boolean isInRange = distanceSquared <= minNoisedRadiusSquaredTruncated;

            // Between that range, the noise mixed with the falloff curve decides.
            if (!isInRange) {

                // Smooth Euclidean-based polynomial falloff curve
                float falloff = Mth.square(maxNoisedRadius * maxNoisedRadius - distanceSquared);

                // Get noise and convert range to [0, 1].
                float noise = OpenSimplex2S.noise3_ImproveXZ(
                        noiseSeed,
                        worldX * NOISE_FREQUENCY_XZ,
                        worldY * NOISE_FREQUENCY_Y,
                        worldZ * NOISE_FREQUENCY_XZ
                ) * 0.5f + 0.5f;

                // Noise value = 0 means we compare the falloff with zero, which occurs at the max noise variation radius.
                // Noise value = 1 means we compare the falloff with the value that occurs at the min noise variation radius instead.
                isInRange = (falloff > noise * falloffAtMinRadius);

            }

            // Let's place blocks.
            if (isInRange) {
                placedSomething |= this.placeColumn(diskConfiguration, worldGenLevel, random, yMax, yMin, mutableBlockPos.set(current));
            }
        }

        return placedSomething;
    }

    @Shadow
    protected abstract boolean placeColumn(DiskConfiguration diskConfiguration, WorldGenLevel worldGenLevel, RandomSource random, int yMax, int yMin, BlockPos.MutableBlockPos mutableBlockPos);

}
