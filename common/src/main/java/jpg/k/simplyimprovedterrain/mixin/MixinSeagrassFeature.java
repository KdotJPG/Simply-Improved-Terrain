package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SeagrassFeature;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Replaces pseudo-normal distribution with circular..
 * Visual impact: ★★★☆☆
 */
@Mixin(SeagrassFeature.class)
public class MixinSeagrassFeature {

    private static final float PLACEMENT_RADIUS = 8.0f * DistributionUtils.RADIUS_RATIO_CIRCLE_TO_SQUARE;

    /**
     * @author K.jpg
     * @reason Isotropic distribution
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<ProbabilityFeatureConfiguration> featurePlaceContext) {
        boolean placed = false;
        RandomSource random = featurePlaceContext.random();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        BlockPos blockPos = featurePlaceContext.origin();
        ProbabilityFeatureConfiguration probabilityFeatureConfiguration = featurePlaceContext.config();

        // Isotropic distribution
        BlockPos placementBlockPos = DistributionUtils.sampleCircleCenterBiasedSpread(PLACEMENT_RADIUS, random, (dx, dz) -> {
            int x = blockPos.getX() + Math.round(dx);
            int z = blockPos.getZ() + Math.round(dz);
            int y = worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            return new BlockPos(x, y, z);
        });

        if (worldGenLevel.getBlockState(placementBlockPos).is(Blocks.WATER)) {
            boolean shouldBeTall = random.nextFloat() < probabilityFeatureConfiguration.probability;
            BlockState blockStateToPlace = shouldBeTall ? Blocks.TALL_SEAGRASS.defaultBlockState() : Blocks.SEAGRASS.defaultBlockState();
            if (blockStateToPlace.canSurvive(worldGenLevel, placementBlockPos)) {
                if (shouldBeTall) {
                    BlockState blockStateToPlaceUpper = blockStateToPlace.setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER);
                    BlockPos placementBlockPosUpper = placementBlockPos.above();
                    if (worldGenLevel.getBlockState(placementBlockPosUpper).is(Blocks.WATER)) {
                        worldGenLevel.setBlock(placementBlockPos, blockStateToPlace, 2);
                        worldGenLevel.setBlock(placementBlockPosUpper, blockStateToPlaceUpper, 2);
                    }
                } else {
                    worldGenLevel.setBlock(placementBlockPos, blockStateToPlace, 2);
                }

                placed = true;
            }
        }

        return placed;
    }

}
