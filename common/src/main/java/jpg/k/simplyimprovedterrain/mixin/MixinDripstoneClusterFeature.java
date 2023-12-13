package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import jpg.k.simplyimprovedterrain.math.RotatedEllipse;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.DripstoneClusterFeature;
import net.minecraft.world.level.levelgen.feature.DripstoneUtils;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DripstoneClusterConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Replaces axis-scaled rectangular placement with rotated ellipse placement.
 * Visual impact: ★☆☆☆☆ (the principle is strong, but here it's not always clear where the patch borders are anyway)
 */
@Mixin(value = DripstoneClusterFeature.class, priority = 250)
public abstract class MixinDripstoneClusterFeature {

    /**
     * @author K.jpg
     * @reason No rectangles allowed :P
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<DripstoneClusterConfiguration> featurePlaceContext) {
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        BlockPos origin = featurePlaceContext.origin();
        DripstoneClusterConfiguration dripstoneClusterConfiguration = featurePlaceContext.config();
        RandomSource random = featurePlaceContext.random();

        if (!DripstoneUtils.isEmptyOrWater(worldGenLevel, origin)) {
            return false;
        } else {
            int height = dripstoneClusterConfiguration.height.sample(random);
            float wetness = dripstoneClusterConfiguration.wetness.sample(random);
            float density = dripstoneClusterConfiguration.density.sample(random);
            int radiusDifference = dripstoneClusterConfiguration.maxDistanceFromEdgeAffectingChanceOfDripstoneColumn;
            float dripstoneChanceAtBoundary = dripstoneClusterConfiguration.chanceOfDripstoneColumnAtMaxDistanceFromCenter;

            // Ellipses
            float uRadius = dripstoneClusterConfiguration.radius.sample(random) * DistributionUtils.RADIUS_RATIO_CIRCLE_TO_SQUARE;
            float vRadius = dripstoneClusterConfiguration.radius.sample(random) * DistributionUtils.RADIUS_RATIO_CIRCLE_TO_SQUARE;
            float ellipseAngle = random.nextFloat() * Mth.TWO_PI;
            RotatedEllipse outerEllipse = RotatedEllipse.createFromAngleAndRadii(ellipseAngle, uRadius, vRadius);
            RotatedEllipse innerEllipse = RotatedEllipse.createFromAngleAndRadii(ellipseAngle, Math.max(0.0f, uRadius - radiusDifference), Math.max(0.0f, vRadius - radiusDifference));
            int radiusLoopBound = (int)Math.max(uRadius, vRadius);

            for (int dz = -radiusLoopBound; dz <= radiusLoopBound; ++dz) {
                for (int dx = -radiusLoopBound; dx <= radiusLoopBound; ++dx) {

                    // Outside the outer ellipse, place nothing.
                    float outerEllipseValue = outerEllipse.compute(dx, dz);
                    if (outerEllipseValue >= 1.0f) continue;

                    // Probability slide between ellipse boundaries, with a fixed 1.0f inside the inner ellipse.
                    float innerEllipseValue = innerEllipse.compute(dx, dz);
                    float chanceOfStalagmiteOrStalactite = this.getChanceOfStalagmiteOrStalactite(innerEllipseValue, outerEllipseValue, dripstoneChanceAtBoundary);

                    BlockPos columnPosXZ = origin.offset(dx, 0, dz);
                    this.placeColumn(worldGenLevel, random, columnPosXZ, dx, dz, wetness,
                            chanceOfStalagmiteOrStalactite, height, density, dripstoneClusterConfiguration);
                }
            }

            return true;
        }
    }

    private float getChanceOfStalagmiteOrStalactite(float innerEllipseDensity, float outerEllipseDensity, float dripstoneChanceAtBoundary) {

        // When either radius of the inner ellipse drops to zero, its density evaluates to infinity/NaN.
        if (!Float.isFinite(innerEllipseDensity)) return dripstoneChanceAtBoundary;

        // Probability is a fixed 1.0f while inside the inner ellipse.
        // This occurs when we're at least maxDistanceFromEdgeAffectingChanceOfDripstoneColumn units inside the outer ellipse.
        if (innerEllipseDensity <= 1.0f) return 1.0f;

        // Should only happen if maxDistanceFromEdgeAffectingChanceOfDripstoneColumn <= 0
        if (outerEllipseDensity >= innerEllipseDensity) return 1.0f;

        // 0 when innerEllipseDensity == 1
        // 1 when outerEllipseDensity == 1
        // monotonically increasing between, so long as both components are (which they are!).
        float slide = (innerEllipseDensity - 1) / (innerEllipseDensity - outerEllipseDensity);

        return Mth.lerp(slide, 1.0f, dripstoneChanceAtBoundary);
    }

    @Shadow
    protected abstract void placeColumn(WorldGenLevel worldGenLevel, RandomSource random, BlockPos columnPosXZ, int xOffset,
                                        int zOffset, float wetness, double chanceOfStalagmiteOrStalactite, int height, float density,
                                        DripstoneClusterConfiguration dripstoneClusterConfiguration);

}
