package jpg.k.simplyimprovedterrain.mixin;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.math.LinearFunction1f;
import jpg.k.simplyimprovedterrain.terrain.OpenSimplex2S;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.EndIslandFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Replaces shaping formula with a piecewise spheroid. Adds noise to break up regularity.
 * Visual impact: ★★★★★
 */
@Mixin(EndIslandFeature.class)
public abstract class MixinEndIslandFeature extends Feature<NoneFeatureConfiguration> {

    // Sphere radius to base the island shape off of.
    private static final float ISLAND_RADIUS_MIN = 5.0f;
    private static final float ISLAND_RADIUS_MAX = 8.0f;

    // Picture a sphere with a horizontal plane going through the equator.
    // Now imagine a second plane extending downward and cutting out everything between the equator and itself.
    // Then visualize the remainder of the hemisphere stretching upward to fill the gap, and outward to match the equator's radius again.
    // The result is a sudden change in slope at the equator, where it starts curving down under right away.
    // The southern pole remains in place for now. The min/max are a randomization range for the actual slice position.
    private static final float SPHERE_HEAD_START_BASE_MIN = 0.15f;
    private static final float SPHERE_HEAD_START_BASE_MAX = 0.55f;

    // Same thing for the upper half.
    private static final float SPHERE_HEAD_START_TOP_MIN = 0.07f;
    private static final float SPHERE_HEAD_START_TOP_MAX = 0.1f;

    // Now rescale the entire lower half.
    private static final float ISLAND_HEIGHT_BASE_RATIO_MIN = 0.6f;
    private static final float ISLAND_HEIGHT_BASE_RATIO_MAX = 1.0f;

    // Now rescale the entire upper half.
    private static final float ISLAND_HEIGHT_TOP_RATIO_MIN = 0.2f;
    private static final float ISLAND_HEIGHT_TOP_RATIO_MAX = 0.29f;

    // Now vary it with noise.
    private static final float NOISE_MODULATION_AMOUNT = 0.42f;
    private static final double NOISE_FREQUENCY_XZ = 0.15;
    private static final double NOISE_FREQUENCY_Y = 0.2;
    private static final long NOISE_SEED_FLIP_MASK = 0x7B1B79229AA14332L;

    public MixinEndIslandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /**
     * @author K.jpg
     * @reason Noise.
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> featurePlaceContext) {
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        RandomSource random = featurePlaceContext.random();
        BlockPos origin = featurePlaceContext.origin();
        long noiseSeed = featurePlaceContext.level().getSeed() ^ NOISE_SEED_FLIP_MASK;

        float radius = Mth.randomBetween(random, ISLAND_RADIUS_MIN, ISLAND_RADIUS_MAX);

        float heightRatioTop  = Mth.lerp(Mth.square(random.nextFloat()),
                ISLAND_HEIGHT_TOP_RATIO_MAX,  ISLAND_HEIGHT_TOP_RATIO_MIN);  // square() and MAX first ->  larger values more common.
        float heightRatioBase = Mth.lerp(Mth.square(random.nextFloat()),
                ISLAND_HEIGHT_BASE_RATIO_MIN, ISLAND_HEIGHT_BASE_RATIO_MAX); // square() and MIN first -> smaller values more common.
        float heightTop  = radius * heightRatioTop;
        float heightBase = radius * heightRatioBase;

        float sphereHeadStartTop  = Mth.randomBetween(random, SPHERE_HEAD_START_TOP_MIN,  SPHERE_HEAD_START_TOP_MAX);
        float sphereHeadStartBase = Mth.randomBetween(random, SPHERE_HEAD_START_BASE_MIN, SPHERE_HEAD_START_BASE_MAX);

        float deltaYSquaredAtJunctionTop  = sphereHeadStartTop  * sphereHeadStartTop;
        float deltaYSquaredAtJunctionBase = sphereHeadStartBase * sphereHeadStartBase;

        // Makes distanceSquaredRescaledXZ + deltaYSquaredRemapped evaluate to 1.0 at the junction for both top and base.
        float distanceSquaredXZRescaleTop  = (1.0f - deltaYSquaredAtJunctionTop)  / (radius * radius);
        float distanceSquaredXZRescaleBase = (1.0f - deltaYSquaredAtJunctionBase) / (radius * radius);

        // To map delta-Y as follows:
        // 0      -> sphereHeadStart
        // height -> 1
        LinearFunction1f deltaYRemapTop  = LinearFunction1f.createAsMap(0,  heightTop,  sphereHeadStartTop,  1.0f);
        LinearFunction1f deltaYRemapBase = LinearFunction1f.createAsMap(0, -heightBase, sphereHeadStartBase, 1.0f);

        // To map the final sphere falloffs to 1 at the center of the crease, and 0 at the boundary.
        // This prevents the noise from excessively modulating the top but barely affecting the base.
        LinearFunction1f densityRemapTop  = LinearFunction1f.createAsMap(deltaYSquaredAtJunctionTop,  1.0f, 1.0f, 0.0f);
        LinearFunction1f densityRemapBase = LinearFunction1f.createAsMap(deltaYSquaredAtJunctionBase, 1.0f, 1.0f, 0.0f);

        // The noise's range is [-1.0f, 1.0f], but we need [0.0f, NOISE_MODULATION_AMOUNT].
        LinearFunction1f noiseValueToThreshold = LinearFunction1f.createAsMap(-1.0f, 1.0f, 0.0f, NOISE_MODULATION_AMOUNT);

        int heightLoopBoundTop = (int)heightTop;
        int heightLoopBoundBase = (int)heightBase;
        int radiusLoopBound = (int)radius;
        for (int y = heightLoopBoundTop; y >= -heightLoopBoundBase; y--) {
            boolean isTop = (y > 0);

            // 0      -> sphereHeadStart
            // height -> 1
            float deltaYSquaredRemapped = Mth.square(isTop ?
                    deltaYRemapTop .compute(y) :
                    deltaYRemapBase.compute(y)
            );

            for (int dz = -radiusLoopBound; dz <= radiusLoopBound; dz++) {
                for (int dx = -radiusLoopBound; dx <= radiusLoopBound; dx++) {
                    float distanceSquaredRescaledXZ = (dz * dz + dx * dx) * (isTop ?
                            distanceSquaredXZRescaleTop :
                            distanceSquaredXZRescaleBase
                    );
                    float distanceSquaredRescaled = distanceSquaredRescaledXZ + deltaYSquaredRemapped;

                    // This value will be positive on the inside and negative on the outside.
                    float density = isTop ?
                            densityRemapTop .compute(distanceSquaredRescaled) :
                            densityRemapBase.compute(distanceSquaredRescaled);

                    boolean shouldPlace = density > NOISE_MODULATION_AMOUNT;
                    if (!shouldPlace) {

                        // Range [0.0f, NOISE_MODULATION_AMOUNT]
                        float thresholdFromNoise = noiseValueToThreshold.compute(OpenSimplex2S.noise3_ImproveXZ(
                                noiseSeed,
                                dx * NOISE_FREQUENCY_XZ,
                                y * NOISE_FREQUENCY_Y,
                                dz * NOISE_FREQUENCY_XZ
                        ));

                        shouldPlace = (density > thresholdFromNoise);
                    }

                    if (shouldPlace) {
                        this.setBlock(worldGenLevel, origin.offset(dx, y, dz), Blocks.END_STONE.defaultBlockState());
                    }
                }
            }
        }

        return true;
    }
}
