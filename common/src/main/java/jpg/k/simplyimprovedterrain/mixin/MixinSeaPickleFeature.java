package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SeaPickleFeature;
import net.minecraft.world.level.levelgen.feature.configurations.CountConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Replaces pseudo-normal distribution with circular..
 * Visual impact: ★★★☆☆
 */
@Mixin(SeaPickleFeature.class)
public class MixinSeaPickleFeature {

    private static final float PLACEMENT_RADIUS = 8.0f * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;

    /**
     * @author K.jpg
     * @reason Isotropic distribution
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<CountConfiguration> featurePlaceContext) {
        int placedCount = 0;
        RandomSource random = featurePlaceContext.random();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        BlockPos blockPos = featurePlaceContext.origin();
        int attemptCount = featurePlaceContext.config().count().sample(random);

        for (int i = 0; i < attemptCount; ++i) {

            // Isotropic distribution
            BlockPos placementBlockPos = DistributionUtils.sampleCircleCenterBiasedSpread(PLACEMENT_RADIUS, random, (dx, dz) -> {
                int x = blockPos.getX() + Math.round(dx);
                int z = blockPos.getZ() + Math.round(dz);
                int y = worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                return new BlockPos(x, y, z);
            });

            BlockState blockStateToPlace = Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, random.nextInt(4) + 1);
            if (worldGenLevel.getBlockState(placementBlockPos).is(Blocks.WATER) && blockStateToPlace.canSurvive(worldGenLevel, placementBlockPos)) {
                worldGenLevel.setBlock(placementBlockPos, blockStateToPlace, 2);
                ++placedCount;
            }
        }

        return placedCount > 0;
    }
}
