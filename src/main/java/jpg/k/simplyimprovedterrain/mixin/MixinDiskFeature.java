package jpg.k.simplyimprovedterrain.mixin;

import java.util.Iterator;
import java.util.Random;

import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.DiskFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
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
    public void injectGenerate(FeatureContext<DiskFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
        DiskFeatureConfig diskFeatureConfig = (DiskFeatureConfig)context.getConfig();
        Random random = context.getRandom();
        BlockPos blockPos = context.getOrigin();
        StructureWorldAccess structureWorldAccess = context.getWorld();
        boolean isFallingBlockDisk = diskFeatureConfig.state.getBlock() instanceof FallingBlock;

        // Height range configuration
        int yPos = blockPos.getY();
        int yMax = yPos + diskFeatureConfig.halfHeight;
        int yMin = yPos - diskFeatureConfig.halfHeight - 1;

        // Choose a radius range roughly 0.75x to 1.25x the defined radius
        int configuredRadius = diskFeatureConfig.radius.get(random);
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
                    boolean placedBlockThisColumn = false;

                    for(int y = yMax; y >= yMin; --y) {
                        currentBlockPos.set(x, y, z);
                        BlockState blockState = structureWorldAccess.getBlockState(currentBlockPos);
                        Block block = blockState.getBlock();
                        boolean placedBlockThisPosition = false;
                        if (y > yMin) {
                            Iterator var21 = diskFeatureConfig.targets.iterator();

                            while(var21.hasNext()) {
                                BlockState blockState2 = (BlockState)var21.next();
                                if (blockState2.isOf(block)) {
                                    structureWorldAccess.setBlockState(currentBlockPos, diskFeatureConfig.state, 2);
                                    flag = true;
                                    placedBlockThisPosition = true;
                                    break;
                                }
                            }
                        }

                        if (isFallingBlockDisk && placedBlockThisColumn && blockState.isAir()) {
                            BlockState blockState3 = diskFeatureConfig.state.isOf(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.getDefaultState() : Blocks.SANDSTONE.getDefaultState();
                            structureWorldAccess.setBlockState(new BlockPos(x, y + 1, z), blockState3, 2);
                        }

                        placedBlockThisColumn = placedBlockThisPosition;
                    }
                }
            }
        }


        cir.setReturnValue(flag);
        cir.cancel();
    }

}
