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
    private Biome centerBiome;

    @Shadow
    private Biome highlandsBiome;

    @Shadow
    private Biome midlandsBiome;

    @Shadow
    private Biome smallIslandsBiome;

    @Shadow
    private Biome barrensBiome;

    @Shadow
    private @Final SimplexNoiseSampler noise;

    /**
     * Gets the island biome using the new island noise from Simply Improved Terrain.
     * Must be Overwrite for other mods (e.g. Abnormals Core) to mix into its return statements instead of only Vanilla's.
     * @author K.jpg
     */
    @Overwrite
    public Biome getBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ) {
        if ((long)biomeX * (long)biomeX + (long)biomeZ * (long)biomeZ <= 0x10000L) {
            return this.centerBiome;
        } else {
            double f = MetaballEndIslandNoise.INSTANCE.getNoise(((ISimplexNoiseSampler)(Object) noise).getPermTable(), biomeX * 4 + 2, biomeZ * 4 + 2);
            if (f > 40.0F) {
                return this.highlandsBiome;
            } else if (f >= 0.0F) {
                return this.midlandsBiome;
            } else {
                return f < -20.0F ? this.smallIslandsBiome : this.barrensBiome;
            }
        }
    }

    @Inject(method = "getNoiseAt", at = @At("HEAD"), cancellable = true)
    private static void injectGetNoiseAt(SimplexNoiseSampler simplex, int biomeX, int biomeZ, CallbackInfoReturnable<Float> cir) {
        float f = (float)MetaballEndIslandNoise.INSTANCE.getNoise(((ISimplexNoiseSampler)(Object)simplex).getPermTable(), biomeX * 8, biomeZ * 8);
        cir.setReturnValue(f);
        cir.cancel();
    }

}
