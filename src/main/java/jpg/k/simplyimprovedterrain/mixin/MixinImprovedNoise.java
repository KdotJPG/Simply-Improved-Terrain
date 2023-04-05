package jpg.k.simplyimprovedterrain.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImprovedNoise.class)
public class MixinImprovedNoise {

    @Shadow
    public @Final double xo;

    @Shadow
    public @Final double yo;

    @Shadow
    public @Final double zo;

    // Borrowed and modified
    /**
     * @author K.jpg
     * @reason Domain Rotation
     */
    @Overwrite
    public double noise(double x, double y, double z, double shelfParam1, double shelfParam2) {

        // Domain Rotation!
        double xz = x + z;
        double s2 = xz * -0.211324865405187;
        double yy = y * 0.577350269189626;
        x += s2 + yy;
        z += s2 + yy;
        y = xz * -0.577350269189626 + yy;

        double d0 = x + this.xo;
        double d1 = y + this.yo;
        double d2 = z + this.zo;
        int i = Mth.floor(d0);
        int j = Mth.floor(d1);
        int k = Mth.floor(d2);
        double d3 = d0 - (double)i;
        double d4 = d1 - (double)j;
        double d5 = d2 - (double)k;
        double d6 = Mth.smoothstep(d3);
        double d7 = Mth.smoothstep(d4);
        double d8 = Mth.smoothstep(d5);

        // Shelf code removed. It doesn't seem to be used anywhere other than terrain gen, which this mod changes.

        return ((ImprovedNoise)(Object)this).sampleAndLerp(i, j, k, d3, d4, d5, d6, d7, d8);
    }

}