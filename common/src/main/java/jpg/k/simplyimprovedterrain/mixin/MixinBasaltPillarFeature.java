package jpg.k.simplyimprovedterrain.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.BasaltPillarFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BasaltPillarFeature.class)
public abstract class MixinBasaltPillarFeature {

    private static final int SPREAD_RADIUS = 4;
    private static final int GUARANTEED_SPREAD_RADIUS_SQUARED = 1;

    // Related to https://www.redblobgames.com/grids/circle-drawing/#aesthetics
    private static final int SPREAD_RADIUS_SQUARED_ISH = SPREAD_RADIUS * (SPREAD_RADIUS + 1);

    /**
     * @author K.jpg
     * @reason Euclidean falloff
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> featurePlaceContext) {
        BlockPos blockPos = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        RandomSource randomSource = featurePlaceContext.random();

        if (worldGenLevel.isEmptyBlock(blockPos) && !worldGenLevel.isEmptyBlock(blockPos.above())) {
            BlockPos.MutableBlockPos columnBlockPos = blockPos.mutable();
            BlockPos.MutableBlockPos hangOffBlockPos = blockPos.mutable();

            while (worldGenLevel.isEmptyBlock(columnBlockPos)) {
                if (worldGenLevel.isOutsideBuildHeight(columnBlockPos)) {
                    return true;
                }

                worldGenLevel.setBlock(columnBlockPos, Blocks.BASALT.defaultBlockState(), 2);
                placeHangOff(worldGenLevel, randomSource, hangOffBlockPos.setWithOffset(columnBlockPos, Direction.NORTH));
                placeHangOff(worldGenLevel, randomSource, hangOffBlockPos.setWithOffset(columnBlockPos, Direction.SOUTH));
                placeHangOff(worldGenLevel, randomSource, hangOffBlockPos.setWithOffset(columnBlockPos, Direction.WEST));
                placeHangOff(worldGenLevel, randomSource, hangOffBlockPos.setWithOffset(columnBlockPos, Direction.EAST));
                columnBlockPos.move(Direction.DOWN);
            }

            columnBlockPos.move(Direction.UP);
            placeBaseHangOff(worldGenLevel, randomSource, hangOffBlockPos.setWithOffset(columnBlockPos, Direction.NORTH));
            placeBaseHangOff(worldGenLevel, randomSource, hangOffBlockPos.setWithOffset(columnBlockPos, Direction.SOUTH));
            placeBaseHangOff(worldGenLevel, randomSource, hangOffBlockPos.setWithOffset(columnBlockPos, Direction.WEST));
            placeBaseHangOff(worldGenLevel, randomSource, hangOffBlockPos.setWithOffset(columnBlockPos, Direction.EAST));
            columnBlockPos.move(Direction.DOWN);
            BlockPos.MutableBlockPos spreadBlockPos = new BlockPos.MutableBlockPos();

            for (int z = -SPREAD_RADIUS; z <= SPREAD_RADIUS; ++z) {
                for (int x = -SPREAD_RADIUS; x <= SPREAD_RADIUS; ++x) {

                    // Euclidean randomness threshold and loop mask
                    int distanceSquared = x * x + z * z;
                    if (distanceSquared >= SPREAD_RADIUS_SQUARED_ISH) continue;

                    spreadBlockPos.set(columnBlockPos.offset(x, 0, z));

                    boolean shouldPlace = distanceSquared <= GUARANTEED_SPREAD_RADIUS_SQUARED;
                    if (!shouldPlace) {

                        // Zero at GUARANTEED_SPREAD_RADIUS_SQUARED
                        int distanceTransformed = distanceSquared - GUARANTEED_SPREAD_RADIUS_SQUARED;

                        // Create a strong inclination to place towards the center.
                        distanceTransformed = distanceTransformed * distanceTransformed * distanceTransformed;

                        // Sample within the range of the above.
                        int sample = randomSource.nextInt((SPREAD_RADIUS_SQUARED_ISH - GUARANTEED_SPREAD_RADIUS_SQUARED) *
                                (SPREAD_RADIUS_SQUARED_ISH - GUARANTEED_SPREAD_RADIUS_SQUARED) *
                                (SPREAD_RADIUS_SQUARED_ISH - GUARANTEED_SPREAD_RADIUS_SQUARED)
                        );

                        shouldPlace = (sample >= distanceTransformed);
                    }

                    if (shouldPlace) {
                        int downwardPlacementIterationsRemaining = 3;
                        while (worldGenLevel.isEmptyBlock(hangOffBlockPos.setWithOffset(spreadBlockPos, Direction.DOWN))) {
                            spreadBlockPos.move(Direction.DOWN);
                            --downwardPlacementIterationsRemaining;
                            if (downwardPlacementIterationsRemaining <= 0) {
                                break;
                            }
                        }

                        if (!worldGenLevel.isEmptyBlock(hangOffBlockPos.setWithOffset(spreadBlockPos, Direction.DOWN))) {
                            worldGenLevel.setBlock(spreadBlockPos, Blocks.BASALT.defaultBlockState(), 2);
                        }
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Shadow
    protected abstract void placeBaseHangOff(LevelAccessor levelAccessor, RandomSource randomSource, BlockPos blockPos);

    @Shadow
    protected abstract boolean placeHangOff(LevelAccessor levelAccessor, RandomSource randomSource, BlockPos blockPos);

}
