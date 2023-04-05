package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.BaseDiskFeature;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BaseDiskFeature.class)
public class MixinBaseDiskFeature {

    /**
     * @author K.jpg
     * @reason No pointy circles >:^(
     */
    @Overwrite
    public boolean place(WorldGenLevel world, ChunkGenerator chunkGenerator, Random random, BlockPos origin, DiskConfiguration config) {

        // Choose a radius range roughly 0.75x to 1.25x the defined radius
        int configuredRadius = config.radius.sample(random);
        int radiusMin = (configuredRadius * 3 + 2) >> 2;
        int radiusRange = configuredRadius >> 1;

        // Choose the radius
        int radiusBase = (radiusRange < 1 ? 0 : random.nextInt(radiusRange)) + radiusMin;

        // An offset of slightly less than sqrt2-1 improves visual results.
        // It's similar to the recommendation to use N.5 radii, in this article:
        // - https://www.redblobgames.com/grids/circle-drawing/
        // except it also avoids the 3x3 square without requiring a special case.
        float radius = radiusBase + 0.41421356f;

        // In the actual loop, we will only work with ints, so we can convert now.
        int radiusSqInt = (int)(radius * radius);
        int radiusBound = radiusBase + 1;

        boolean placed = false;
        BlockPos.MutableBlockPos currentBlockPos = BlockPos.ZERO.mutable();
        for (int x = origin.getX() - radiusBound; x <= origin.getX() + radiusBound; ++x) {
            for (int z = origin.getZ() - radiusBound; z <= origin.getZ() + radiusBound; ++z) {
                int dx = x - origin.getX();
                int dz = z - origin.getZ();
                if (dx * dx + dz * dz <= radiusSqInt) {
                    for (int y = origin.getY() - config.halfHeight; y <= origin.getY() + config.halfHeight; ++y) {
                        currentBlockPos.set(x, y, z);
                        Block block = world.getBlockState(currentBlockPos).getBlock();

                        for (BlockState blockState : config.targets) {
                            if (blockState.is(block)) {
                                world.setBlock(currentBlockPos, config.state, 2);
                                placed = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        return placed;
    }

}
