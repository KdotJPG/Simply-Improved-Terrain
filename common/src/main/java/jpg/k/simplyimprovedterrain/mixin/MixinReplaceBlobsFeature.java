package jpg.k.simplyimprovedterrain.mixin;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import jpg.k.simplyimprovedterrain.math.RotatedEllipsoid;
import jpg.k.simplyimprovedterrain.terrain.OpenSimplex2S;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.ReplaceBlobsFeature;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceSphereConfiguration;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Replaces octahedral spread with ellipsoidal; adds noise for variation.
 * Visual impact: ★★★★★
 */
@Mixin(ReplaceBlobsFeature.class)
public class MixinReplaceBlobsFeature extends Feature<ReplaceSphereConfiguration> {

    private static final double NOISE_FREQUENCY_XZ = 0.15;
    private static final double NOISE_FREQUENCY_Y = 0.15;
    private static final float RADIUS_MIN_RATIO_TO_CONFIG = 0.8f;
    private static final float RADIUS_MAX_RATIO_TO_CONFIG = 1.25f;
    private static final long NOISE_SEED_BIT_FLIP = -0x49A446DC64F6A7B7L;

    public MixinReplaceBlobsFeature(Codec<ReplaceSphereConfiguration> codec) {
        super(codec);
    }

    /**
     * @author K.jpg
     * @reason Blobbier blobs!
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<ReplaceSphereConfiguration> featurePlaceContext) {
        ReplaceSphereConfiguration replaceSphereConfiguration = featurePlaceContext.config();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        RandomSource random = featurePlaceContext.random();
        Block targetBlock = replaceSphereConfiguration.targetState.getBlock();
        long noiseSeed = featurePlaceContext.level().getSeed() ^ NOISE_SEED_BIT_FLIP;

        BlockPos targetBlockPos = findTarget(worldGenLevel, featurePlaceContext.origin().mutable().clamp(
                Direction.Axis.Y, worldGenLevel.getMinBuildHeight() + 1, worldGenLevel.getMaxBuildHeight() - 1), targetBlock);
        if (targetBlockPos == null) {
            return false;
        }

        int radiusA = replaceSphereConfiguration.radius().sample(random);
        int radiusB = replaceSphereConfiguration.radius().sample(random);
        int radiusC = replaceSphereConfiguration.radius().sample(random);
        Matrix3f ellipseRotation = DistributionUtils.randomRotation3D(random);

        float radiusAMax = Math.min(LevelChunkSection.SECTION_WIDTH, radiusA * RADIUS_MAX_RATIO_TO_CONFIG);
        float radiusBMax = Math.min(LevelChunkSection.SECTION_WIDTH, radiusB * RADIUS_MAX_RATIO_TO_CONFIG);
        float radiusCMax = Math.min(LevelChunkSection.SECTION_WIDTH, radiusC * RADIUS_MAX_RATIO_TO_CONFIG);
        RotatedEllipsoid ellipsoidLarge = RotatedEllipsoid.createFromRotationAndRadii(ellipseRotation, radiusAMax, radiusBMax, radiusCMax);

        float radiusAMin = radiusAMax * (RADIUS_MIN_RATIO_TO_CONFIG / RADIUS_MAX_RATIO_TO_CONFIG);
        float radiusBMin = radiusBMax * (RADIUS_MIN_RATIO_TO_CONFIG / RADIUS_MAX_RATIO_TO_CONFIG);
        float radiusCMin = radiusCMax * (RADIUS_MIN_RATIO_TO_CONFIG / RADIUS_MAX_RATIO_TO_CONFIG);
        RotatedEllipsoid ellipsoidSmall = RotatedEllipsoid.createFromRotationAndRadii(ellipseRotation, radiusAMin, radiusBMin, radiusCMin);

        boolean placed = false;
        BlockPos.MutableBlockPos currentBlockPos = new BlockPos.MutableBlockPos();
        float radiusMax = Math.max(radiusAMax, Math.max(radiusBMax, radiusCMax));
        int radiusLoopBound = (int)radiusMax;
        int yStart = Math.max(-radiusLoopBound, worldGenLevel.getMinBuildHeight() - targetBlockPos.getY());
        int yEnd = Math.min(radiusLoopBound, worldGenLevel.getMaxBuildHeight() - 1 - targetBlockPos.getY());
        for (int dz = -radiusLoopBound; dz <= radiusLoopBound; dz++) {
            for (int dx = -radiusLoopBound; dx <= radiusLoopBound; dx++) {
                for (int dy = yStart; dy <= yEnd; dy++) {

                    // If outside sphere containing large ellipsoid, skip.
                    if (dz * dz + dx * dx + dy * dy >= radiusMax * radiusMax) continue;

                    // If outside large ellipsoid, skip.
                    float ellipsoidValueLarge = ellipsoidLarge.compute(dx, dy, dz);
                    if (ellipsoidValueLarge >= 1.0f) continue;

                    currentBlockPos.setWithOffset(targetBlockPos, dx, dy, dz);

                    // If outside small ellipsoid, skip depending on noise and falloff.
                    float ellipsoidValueSmall = ellipsoidSmall.compute(dx, dy, dz);
                    if (ellipsoidValueSmall >= 1.0f) {

                        // Get noise and rescale range to [0, 1].
                        float noiseValue = OpenSimplex2S.noise3_ImproveXZ(
                                noiseSeed,
                                currentBlockPos.getX() * NOISE_FREQUENCY_XZ,
                                currentBlockPos.getY() * NOISE_FREQUENCY_Y,
                                currentBlockPos.getZ() * NOISE_FREQUENCY_XZ
                        ) * 0.5f + 0.5f;

                        // Slide between small and large ellipsoids using the noise. Check solidity against this value.
                        float noisyEllipsoidValue = Mth.lerp(noiseValue, ellipsoidValueSmall, ellipsoidValueLarge);
                        if (noisyEllipsoidValue >= 1.0f) continue;
                    }

                    BlockState blockState = worldGenLevel.getBlockState(currentBlockPos);
                    if (blockState.is(targetBlock)) {
                        this.setBlock(worldGenLevel, currentBlockPos, replaceSphereConfiguration.replaceState);
                        placed = true;
                    }
                }
            }
        }

        return placed;
    }

    @Shadow @Nullable
    private static BlockPos findTarget(LevelAccessor levelAccessor, BlockPos.MutableBlockPos mutableBlockPos, Block block) {
        throw new NotImplementedException();
    }

}
