package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.mixinapi.IMixinSimplexNoise;
import jpg.k.simplyimprovedterrain.util.noise.MetaballEndIslandNoise;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = TheEndBiomeSource.class, priority = 500)
public class MixinTheEndBiomeSource {

    @Shadow
    private @Final
    Biome end;

    @Shadow
    private @Final Biome highlands;

    @Shadow
    private @Final Biome midlands;

    @Shadow
    private @Final Biome islands;

    @Shadow
    private @Final Biome barrens;

    @Shadow
    private @Final SimplexNoise islandNoise;

    /**
     * @author K.jpg
     * @reason Match replacement end island noise.
     */
    @Overwrite
    public Biome getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        if ((long)biomeX * (long)biomeX + (long)biomeZ * (long)biomeZ <= 0x10000L) {
            return this.end;
        } else {
            double f = MetaballEndIslandNoise.INSTANCE.getNoise(((IMixinSimplexNoise)islandNoise).getPermTable(), biomeX * 4 + 2, biomeZ * 4 + 2);
            if (f > 40.0F) {
                return this.highlands;
            } else if (f >= 0.0F) {
                return this.midlands;
            } else {
                return f < -20.0F ? this.islands : this.barrens;
            }
        }
    }

    /**
     * @author K.jpg
     * @reason Replacement end island noise.
     */
    @Overwrite
    public static float getHeightValue(SimplexNoise simplex, int biomeX, int biomeZ) {
        int[] permTable = ((IMixinSimplexNoise) simplex).getPermTable();
        return (float)MetaballEndIslandNoise.INSTANCE.getNoise(permTable, biomeX * 8, biomeZ * 8);
    }

}
