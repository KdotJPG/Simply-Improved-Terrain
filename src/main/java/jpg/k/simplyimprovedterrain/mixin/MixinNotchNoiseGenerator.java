package jpg.k.simplyimprovedterrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.ImprovedNoiseGenerator;

@Mixin(ImprovedNoiseGenerator.class)
public class MixinNotchNoiseGenerator {

	@Shadow
	public @Final double xCoord;

	@Shadow
	public @Final double yCoord;

	@Shadow
	public @Final double zCoord;
   
	// Borrowed and modified
    @Inject(method = "func_215456_a", at = @At("HEAD"), cancellable = true)
	public void inject_func_215456_a(double x, double y, double z, double shelfParam1, double shelfParam2, CallbackInfoReturnable<Double> cir) {
    	
    	// Domain Rotation!
        double xz = x + z;
        double s2 = xz * -0.211324865405187;
        double yy = y * 0.577350269189626;
        x += s2 + yy;
        z += s2 + yy;
        y = xz * -0.577350269189626 + yy;
    	
        double d0 = x + this.xCoord;
        double d1 = y + this.yCoord;
        double d2 = z + this.zCoord;
        int i = MathHelper.floor(d0);
        int j = MathHelper.floor(d1);
        int k = MathHelper.floor(d2);
        double d3 = d0 - (double)i;
        double d4 = d1 - (double)j;
        double d5 = d2 - (double)k;
        double d6 = MathHelper.perlinFade(d3);
        double d7 = MathHelper.perlinFade(d4);
        double d8 = MathHelper.perlinFade(d5);
        double d9;
        
        // Shelf code removed. I don't think it's important anywhere other than the terrain gen.
        // Maybe I will re-add it a la NeoNotchNoise.java, for mods which call upon this.
        // For now, I want to improve Nether biome placement.

        double value = ((ImprovedNoiseGenerator)(Object)this).func_215459_a(i, j, k, d3, d4, d5, d6, d7, d8);
        cir.setReturnValue(value);
        cir.cancel();
    }
}