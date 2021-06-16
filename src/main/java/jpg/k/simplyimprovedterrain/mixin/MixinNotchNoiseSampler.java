package jpg.k.simplyimprovedterrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.PerlinNoiseSampler;

@Mixin(PerlinNoiseSampler.class)
public abstract class MixinNotchNoiseSampler {

    @Shadow
    public @Final double originX;

    @Shadow
    public @Final double originY;

    @Shadow
    public @Final double originZ;

    @Shadow protected abstract double sample(int sectionX, int sectionY, int sectionZ, double localX, double localY, double localZ, double fadeLocalY);

    // Borrowed and modified
    @Inject(method = "sample(DDDDD)D", at = @At("HEAD"), cancellable = true)
    public void injectSample(double x, double y, double z, double shelfParam1, double shelfParam2, CallbackInfoReturnable<Double> cir) {

        // Domain Rotation!
        double xz = x + z;
        double s2 = xz * -0.211324865405187;
        double yy = y * 0.577350269189626;
        x += s2 + yy;
        z += s2 + yy;
        y = xz * -0.577350269189626 + yy;

        x += this.originX;
        y += this.originY;
        z += this.originZ;
        int xb = MathHelper.floor(x);
        int yb = MathHelper.floor(y);
        int zb = MathHelper.floor(z);
        double localX = x - (double)xb;
        double localY = y - (double)yb;
        double localZ = z - (double)zb;

        // Shelf code removed. I don't think it's important anywhere other than the terrain gen.
        // Maybe I will re-add it a la NeoNotchNoise.java, for mods which call upon this.
        // For now, I want to improve Nether biome placement.

        double value = this.sample(xb, yb, zb, localX, localY, localZ, localY);
        cir.setReturnValue(value);
        cir.cancel();
    }

}