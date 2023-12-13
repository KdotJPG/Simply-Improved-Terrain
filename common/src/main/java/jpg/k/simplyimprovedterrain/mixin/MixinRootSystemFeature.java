package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.RootSystemFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RootSystemConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.function.Predicate;

/**
 * Replaces pseudo-normal distribution with ellipsoidal.
 * Visual impact: ★★★☆☆ (when excavated)
 */
@Mixin(value = RootSystemFeature.class, priority = 250)
public class MixinRootSystemFeature {

    /**
     * @author K.jpg
     * @reason Isotropic distribution
     */
    @Overwrite
    private static void placeRootedDirt(WorldGenLevel worldGenLevel, RootSystemConfiguration rootSystemConfiguration, RandomSource random, int x, int z, BlockPos.MutableBlockPos placementMutableBlockPos) {
        int rootRadius = rootSystemConfiguration.rootRadius;
        Predicate<BlockState> predicate = (blockState) -> blockState.is(rootSystemConfiguration.rootReplaceable);

        for (int i = 0; i < rootSystemConfiguration.rootPlacementAttempts; ++i) {

            // Isotropic placement
            DistributionUtils.sampleCircleCenterBiasedSpread(rootRadius, random, (dx, dz) -> placementMutableBlockPos.setWithOffset(
                    placementMutableBlockPos, Math.round(dx), 0, Math.round(dz)
            ));

            if (predicate.test(worldGenLevel.getBlockState(placementMutableBlockPos))) {
                worldGenLevel.setBlock(placementMutableBlockPos, rootSystemConfiguration.rootStateProvider.getState(random, placementMutableBlockPos), 2);
            }

            placementMutableBlockPos.setX(x);
            placementMutableBlockPos.setZ(z);
        }
    }

    /**
     * @author K.jpg
     * @reason Mojang, you gotta move away from that pseudonormal distribution!
     */
    @Overwrite
    private static void placeRoots(WorldGenLevel worldGenLevel, RootSystemConfiguration rootSystemConfiguration, RandomSource random, BlockPos blockPos, BlockPos.MutableBlockPos placementMutableBlockPos) {
        float hangingRootRadius = rootSystemConfiguration.hangingRootRadius * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;
        float hangingRootsVerticalSpan = rootSystemConfiguration.hangingRootsVerticalSpan * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;

        for (int i = 0; i < rootSystemConfiguration.hangingRootPlacementAttempts; ++i) {

            // Isotropic placement
            DistributionUtils.sampleEllipsoidCenterBiasedSpread(hangingRootRadius, hangingRootsVerticalSpan, random, (dx, dy, dz) -> placementMutableBlockPos.setWithOffset(
                    blockPos, Math.round(dx), Math.round(dy), Math.round(dz)
            ));

            if (worldGenLevel.isEmptyBlock(placementMutableBlockPos)) {
                BlockState blockState = rootSystemConfiguration.hangingRootStateProvider.getState(random, placementMutableBlockPos);
                if (blockState.canSurvive(worldGenLevel, placementMutableBlockPos) && worldGenLevel.getBlockState(placementMutableBlockPos.above()).isFaceSturdy(worldGenLevel, placementMutableBlockPos, Direction.DOWN)) {
                    worldGenLevel.setBlock(placementMutableBlockPos, blockState, 2);
                }
            }
        }
    }
}
