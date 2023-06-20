package jpg.k.simplyimprovedterrain.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DensityFunctions.MulOrAdd.class)
public class FixinMulOrAdd {

    @Inject(method = "mapAll", at = @At("RETURN"), cancellable = true)
    public void injectMapAll(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> cir) {
        cir.setReturnValue(visitor.apply(cir.getReturnValue()));
    }

}
