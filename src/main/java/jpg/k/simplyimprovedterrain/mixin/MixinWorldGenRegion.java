package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.biome.CachedScatteredBiomeMagnifier;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.FuzzyOffsetConstantColumnBiomeZoomer;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeZoomer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldGenRegion.class)
public class MixinWorldGenRegion {

    @Redirect(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V", at = @At(
            value = "NEW",
            target = "Lnet/minecraft/world/level/biome/BiomeManager;"
    ))
    private BiomeManager redirectBiomeManager(BiomeManager.NoiseBiomeSource biomeReader, long biomeMagnifierSeed, BiomeZoomer biomeMagnifier) {
        if (biomeMagnifier instanceof FuzzyOffsetConstantColumnBiomeZoomer) {
            biomeMagnifier = CachedScatteredBiomeMagnifier.INSTANCE;
        }

        return new BiomeManager(biomeReader, biomeMagnifierSeed, biomeMagnifier);
    }

}
