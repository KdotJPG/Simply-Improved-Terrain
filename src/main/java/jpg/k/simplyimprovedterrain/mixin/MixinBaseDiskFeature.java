package jpg.k.simplyimprovedterrain.mixin;

import java.util.Iterator;
import java.util.Random;

import jpg.k.simplyimprovedterrain.terrain.OpenSimplex2S;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.BaseDiskFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseDiskFeature.class)
public class MixinBaseDiskFeature {

    private static final double NOISE_FREQUENCY_XZ = 0.15;
    private static final double NOISE_FREQUENCY_Y = 0.15;
    private static final float RADIUS_MIN_RATIO_TO_CONFIG = 0.7f;
    private static final float RADIUS_MAX_RATIO_TO_CONFIG = 1.45f;
    private static final float RADIUS_PADDING = 0.5f;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    public void injectGenerate(FeaturePlaceContext<DiskConfiguration> featurePlaceContext, CallbackInfoReturnable<Boolean> cir) {
        DiskConfiguration diskFeatureConfig = featurePlaceContext.config();
        Random random = featurePlaceContext.random();
        BlockPos blockPos = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        boolean isFallingBlockDisk = diskFeatureConfig.state().getBlock() instanceof FallingBlock;
        long worldSeed = featurePlaceContext.level().getSeed();

        // Height range configuration
        int yPos = blockPos.getY();
        int yMax = yPos + diskFeatureConfig.halfHeight();
        int yMin = yPos - diskFeatureConfig.halfHeight() - 1;

        // Set up radius variation.
        float minNoisedRadius, maxNoisedRadius;
        int configuredRadius = diskFeatureConfig.radius().sample(random);
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
        BlockPos.MutableBlockPos currentBlockPos = BlockPos.ZERO.mutable();
        for (int x = blockPos.getX() - radiusBound; x <= blockPos.getX() + radiusBound; x++) {
            for (int z = blockPos.getZ() - radiusBound; z <= blockPos.getZ() + radiusBound; z++) {
                int distSq = Mth.square(x - blockPos.getX()) + Mth.square(z - blockPos.getZ());

                // No blocks will be placed outside of maximumm radius.
                if (distSq >= maxNoisedRadiusSqInt) continue;

                // Always place blocks within minimum radius.
                boolean isInRange = distSq <= minNoisedRadiusSqInt;

                // Between that range, the noise mixed with the falloff curve decides.
                if (!isInRange) {

                    // Smooth Euclidean-based polynomial falloff curve
                    float falloff = Mth.square(maxNoisedRadius * maxNoisedRadius - distSq);

                    // Get noise, and convert range.
                    float noise = OpenSimplex2S.noise3_ImproveXZ(worldSeed,
                            x * NOISE_FREQUENCY_XZ,
                            blockPos.getY() * NOISE_FREQUENCY_Y,
                            z * NOISE_FREQUENCY_XZ);
                    noise = noise * 0.5f + 0.5f;

                    // Noise value = 0 means we subtract nothing from the falloff, so 0 occurs at max radius.
                    // Noise value = 1 means we subtract a value from the falloff so that 0 occurs at min radius.
                    falloff -= noise * falloffAtMinRadius;
                    isInRange = (falloff > 0);

                }

                // Let's place blocks.
                if (isInRange) {
                    boolean placedBlockThisColumn = false;

                    // Place blocks vertically downward.
                    for (int y = yMax; y >= yMin; y--) {
                        currentBlockPos.set(x, y, z);
                        BlockState blockState = worldGenLevel.getBlockState(currentBlockPos);
                        Block block = blockState.getBlock();
                        boolean placedBlockThisPosition = false;

                        // In strict placement range, replace the block if it's one of the target blocks to replace.
                        if (y > yMin) {
                            for (BlockState targetReplacementBlock : diskFeatureConfig.targets()) {
                                if (targetReplacementBlock.is(block)) {
                                    worldGenLevel.setBlock(currentBlockPos, diskFeatureConfig.state(), 2);
                                    placedSomething = true;
                                    placedBlockThisPosition = true;
                                    break;
                                }
                            }
                        }

                        // In extended range (one past strict), if we're placing falling blocks, back them up with sandstone.'
                        if (isFallingBlockDisk && placedBlockThisColumn && blockState.isAir()) {
                            BlockState sandStoneReplacement = diskFeatureConfig.state().is(Blocks.RED_SAND) ?
                                    Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
                            worldGenLevel.setBlock(new BlockPos(x, y + 1, z), sandStoneReplacement, 2);
                        }

                        placedBlockThisColumn = placedBlockThisPosition;
                    }
                }
            }
        }

        cir.setReturnValue(placedSomething);
        cir.cancel();
    }

}
