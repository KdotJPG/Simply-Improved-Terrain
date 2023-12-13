package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.NetherForestVegetationFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NetherForestVegetationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Replaces pseudo-normal distribution with ellipsoidal.
 * Visual impact: ★★★☆☆
 */
@Mixin(value = NetherForestVegetationFeature.class, priority = 250)
public class MixinNetherForestVegetationFeature {

    /**
     * @author K.jpg
     * @reason Isotropic spread.
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<NetherForestVegetationConfig> featurePlaceContext) {
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        BlockPos origin = featurePlaceContext.origin();
        BlockState blockState = worldGenLevel.getBlockState(origin.below());
        NetherForestVegetationConfig netherForestVegetationConfig = featurePlaceContext.config();
        RandomSource random = featurePlaceContext.random();

        if (!blockState.is(BlockTags.NYLIUM)) {
            return false;
        }

        int originY = origin.getY();
        if (originY >= worldGenLevel.getMinBuildHeight() + 1 && originY + 1 < worldGenLevel.getMaxBuildHeight()) {
            int placementCount = 0;

            for (int i = 0; i < netherForestVegetationConfig.spreadWidth * netherForestVegetationConfig.spreadWidth; ++i) {

                // Isotropic spread.
                BlockPos placementBlockPos = DistributionUtils.sampleEllipsoidCenterBiasedSpread(
                        netherForestVegetationConfig.spreadWidth * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE,
                        netherForestVegetationConfig.spreadHeight * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE,
                        random,
                        (dx, dy, dz) -> origin.offset(Math.round(dx), Math.round(dy), Math.round(dz))
                );

                BlockState placementBlockState = netherForestVegetationConfig.stateProvider.getState(random, placementBlockPos);
                if (worldGenLevel.isEmptyBlock(placementBlockPos) && placementBlockPos.getY() > worldGenLevel.getMinBuildHeight()
                        && placementBlockState.canSurvive(worldGenLevel, placementBlockPos)) {
                    worldGenLevel.setBlock(placementBlockPos, placementBlockState, 2);
                    ++placementCount;
                }
            }

            return (placementCount > 0);
        }

        return false;
    }
}
