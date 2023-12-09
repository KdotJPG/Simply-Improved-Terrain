package jpg.k.simplyimprovedterrain.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import jpg.k.simplyimprovedterrain.terrain.OpenSimplex2S;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.BambooFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds noise to the podzol distributions emanating from the bases of the plants.
 * Visual impact: ★★★☆☆  (dense clustering and built-in radius variation in vanilla help to an extent)
 */
@Mixin(BambooFeature.class)
public class MixinBambooFeature {

    private static final float MIN_RADIUS = 1.4f;
    private static final float MAX_RADIUS = 7.25f;

    private static final float RADIUS_NOISE_MODULATION_RATIO = 0.8f;
    private static final double NOISE_FREQUENCY_XZ = 0.1875;
    private static final double NOISE_FREQUENCY_Y = 0.1875;
    private static final long NOISE_SEED_FLIP_MASK = -0x0845BF5BC9DCEE05L;

    @WrapOperation(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/RandomSource;nextInt(I)I",
                    ordinal = 1
            )
    )
    private int computeBoundingRadius(
            RandomSource random,
            int range,
            Operation<Integer> original,
            @Share("maxNoisedSurfaceRadiusSquared") LocalFloatRef maxNoisedSurfaceRadiusSquaredRef,
            @Share("falloffAtMinRadius") LocalFloatRef falloffAtMinRadiusRef,
            @Share("maxNoisedSurfaceRadiusSquaredTruncated") LocalIntRef maxNoisedSurfaceRadiusSquaredTruncatedRef,
            @Share("minNoisedSurfaceRadiusSquaredTruncated") LocalIntRef minNoisedSurfaceRadiusSquaredTruncatedRef
    ) {

        // Radius range for noise modulation.
        float maxNoisedSurfaceRadius = Mth.randomBetween(random, MIN_RADIUS, MAX_RADIUS);
        float minNoisedSurfaceRadius = maxNoisedSurfaceRadius * RADIUS_NOISE_MODULATION_RATIO;
        maxNoisedSurfaceRadiusSquaredRef.set(maxNoisedSurfaceRadius * maxNoisedSurfaceRadius);
        falloffAtMinRadiusRef.set(Mth.square(maxNoisedSurfaceRadius * maxNoisedSurfaceRadius - minNoisedSurfaceRadius * minNoisedSurfaceRadius));

        // Make the loop nicer.
        maxNoisedSurfaceRadiusSquaredTruncatedRef.set((int)(maxNoisedSurfaceRadius * maxNoisedSurfaceRadius));
        minNoisedSurfaceRadiusSquaredTruncatedRef.set((int)(minNoisedSurfaceRadius * minNoisedSurfaceRadius));

        // Loop bound needs ceil() to counter the `n * n + o * o <= k * k` check.
        // If this weren't there, we could use floor() or equivalently, since it's positive, a plain integer cast.
        // Then -1 to counter the +1 applied to the original result (so we could just use an int-cast after all, if we wanted).
        return Mth.ceil(maxNoisedSurfaceRadius) - 1;
    }

    @WrapWithCondition(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
                    ordinal = 0
            )
    )
    private boolean shouldPlaceBlock(
            WorldGenLevel worldGenLevel,
            BlockPos blockPos,
            BlockState blockState,
            int flag,
            FeaturePlaceContext<ProbabilityFeatureConfiguration> featurePlaceContext,
            @Share("maxNoisedSurfaceRadiusSquared") LocalFloatRef maxNoisedSurfaceRadiusSquaredRef,
            @Share("falloffAtMinRadius") LocalFloatRef falloffAtMinRadiusRef,
            @Share("maxNoisedSurfaceRadiusSquaredTruncated") LocalIntRef maxNoisedSurfaceRadiusSquaredTruncatedRef,
            @Share("minNoisedSurfaceRadiusSquaredTruncated") LocalIntRef minNoisedSurfaceRadiusSquaredTruncatedRef
    ) {

        int worldX = blockPos.getX();
        int worldZ = blockPos.getZ();
        int dx = worldX - featurePlaceContext.origin().getX();
        int dz = worldZ - featurePlaceContext.origin().getZ();
        int distanceSquared = dx * dx + dz * dz;

        // No surface blocks will be placed outside thw maximum radius.
        if (distanceSquared >= maxNoisedSurfaceRadiusSquaredTruncatedRef.get()) return false;

        // Always place blocks within the minimum radius.
        boolean isInRange = distanceSquared <= minNoisedSurfaceRadiusSquaredTruncatedRef.get();

        int surfaceY = worldGenLevel.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;

        // Between that range, the noise mixed with the falloff curve decides.
        if (!isInRange) {

            // Smooth Euclidean-based polynomial falloff curve
            float falloff = Mth.square(maxNoisedSurfaceRadiusSquaredRef.get() - distanceSquared);

            // Get noise and convert range to [0, 1].
            long noiseSeed = worldGenLevel.getSeed() ^ NOISE_SEED_FLIP_MASK;
            float noise = OpenSimplex2S.noise3_ImproveXZ(noiseSeed,
                    worldX   * NOISE_FREQUENCY_XZ,
                    surfaceY * NOISE_FREQUENCY_Y,
                    worldZ   * NOISE_FREQUENCY_XZ
            ) * 0.5f + 0.5f;

            // Noise value = 0 means we compare the falloff with zero, which occurs at the max noise variation radius.
            // Noise value = 1 means we compare the falloff with the value that occurs at the min noise variation radius instead.
            isInRange = (falloff > noise * falloffAtMinRadiusRef.get());
        }

        return isInRange;
    }
}
