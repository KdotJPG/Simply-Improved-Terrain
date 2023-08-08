package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.ArrayBackedCache2D;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunk.class)
public class MixinNoiseChunk {

    @Shadow @Final private int firstCellX;
    @Shadow @Final private int firstCellZ;
    @Shadow @Final int cellWidth;

    @Inject(method = "wrapNew", at = @At(value = "RETURN"), cancellable = true)
    private void modifyWrapping(DensityFunction function, CallbackInfoReturnable<DensityFunction> cir) {
        if (cir.getReturnValue() instanceof NoiseChunk.Cache2D cache2D) {
            cir.setReturnValue(new ArrayBackedCache2D(firstCellX * cellWidth, firstCellZ * cellWidth, cache2D));
        }
    }

}
