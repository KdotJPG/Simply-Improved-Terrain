package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
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

    @Inject(method = "func_241855_a", at = @At("HEAD"), cancellable = true)
    public void inject_func_241855_a(ISeedReader p_241855_1_, ChunkGenerator p_241855_2_, Random p_241855_3_, BlockPos p_241855_4_, SphereReplaceConfig p_241855_5_,
            CallbackInfoReturnable<Boolean> cir) {

        // Choose a radius range roughly 0.75x to 1.25x the defined radius
        int configuredRadius = p_241855_5_.radius.func_242259_a(p_241855_3_);
        int radiusMin = (configuredRadius * 3 + 2) >> 2;
        int radiusRange = configuredRadius >> 1;

        // Choose the radius
        int radiusBase = (radiusRange < 1 ? 0 : p_241855_3_.nextInt(radiusRange)) + radiusMin;

        // An offset of slightly less than sqrt2-1 improves visual results.
        // It's similar to the recommendation to use N.5 radii, in this article:
        // - https://www.redblobgames.com/grids/circle-drawing/
        // except it also avoids the 3x3 square without requiring a special case.
        float radius = radiusBase + 0.41421356f;

        // In the actual loop, we will only work with ints, so we can convert now.
        int radiusSqInt = (int)(radius * radius);
        int radiusBound = radiusBase + 1;

        boolean flag = false;
        BlockPos.Mutable currentBlockPos = BlockPos.ZERO.func_239590_i_();
        for (int x = p_241855_4_.getX() - radiusBound; x <= p_241855_4_.getX() + radiusBound; ++x) {
            for (int z = p_241855_4_.getZ() - radiusBound; z <= p_241855_4_.getZ() + radiusBound; ++z) {
                int dx = x - p_241855_4_.getX();
                int dz = z - p_241855_4_.getZ();
                if (dx * dx + dz * dz <= radiusSqInt) {
                    for (int y = p_241855_4_.getY() - p_241855_5_.field_242809_d; y <= p_241855_4_.getY() + p_241855_5_.field_242809_d; ++y) {
                        currentBlockPos.setPos(x, y, z);
                        Block block = p_241855_1_.getBlockState(currentBlockPos).getBlock();

                        for (BlockState blockState : p_241855_5_.targets) {
                            if (blockState.isIn(block)) {
                                p_241855_1_.setBlockState(currentBlockPos, p_241855_5_.state, 2);
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
