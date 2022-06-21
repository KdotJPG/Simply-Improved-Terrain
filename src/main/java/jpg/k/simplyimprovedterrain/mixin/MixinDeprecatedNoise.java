package jpg.k.simplyimprovedterrain.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.apache.commons.lang3.NotImplementedException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Note: ImprovedNoise.class is so-named (to my understanding) because it is an implementation of 2002 "Improved (Perlin) Noise"
 * - ImprovedNoise.class is Perlin noise
 * - PerlinNoise.class is fractal noise on Perlin
 * - SimplexNoise.class is Simplex noise
 * - PerlinSimplexNoise.class is fractal noise on Simplex.
 *
 * "Improved" refers to gradient and fade-curve improvements compared to the 1980s implementation, but 2002 Improved Noise
 * does not address Perlin's characteristic grid-following tendency. We will improve this here, through domain rotation.
 * https://noiseposti.ng/posts/2022-01-16-The-Perlin-Problem-Moving-Past-Square-Noise.html#promote-effective-fixes
 * https://noiseposti.ng/posts/2022-01-16-The-Perlin-Problem-Moving-Past-Square-Noise.html#domain-rotation
 */
@Mixin(ImprovedNoise.class)
public abstract class MixinDeprecatedNoise {

    @Shadow protected abstract double sampleAndLerp(int xb, int yb, int zb, double dx, double dy, double dz, double dy2);
    @Shadow protected abstract int p(int i);
    @Shadow protected static double gradDot(int p, double v, double e, double f) {
        throw new NotImplementedException();
    }

    @Inject(method = "noise(DDDDD)D", at = @At("HEAD"), cancellable = true)
    public void injectNoise(double x, double y, double z, double shelfParam1, double shelfParam2, CallbackInfoReturnable<Double> cir) {

        // Domain Rotation! Remove the square slices from the main XZ world plane.
        // Always domain-rotate your Perlin, folks.
        // https://noiseposti.ng/posts/2022-01-16-The-Perlin-Problem-Moving-Past-Square-Noise.html#domain-rotation
        double xz = x + z;
        double s2 = xz * -0.211324865405187;
        double yy = y * 0.577350269189626;
        x += s2 + yy;
        z += s2 + yy;
        y = xz * -0.577350269189626 + yy;

        // Random-offsetting is a bit redundant given the noise is already seeded.
        // Either it offers no effective variation (then wouldn't be needed)
        // or offers seed-global variation (instead, let us strive for local variation by design!)
        //x += this.originX;
        //y += this.originY;
        //z += this.originZ;

        int xb = Mth.floor(x);
        int yb = Mth.floor(y);
        int zb = Mth.floor(z);
        double dx = x - xb;
        double dy = y - yb;
        double dz = z - zb;

        double value;
        if (shelfParam1 != 0) value = sampleAndLerpWithNewShelves(xb, yb, zb, dx, dy, dz);
        else value = sampleAndLerp(xb, yb, zb, dx, dy, dz, dy);

        cir.setReturnValue(value);
    }

    private double sampleAndLerpWithNewShelves(int xb, int yb, int zb, double dx, double dy, double dz) {
        int h0YZ = this.p(xb);
        int h1YZ = this.p(xb + 1);
        int h00Z = this.p(h0YZ + yb);
        int h01Z = this.p(h0YZ + yb + 1);
        int h10Z = this.p(h1YZ + yb);
        int h11Z = this.p(h1YZ + yb + 1);
        double h = gradDotWithNewShelves(this.p(h00Z + zb), dx, dy, dz);
        double r = gradDotWithNewShelves(this.p(h10Z + zb), dx - 1.0D, dy, dz);
        double s = gradDotWithNewShelves(this.p(h01Z + zb), dx, dy - 1.0D, dz);
        double t = gradDotWithNewShelves(this.p(h11Z + zb), dx - 1.0D, dy - 1.0D, dz);
        double u = gradDotWithNewShelves(this.p(h00Z + zb + 1), dx, dy, dz - 1.0D);
        double v = gradDotWithNewShelves(this.p(h10Z + zb + 1), dx - 1.0D, dy, dz - 1.0D);
        double w = gradDotWithNewShelves(this.p(h01Z + zb + 1), dx, dy - 1.0D, dz - 1.0D);
        double x = gradDotWithNewShelves(this.p(h11Z + zb + 1), dx - 1.0D, dy - 1.0D, dz - 1.0D);
        double y = Mth.smoothstep(dx);
        double z = Mth.smoothstep(dy);
        double aa = Mth.smoothstep(dz);
        return Mth.lerp3(y, z, aa, h, r, s, t, u, v, w, x);
    }

    private static double gradDotWithNewShelves(int hash256, double dx, double dy, double dz) {

        // Translate remaining 4 hash bits into a "shelf" position.
        double shelfPosition = (hash256 >> 4) * (-3.0 / 16.0);

        // Accounting for the domain rotation, skip this vertex contribution
        // if we're the decided amount below this vertex contribution.
        if (dx + dy + dz < shelfPosition) return 0;

        // Otherwise do the regular thing.
        else return gradDot(hash256, dx, dy, dz);
    }


}
