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
		
		int configuredRadius = p_241855_5_.radius.func_242259_a(p_241855_3_);
		int radiusMin = (configuredRadius * 3 + 2) >> 2;
		int radiusRange = configuredRadius >> 1;
		
		int radiusBase = p_241855_3_.nextInt(radiusRange) + radiusMin;
		float radius = radiusBase + 0.41421356f; // Optimize appearance
		int radiusBound = radiusBase + 1;

		boolean flag = false;
		for(int j = p_241855_4_.getX() - radiusBound; j <= p_241855_4_.getX() + radiusBound; ++j) {
			for(int k = p_241855_4_.getZ() - radiusBound; k <= p_241855_4_.getZ() + radiusBound; ++k) {
				int l = j - p_241855_4_.getX();
				int i1 = k - p_241855_4_.getZ();
				if (l * l + i1 * i1 < radius * radius) {
					for(int j1 = p_241855_4_.getY() - p_241855_5_.field_242809_d; j1 <= p_241855_4_.getY() + p_241855_5_.field_242809_d; ++j1) {
						BlockPos blockpos = new BlockPos(j, j1, k);
						Block block = p_241855_1_.getBlockState(blockpos).getBlock();

						for(BlockState blockstate : p_241855_5_.targets) {
							if (blockstate.isIn(block)) {
								p_241855_1_.setBlockState(blockpos, p_241855_5_.state, 2);
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
