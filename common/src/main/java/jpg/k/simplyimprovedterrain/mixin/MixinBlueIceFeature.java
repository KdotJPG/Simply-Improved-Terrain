package jpg.k.simplyimprovedterrain.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.BlueIceFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Replaces the pseudonormal-based distribution with an ellipsoid-based one.
 * Visual impact: ★★★☆☆ (when excavated)
 */
@Mixin(BlueIceFeature.class)
public class MixinBlueIceFeature {

    private static final float PLACEMENT_RADIUS_XZ = 4.0f * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;
    private static final float PLACEMENT_RADIUS_Y = 5.5f * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;
    private static final float PLACEMENT_OFFSET_Y = -0.5f;

    @WrapOperation(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At(
                    target = "Lnet/minecraft/core/BlockPos;offset(III)Lnet/minecraft/core/BlockPos;",
                    value = "INVOKE"
            )
    )
    private BlockPos sampleBlockOffset(BlockPos origin, int dxOriginal, int dyOriginal, int dzOriginal,
                                       Operation<BlockPos> original, FeaturePlaceContext<NoneFeatureConfiguration> featurePlaceContext) {
        RandomSource random = featurePlaceContext.random();

        return DistributionUtils.sampleEllipsoidCenterBiasedSpread(
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
                        // (just return the origin, which we know has already been placed into)
                        if (retroactiveRadiusXZ <= 0) return origin;

                        float xzScale = retroactiveRadiusXZ * (1.0f / PLACEMENT_RADIUS_XZ);
                        dx *= xzScale;
                        dz *= xzScale;
                    }

                    return origin.offset(Math.round(dx), Math.round(dy), Math.round(dz));
                }
        );
    }

    @WrapOperation(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At(
                    target = "Lnet/minecraft/util/RandomSource;nextInt(I)I",
                    value = "INVOKE"
            )
    )
    private int noOpRandomNextInt(RandomSource random, int range, Operation<Integer> original) {
        return 0;
    }

}
