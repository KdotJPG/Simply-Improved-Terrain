package jpg.k.simplyimprovedterrain.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.RandomPatchFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(RandomPatchFeature.class)
public class MixinRandomPatchFeature {

    /**
     * @author K.jpg
     * @reason Sphericality
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<RandomPatchConfiguration> featurePlaceContext) {
        RandomPatchConfiguration randomPatchConfiguration = featurePlaceContext.config();
        RandomSource randomSource = featurePlaceContext.random();
        BlockPos origin = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();

        int xzSpread = randomPatchConfiguration.xzSpread() + 1;
        int ySpread = randomPatchConfiguration.ySpread() + 1;

        int placementCount = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < randomPatchConfiguration.tries(); ++i) {

            // Isotropic placement
            float sphereY = randomSource.nextFloat() * 2.0f - 1.0f;
            float sphereTheta = randomSource.nextFloat() * Mth.TWO_PI;
            float sphereXZScale = Mth.sqrt(1.0f - sphereY * sphereY);

            // Same distribution as `sqrt(rand)`. Biases towards center; use a cube root for a true uniform spherical distribution.
            float radiusScale = 1.0f - Math.abs(randomSource.nextFloat() - randomSource.nextFloat());

            mutableBlockPos.setWithOffset(
                    origin,
                    Math.round(radiusScale * sphereXZScale * xzSpread * Mth.cos(sphereTheta)),
                    Math.round(radiusScale * sphereY * ySpread),
                    Math.round(radiusScale * sphereXZScale * xzSpread * Mth.sin(sphereTheta))
            );

            if ((randomPatchConfiguration.feature().value()).place(worldGenLevel, featurePlaceContext.chunkGenerator(), randomSource, mutableBlockPos)) {
                ++placementCount;
            }
        }

        return (placementCount > 0);
    }


}
