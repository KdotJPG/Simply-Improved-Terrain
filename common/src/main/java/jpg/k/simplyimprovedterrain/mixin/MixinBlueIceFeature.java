package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.BlueIceFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Replaces the pseudonormal-based distribution with an ellipsoid-based one.
 * Visual impact: ★★★☆☆ (when excavated)
 */
@Mixin(BlueIceFeature.class)
public class MixinBlueIceFeature {

    private static final int MAX_PLACEMENT_ATTEMPTS = 200;
    private static final float PLACEMENT_RADIUS_XZ = 4.0f * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;
    private static final float PLACEMENT_RADIUS_Y = 5.5f * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;
    private static final float PLACEMENT_OFFSET_Y = -0.5f;

    /**
     * @author K.jpg
     * @reason Pseudonormal spread -> isotropic.
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> featurePlaceContext) {
        BlockPos origin = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        RandomSource random = featurePlaceContext.random();

        if (origin.getY() > worldGenLevel.getSeaLevel() - 1) {
            return false;
        }

        if (!worldGenLevel.getBlockState(origin).is(Blocks.WATER) && !worldGenLevel.getBlockState(origin.below()).is(Blocks.WATER)) {
            return false;
        }

        boolean hasPackedIceAboveOrAdjacent = false;
        for (Direction direction : Direction.values()) {
            if (direction != Direction.DOWN && worldGenLevel.getBlockState(origin.relative(direction)).is(Blocks.PACKED_ICE)) {
                hasPackedIceAboveOrAdjacent = true;
                break;
            }
        }

        if (!hasPackedIceAboveOrAdjacent) {
            return false;
        }

        worldGenLevel.setBlock(origin, Blocks.BLUE_ICE.defaultBlockState(), 2);

        for (int i = 0; i < MAX_PLACEMENT_ATTEMPTS; ++i) {

            BlockPos placementBlockPos = DistributionUtils.sampleEllipsoidCenterBiasedSpread(
                    PLACEMENT_RADIUS_XZ, PLACEMENT_RADIUS_Y, random,
                    (dx, dy, dz) -> {

                        // Vanilla sampled an uneven vertical distribution. Offset for a closer resemblance.
                        dy += PLACEMENT_OFFSET_Y;

                        // Vanilla has this thing where the vertical displacement affects the XZ radius, possibly vanishingly.
                        // Sphere/ellipsoid offset generation computes all three coordinates at once, but that doesn't mean
                        // we can't modify the XZ coordinates retroactively.
                        if (dy < 2) {
                            float retroactiveRadiusXZ = PLACEMENT_RADIUS_XZ + dy / 2;

                            // No placement when the radius drops to or below zero.
                            if (retroactiveRadiusXZ <= 0) return null;

                            float xzScale = retroactiveRadiusXZ * (1.0f / PLACEMENT_RADIUS_XZ);
                            dx *= xzScale;
                            dz *= xzScale;
                        }

                        return origin.offset(Math.round(dx), Math.round(dy), Math.round(dz));
                    }
            );

            if (placementBlockPos != null) {
                BlockState blockStateHere = worldGenLevel.getBlockState(placementBlockPos);
                if (blockStateHere.isAir() || blockStateHere.is(Blocks.WATER) || blockStateHere.is(Blocks.PACKED_ICE) || blockStateHere.is(Blocks.ICE)) {
                    for (Direction direction : Direction.values()) {
                        BlockState neighborBlockState = worldGenLevel.getBlockState(placementBlockPos.relative(direction));
                        if (neighborBlockState.is(Blocks.BLUE_ICE)) {
                            worldGenLevel.setBlock(placementBlockPos, Blocks.BLUE_ICE.defaultBlockState(), 2);
                            break;
                        }
                    }
                }
            }
        }

        return true;
    }

}
