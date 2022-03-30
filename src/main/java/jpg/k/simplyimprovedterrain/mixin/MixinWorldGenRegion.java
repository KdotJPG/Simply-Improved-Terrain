package jpg.k.simplyimprovedterrain.mixin;

import net.minecraft.world.biome.IBiomeMagnifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import jpg.k.simplyimprovedterrain.biome.CachedScatteredBiomeMagnifier;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.ColumnFuzzedBiomeMagnifier;
import net.minecraft.world.gen.WorldGenRegion;

@Mixin(WorldGenRegion.class)
public class MixinWorldGenRegion {

    @Redirect(method = "<init>(Lnet/minecraft/world/server/ServerWorld;Ljava/util/List;)V", at = @At(
            value = "NEW",
            target = "Lnet/minecraft/world/biome/BiomeManager;"
    ))
    private BiomeManager redirectBiomeManager(BiomeManager.IBiomeReader biomeReader, long biomeMagnifierSeed, IBiomeMagnifier biomeMagnifier) {
        if (biomeMagnifier instanceof ColumnFuzzedBiomeMagnifier) {
            biomeMagnifier = CachedScatteredBiomeMagnifier.INSTANCE;
        }

        return new BiomeManager(biomeReader, biomeMagnifierSeed, biomeMagnifier);
    }

}
