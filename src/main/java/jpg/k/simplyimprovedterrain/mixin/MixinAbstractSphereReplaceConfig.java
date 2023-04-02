package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import jpg.k.simplyimprovedterrain.util.noise.OpenSimplex2S;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.SphereReplaceConfig;
import net.minecraft.world.gen.feature.AbstractSphereReplaceConfig;

@Mixin(AbstractSphereReplaceConfig.class)
public class MixinAbstractSphereReplaceConfig {

    private static final double NOISE_FREQUENCY_XZ = 0.15;
    private static final double NOISE_FREQUENCY_Y = 0.15;
    private static final float RADIUS_MIN_RATIO_TO_CONFIG = 0.6f;
    private static final float RADIUS_MAX_RATIO_TO_CONFIG = 1.45f;
    private static final float RADIUS_PADDING = 0.5f;

    /**
     * @author K.jpg
     * @reason No pointy circles >:^( -- Update: no hyper-regular ones either.
     */
    @Overwrite
    public boolean place(ISeedReader world, ChunkGenerator chunkGenerator, Random random, BlockPos origin, SphereReplaceConfig config) {
        long worldSeed = world.getSeed();

        // Height range configuration
        int yPos = origin.getY();
        int yMax = yPos + config.halfHeight;
        int yMin = yPos - config.halfHeight - 1;

        // Set up radius variation.
        float minNoisedRadius, maxNoisedRadius;
        int configuredRadius = config.radius.sample(random);
        if (configuredRadius == 1) {
            minNoisedRadius = maxNoisedRadius = 1; // Some mods use radius=1 for explicitly-single-block placements.
        } else {
            maxNoisedRadius = configuredRadius * RADIUS_MAX_RATIO_TO_CONFIG + RADIUS_PADDING;
            minNoisedRadius = configuredRadius * RADIUS_MIN_RATIO_TO_CONFIG + RADIUS_PADDING;
        }
        float falloffAtMinRadius = MathHelper.square(maxNoisedRadius * maxNoisedRadius - minNoisedRadius * minNoisedRadius);

        // Make the loop nicer.
        int minNoisedRadiusSqInt = (int)(minNoisedRadius * minNoisedRadius);
        int maxNoisedRadiusSqInt = (int)(maxNoisedRadius * maxNoisedRadius);
        int radiusBound = (int)maxNoisedRadius + 1;

        boolean placedSomething = false;
        BlockPos.Mutable mutableBlockPos = new BlockPos.Mutable();
        for (BlockPos current : BlockPos.betweenClosed(origin.offset(-radiusBound, 0, -radiusBound), origin.offset(radiusBound, 0, radiusBound))) {
            int x = current.getX(), z = current.getZ();
            int dx = x - origin.getX(), dz = z - origin.getZ();
            int distSq = dx * dx + dz * dz;

            // No blocks will be placed outside thw maximum radius.
            if (distSq >= maxNoisedRadiusSqInt) continue;

            // Always place blocks within the minimum radius.
            boolean isInRange = distSq <= minNoisedRadiusSqInt;

            // Between that range, the noise mixed with the falloff curve decides.
            if (!isInRange) {

                // Smooth Euclidean-based polynomial falloff curve
                float falloff = MathHelper.square(maxNoisedRadius * maxNoisedRadius - distSq);

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
                placedSomething |= this.placeColumn(config, world, random, yMax, yMin, mutableBlockPos.set(current));
            }
        }

        return placedSomething;
    }

    @Unique
    private boolean placeColumn(SphereReplaceConfig config, ISeedReader world, Random random, int yMax, int yMin, BlockPos.Mutable mutableBlocKPos) {
        boolean placed = false;
        for (int y = yMin; y <= yMax; ++y) {
            mutableBlocKPos.setY(y);
            Block block = world.getBlockState(mutableBlocKPos).getBlock();

            for (BlockState blockState : config.targets) {
                if (blockState.is(block)) {
                    world.setBlock(mutableBlocKPos, config.state, 2);
                    placed = true;
                    break;
                }
            }
        }
        return placed;
    }

}
