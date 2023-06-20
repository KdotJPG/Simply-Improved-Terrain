package jpg.k.simplyimprovedterrain.mixin;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.math.ExtraMath;
import jpg.k.simplyimprovedterrain.math.RotatedEllipsoid;
import jpg.k.simplyimprovedterrain.terrain.OpenSimplex2S;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.BlockBlobFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Replaces the clone-and-offset variation with ellipsoid rotation and noise.
 * Visual impact: ★★★☆☆
 */
@Mixin(BlockBlobFeature.class)
public class MixinBlockBlobFeature extends Feature<BlockStateConfiguration> {

    private static final float MIN_RADIUS = 1.42f;
    private static final float MAX_RADIUS = 3.6f;

    private static final double NOISE_FREQUENCY_XZ = 0.2;
    private static final double NOISE_FREQUENCY_Y = 0.2;
    private static final long NOISE_SEED_FLIP_MASK = 0x105931A6DAF17D0EL;

    private static final int RADIUS_LOOP_BOUND = (int)MAX_RADIUS;

    public MixinBlockBlobFeature(Codec<BlockStateConfiguration> codec) {
        super(codec);
    }

    /**
     * @author K.jpg
     * @reason Scale vectors not axes
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> featurePlaceContext) {
        BlockPos origin = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        RandomSource random = featurePlaceContext.random();
        BlockStateConfiguration blockStateConfiguration = featurePlaceContext.config();
        long noiseSeed = featurePlaceContext.level().getSeed() ^ NOISE_SEED_FLIP_MASK;

        int minPlacementHeight = worldGenLevel.getMinBuildHeight() + RADIUS_LOOP_BOUND;

        // Search for a position at or below dirt or stone.
        for (; origin.getY() >= minPlacementHeight; origin = origin.below()) {
            if (!worldGenLevel.isEmptyBlock(origin.below())) {
                BlockState blockState = worldGenLevel.getBlockState(origin.below());
                if (isDirt(blockState) || isStone(blockState)) {
                    break;
                }
            }
        }

        if (origin.getY() < minPlacementHeight) {
            return false;
        }

        // Two randomly scaled and oriented ellipsoids.
        // We will mix between these with noise.
        RotatedEllipsoid ellipsoidA = RotatedEllipsoid.createFromRandomAndRadii(
                random,
                Mth.randomBetween(random, MIN_RADIUS, MAX_RADIUS),
                Mth.randomBetween(random, MIN_RADIUS, MAX_RADIUS),
                Mth.randomBetween(random, MIN_RADIUS, MAX_RADIUS)
        );
        RotatedEllipsoid ellipsoidB = RotatedEllipsoid.createFromRandomAndRadii(
                random,
                Mth.randomBetween(random, MIN_RADIUS, MAX_RADIUS),
                Mth.randomBetween(random, MIN_RADIUS, MAX_RADIUS),
                Mth.randomBetween(random, MIN_RADIUS, MAX_RADIUS)
        );

        BlockPos.MutableBlockPos currentBlockPos = new BlockPos.MutableBlockPos();
        for (int dz = -RADIUS_LOOP_BOUND; dz <= RADIUS_LOOP_BOUND; dz++) {
            for (int dx = -RADIUS_LOOP_BOUND; dx <= RADIUS_LOOP_BOUND; dx++) {
                for (int dy = -RADIUS_LOOP_BOUND; dy <= RADIUS_LOOP_BOUND; dy++) {
                    currentBlockPos.setWithOffset(origin, dx, dy, dz);

                    float ellipsoidValueA = ellipsoidA.compute(dx, dy, dz);
                    float ellipsoidValueB = ellipsoidB.compute(dx, dy, dz);

                    // No block placement if we're outside both ellipsoids.
                    if (ellipsoidValueA >= 1 && ellipsoidValueB >= 1) continue;

                    // If we're outside one of them, then let noise smoothly decide between them.
                    if (ellipsoidValueA >= 1 || ellipsoidValueB >= 1) {

                        // Sample a smooth noise value, range [-1, 1]
                        float noiseValue = OpenSimplex2S.noise3_ImproveXZ(
                                noiseSeed,
                                currentBlockPos.getX() * NOISE_FREQUENCY_XZ,
                                currentBlockPos.getY() * NOISE_FREQUENCY_Y,
                                currentBlockPos.getZ() * NOISE_FREQUENCY_XZ
                        );

                        // Rescale to [0, 1] and make extreme values more common.
                        float slideFromNoise = ExtraMath.clampedFadeWithSymmetricDomainAndUnitRange(noiseValue);

                        // Mixed ellipsoid value determines solidity.
                        float ellipsoidValue = Mth.lerp(slideFromNoise, ellipsoidValueA, ellipsoidValueB);
                        if (ellipsoidValue >= 1) continue;
                    }

                    worldGenLevel.setBlock(currentBlockPos, blockStateConfiguration.state, 3);
                }
            }
        }

        return true;
    }

}
