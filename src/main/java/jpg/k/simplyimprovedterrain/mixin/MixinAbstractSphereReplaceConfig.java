package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.SphereReplaceConfig;
import net.minecraft.world.gen.feature.AbstractSphereReplaceConfig;

@Mixin(AbstractSphereReplaceConfig.class)
public class MixinAbstractSphereReplaceConfig {

    /**
     * @author K.jpg
     * @reason No pointy circles >:^(
     */
    @Overwrite
    public boolean place(ISeedReader world, ChunkGenerator chunkGenerator, Random random, BlockPos origin, SphereReplaceConfig config) {

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
        BlockPos.Mutable currentBlockPos = BlockPos.ZERO.mutable();
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
