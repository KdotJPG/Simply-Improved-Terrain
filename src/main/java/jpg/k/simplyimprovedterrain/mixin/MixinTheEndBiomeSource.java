package jpg.k.simplyimprovedterrain.mixin;

import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.TheEndBiomeSource;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jpg.k.simplyimprovedterrain.mixinapi.ISimplexNoiseSampler;
import jpg.k.simplyimprovedterrain.util.noise.MetaballEndIslandNoise;

@Mixin(value = TheEndBiomeSource.class, priority = 500)
public class MixinTheEndBiomeSource {

    @Shadow
    private long seed;

    @Shadow
    private Biome field_26700;

    @Shadow
    private Biome field_26701;

    @Shadow
    private Biome field_26702;

    @Shadow
    private Biome field_26703;

    @Shadow
    private Biome field_26704;

    @Shadow
    private @Final SimplexNoiseSampler noise;

    // Must be Overwrite for other mods (e.g. Abnormals Core) to mix into its return statements instead of only Vanilla's.
    @Overwrite
    public Biome getBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ) {
        if ((long)biomeX * (long)biomeX + (long)biomeZ * (long)biomeZ <= 0x10000L) {
            return this.field_26700;
        } else {
            double f = MetaballEndIslandNoise.INSTANCE.getNoise(((ISimplexNoiseSampler)(Object) noise).getPermTable(), biomeX * 4 + 2, biomeZ * 4 + 2);
            return f > 40.0F?this.field_26701:(f >= 0.0F?this.field_26702:(f < -20.0F?this.field_26703:this.field_26704));
        }
    }

    @Inject(method = "getNoiseAt", at = @At("HEAD"), cancellable = true)
    private static void injectGetNoiseAt(SimplexNoiseSampler simplex, int biomeX, int biomeZ, CallbackInfoReturnable<Float> cir) {
        float f = (float)MetaballEndIslandNoise.INSTANCE.getNoise(((ISimplexNoiseSampler)(Object)simplex).getPermTable(), biomeX * 8, biomeZ * 8);
        cir.setReturnValue(f);
        cir.cancel();
    }

}
