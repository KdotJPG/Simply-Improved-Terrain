package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.RandomPatchFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Replaces pseudo-normal distribution with ellipsoidal.
 * Visual impact: ★★★☆☆
 */
@Mixin(RandomPatchFeature.class)
public class MixinRandomPatchFeature {

    /**
     * @author K.jpg
     * @reason Sphericality
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<RandomPatchConfiguration> featurePlaceContext) {
        RandomPatchConfiguration randomPatchConfiguration = featurePlaceContext.config();
        RandomSource random = featurePlaceContext.random();
        BlockPos origin = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();

        float xzSpread = (randomPatchConfiguration.xzSpread() + 1) * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;
        float ySpread = (randomPatchConfiguration.ySpread() + 1) * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;

        int placementCount = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < randomPatchConfiguration.tries(); ++i) {

            // Isotropic placement
            DistributionUtils.sampleEllipsoidCenterBiasedSpread(xzSpread, ySpread, random, (dx, dy, dz) -> mutableBlockPos.setWithOffset(
                    origin, Math.round(dx), Math.round(dy), Math.round(dz)
            ));

            if ((randomPatchConfiguration.feature().value()).place(worldGenLevel, featurePlaceContext.chunkGenerator(), random, mutableBlockPos)) {
                ++placementCount;
            }
        }

        return (placementCount > 0);
    }


}
