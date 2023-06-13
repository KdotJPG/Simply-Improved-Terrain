package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.terrain.OpenSimplex2S;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DiskFeature.class)
public abstract class MixinDiskFeature {

    private static final double NOISE_FREQUENCY_XZ = 0.15;
    private static final double NOISE_FREQUENCY_Y = 0.15;
    private static final float RADIUS_MIN_RATIO_TO_CONFIG = 0.7f;
    private static final float RADIUS_MAX_RATIO_TO_CONFIG = 1.45f;
    private static final float RADIUS_PADDING = 0.5f;

    @Shadow protected abstract boolean placeColumn(DiskConfiguration diskConfiguration, WorldGenLevel worldGenLevel, RandomSource randomSource, int yMax, int yMin, BlockPos.MutableBlockPos mutableBlockPos);

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    public void injectGenerate(FeaturePlaceContext<DiskConfiguration> featurePlaceContext, CallbackInfoReturnable<Boolean> cir) {
        DiskConfiguration diskConfiguration = featurePlaceContext.config();
        BlockPos center = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        RandomSource randomSource = featurePlaceContext.random();
        long worldSeed = featurePlaceContext.level().getSeed();

        // Height range configuration
        int yPos = center.getY();
        int yMax = yPos + diskConfiguration.halfHeight();
        int yMin = yPos - diskConfiguration.halfHeight() - 1;

        // Set up radius variation.
        float minNoisedRadius, maxNoisedRadius;
        int configuredRadius = diskConfiguration.radius().sample(randomSource);
        if (configuredRadius == 1) {
            minNoisedRadius = maxNoisedRadius = 1; // Some mods use radius=1 for explicitly-single-block placements.
        } else {
            maxNoisedRadius = configuredRadius * RADIUS_MAX_RATIO_TO_CONFIG + RADIUS_PADDING;
            minNoisedRadius = configuredRadius * RADIUS_MIN_RATIO_TO_CONFIG + RADIUS_PADDING;
        }
        float falloffAtMinRadius = Mth.square(maxNoisedRadius * maxNoisedRadius - minNoisedRadius * minNoisedRadius);

        // Make the loop nicer.
        int minNoisedRadiusSqInt = (int)(minNoisedRadius * minNoisedRadius);
        int maxNoisedRadiusSqInt = (int)(maxNoisedRadius * maxNoisedRadius);
        int radiusBound = (int)maxNoisedRadius + 1;

        boolean placedSomething = false;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (BlockPos current : BlockPos.betweenClosed(center.offset(-radiusBound, 0, -radiusBound), center.offset(radiusBound, 0, radiusBound))) {
            int x = current.getX(), z = current.getZ();
            int dx = x - center.getX(), dz = z - center.getZ();
            int distSq = dx * dx + dz * dz;

            // No blocks will be placed outside thw maximum radius.
            if (distSq >= maxNoisedRadiusSqInt) continue;

            // Always place blocks within the minimum radius.
            boolean isInRange = distSq <= minNoisedRadiusSqInt;

            // Between that range, the noise mixed with the falloff curve decides.
            if (!isInRange) {

                // Smooth Euclidean-based polynomial falloff curve
                float falloff = Mth.square(maxNoisedRadius * maxNoisedRadius - distSq);

                // Get noise, and convert range.
                float noise = OpenSimplex2S.noise3_ImproveXZ(worldSeed,
                        x * NOISE_FREQUENCY_XZ,
                        yPos * NOISE_FREQUENCY_Y,
                        z * NOISE_FREQUENCY_XZ);
                noise = noise * 0.5f + 0.5f;

                // Noise value = 0 means we subtract nothing from the falloff, so 0 occurs at max radius.
                // Noise value = 1 means we subtract a value from the falloff so that 0 occurs at min radius.
                falloff -= noise * falloffAtMinRadius;
                isInRange = (falloff > 0);

            }

            // Let's place blocks.
            if (isInRange) {
                placedSomething |= this.placeColumn(diskConfiguration, worldGenLevel, randomSource, yMax, yMin, mutableBlockPos.set(current));
            }
        }

        cir.setReturnValue(placedSomething);
    }

}
