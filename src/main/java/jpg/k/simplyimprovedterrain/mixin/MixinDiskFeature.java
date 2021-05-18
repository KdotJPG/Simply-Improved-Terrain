package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.DiskFeatureConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.DiskFeature;

@Mixin(DiskFeature.class)
public class MixinDiskFeature {

    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    public void injectGenerate(StructureWorldAccess structureWorldAccess, ChunkGenerator chunkGenerator, Random random, BlockPos blockPos, DiskFeatureConfig diskFeatureConfig,
                                     CallbackInfoReturnable<Boolean> cir) {

        // Choose a radius range roughly 0.75x to 1.25x the defined radius
        int configuredRadius = diskFeatureConfig.radius.getValue(random);
        int radiusMin = (configuredRadius * 3 + 2) >> 2;
        int radiusRange = configuredRadius >> 1;

        // Choose the radius
        int radiusBase = random.nextInt(radiusRange) + radiusMin;

        // An offset of slightly less than sqrt2-1 improves visual results.
        // It's similar to the recommendation to use N.5 radii, in this article:
        // - https://www.redblobgames.com/grids/circle-drawing/
        // except it also avoids the 3x3 square without requiring a special case.
        float radius = radiusBase + 0.41421356f; // Optimize appearance

        // In the actual loop, we will only work with ints, so we can convert now.
        int radiusSqInt = (int)(radius * radius);
        int radiusBound = radiusBase + 1;

        boolean flag = false;
        BlockPos.Mutable currentBlockPos = BlockPos.ORIGIN.mutableCopy();
        for (int x = blockPos.getX() - radiusBound; x <= blockPos.getX() + radiusBound; ++x) {
            for (int z = blockPos.getZ() - radiusBound; z <= blockPos.getZ() + radiusBound; ++z) {
                int dx = x - blockPos.getX();
                int dz = z - blockPos.getZ();
                if (dx * dx + dz * dz <= radiusSqInt) {
                    for (int y = blockPos.getY() - diskFeatureConfig.ySize; y <= blockPos.getY() + diskFeatureConfig.ySize; ++y) {
                        currentBlockPos.set(x, y, z);
                        Block block = structureWorldAccess.getBlockState(currentBlockPos).getBlock();

                        for (BlockState blockState : diskFeatureConfig.targets) {
                            if (blockState.isOf(block)) {
                                structureWorldAccess.setBlockState(currentBlockPos, diskFeatureConfig.state, 2);
                                flag = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        cir.setReturnValue(flag);
        cir.cancel();
    }

}
