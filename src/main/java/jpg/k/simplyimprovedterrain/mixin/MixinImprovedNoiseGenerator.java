package jpg.k.simplyimprovedterrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.ImprovedNoiseGenerator;

@Mixin(ImprovedNoiseGenerator.class)
public class MixinImprovedNoiseGenerator {

    private static final double ROOT3OVER2 = Math.sqrt(3.0) / 2.0;

    // Borrowed and modified
    /**
     * @author K.jpg
     * @reason Domain Rotation
     */
    @Overwrite
    public double noise(double x, double y, double z, double shelfParam1, double shelfParam2) {

        // Start each noise layer in the center of a rotated cell to give spawn variety
        // in absence of full per-layer offsetting (removed).
        y += ROOT3OVER2;

        // Domain Rotation!
        double xz = x + z;
        double s2 = xz * -0.211324865405187;
        double yy = y * 0.577350269189626;
        x += s2 + yy;
        z += s2 + yy;
        y = xz * -0.577350269189626 + yy;

        // Base/offset/fade.
        // Random offset removed as it globally affects the value distributions of fractals (subtle details!).
        int xBase = MathHelper.floor(x);
        int yBase = MathHelper.floor(y);
        int zBase = MathHelper.floor(z);
        double xDelta = x - (double)xBase;
        double yDelta = y - (double)yBase;
        double zDelta = z - (double)zBase;
        double fadeX = MathHelper.smoothstep(xDelta);
        double fadeY = MathHelper.smoothstep(yDelta);
        double fadeZ = MathHelper.smoothstep(zDelta);

        // Shelf code removed. It doesn't seem to be used anywhere other than terrain gen, which this mod changes.

        return ((ImprovedNoiseGenerator)(Object)this).sampleAndLerp(xBase, yBase, zBase, xDelta, yDelta, zDelta, fadeX, fadeY, fadeZ);
    }

}