package jpg.k.simplyimprovedterrain.mixin;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.terrain.OpenSimplex2S;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.BambooFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Adds noise to the podzol distributions emanating from the bases of the plants.
 * Visual impact: ★★☆☆☆  (dense clustering and built-in radius variation in vanilla already helped)
 */
@Mixin(BambooFeature.class)
public class MixinBambooFeature extends Feature<ProbabilityFeatureConfiguration> {
    @Shadow @Final private static BlockState BAMBOO_TRUNK;
    @Shadow @Final private static BlockState BAMBOO_FINAL_LARGE;
    @Shadow @Final private static BlockState BAMBOO_TOP_LARGE;
    @Shadow @Final private static BlockState BAMBOO_TOP_SMALL;

    private static final int MIN_HEIGHT = 5;
    private static final int MAX_HEIGHT = 17;

    private static final float MIN_RADIUS = 1.4f;
    private static final float MAX_RADIUS = 7.25f;

    private static final float RADIUS_NOISE_MODULATION_RATIO = 0.8f;
    private static final double NOISE_FREQUENCY_XZ = 0.1875;
    private static final double NOISE_FREQUENCY_Y = 0.1875;
    private static final long NOISE_SEED_FLIP_MASK = -0x0845BF5BC9DCEE05L;

    public MixinBambooFeature(Codec<ProbabilityFeatureConfiguration> codec) {
        super(codec);
    }

    /**
     * @author K.jpg
     * @reason Surface noise.
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<ProbabilityFeatureConfiguration> featurePlaceContext) {
        int placedCount = 0;
        BlockPos origin = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        RandomSource random = featurePlaceContext.random();
        ProbabilityFeatureConfiguration probabilityFeatureConfiguration = featurePlaceContext.config();
        long noiseSeed = featurePlaceContext.level().getSeed() ^ NOISE_SEED_FLIP_MASK;

        BlockPos.MutableBlockPos trunkMutableBlockPos = origin.mutable();
        BlockPos.MutableBlockPos surfaceMutableBlockPos = origin.mutable();

        if (worldGenLevel.isEmptyBlock(trunkMutableBlockPos)) {
            if (Blocks.BAMBOO.defaultBlockState().canSurvive(worldGenLevel, trunkMutableBlockPos)) {
                int trunkHeight = random.nextInt(MAX_HEIGHT - MIN_HEIGHT) + MIN_HEIGHT;
                if (random.nextFloat() < probabilityFeatureConfiguration.probability) {

                    // Radius range for noise modulation.
                    float maxNoisedSurfaceRadius = Mth.randomBetween(random, MIN_RADIUS, MAX_RADIUS);
                    float minNoisedSurfaceRadius = maxNoisedSurfaceRadius * RADIUS_NOISE_MODULATION_RATIO;
                    float falloffAtMinRadius = Mth.square(maxNoisedSurfaceRadius * maxNoisedSurfaceRadius - minNoisedSurfaceRadius * minNoisedSurfaceRadius);

                    // Make the loop nicer.
                    int maxNoisedSurfaceRadiusSquaredTruncated = (int)(maxNoisedSurfaceRadius * maxNoisedSurfaceRadius);
                    int minNoisedSurfaceRadiusSquaredTruncated = (int)(minNoisedSurfaceRadius * minNoisedSurfaceRadius);
                    int surfaceRadiusLoopBound = (int)maxNoisedSurfaceRadius;

                    for (int dz = -surfaceRadiusLoopBound; dz <= surfaceRadiusLoopBound; dz++) {
                        for (int dx = -surfaceRadiusLoopBound; dx <= surfaceRadiusLoopBound; dx++) {
                            int distanceSquared = dz * dz + dx * dx;

                            // No surface blocks will be placed outside thw maximum radius.
                            if (distanceSquared >= maxNoisedSurfaceRadiusSquaredTruncated) continue;

                            // Always place blocks within the minimum radius.
                            boolean isInRange = distanceSquared <= minNoisedSurfaceRadiusSquaredTruncated;

                            int worldX = dx + origin.getX();
                            int worldZ = dz + origin.getZ();
                            int surfaceY = worldGenLevel.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;

                            // Between that range, the noise mixed with the falloff curve decides.
                            if (!isInRange) {

                                // Smooth Euclidean-based polynomial falloff curve
                                float falloff = Mth.square(maxNoisedSurfaceRadius * maxNoisedSurfaceRadius - distanceSquared);

                                // Get noise and convert range to [0, 1].
                                float noise = OpenSimplex2S.noise3_ImproveXZ(noiseSeed,
                                        worldX   * NOISE_FREQUENCY_XZ,
                                        surfaceY * NOISE_FREQUENCY_Y,
                                        worldZ   * NOISE_FREQUENCY_XZ
                                ) * 0.5f + 0.5f;

                                // Noise value = 0 means we compare the falloff with zero, which occurs at the max noise variation radius.
                                // Noise value = 1 means we compare the falloff with the value that occurs at the min noise variation radius instead.
                                isInRange = (falloff > noise * falloffAtMinRadius);
                            }

                            if (isInRange) {
                                surfaceMutableBlockPos.set(worldX, surfaceY, worldZ);
                                if (isDirt(worldGenLevel.getBlockState(surfaceMutableBlockPos))) {
                                    worldGenLevel.setBlock(surfaceMutableBlockPos, Blocks.PODZOL.defaultBlockState(), 2);
                                }
                            }
                        }
                    }
                }

                // Place main bamboo trunk
                for (int i = 0; i < trunkHeight && worldGenLevel.isEmptyBlock(trunkMutableBlockPos); ++i) {
                    worldGenLevel.setBlock(trunkMutableBlockPos, BAMBOO_TRUNK, 2);
                    trunkMutableBlockPos.move(Direction.UP, 1);
                }

                // Place the parts that go on top.
                if (trunkMutableBlockPos.getY() - origin.getY() >= 3) {
                    worldGenLevel.setBlock(trunkMutableBlockPos, BAMBOO_FINAL_LARGE, 2);
                    worldGenLevel.setBlock(trunkMutableBlockPos.move(Direction.DOWN, 1), BAMBOO_TOP_LARGE, 2);
                    worldGenLevel.setBlock(trunkMutableBlockPos.move(Direction.DOWN, 1), BAMBOO_TOP_SMALL, 2);
                }
            }

            ++placedCount;
        }

        return (placedCount > 0);
    }
}
