package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.biome.FiddledBiomeResolver;
import jpg.k.simplyimprovedterrain.biome.WrappedFiddledBiomeResolver;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinRandomState;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGenerator {

    @SuppressWarnings("ConstantConditions")
    @ModifyVariable(method = "doCreateBiomes", at = @At("FIELD"), ordinal = 0)
    private BiomeResolver modifyBiomeSource(BiomeResolver biomeSourceOriginal, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunkAccess) {

        // Match BiomeManager sample displacements where possible.
        if (biomeSourceOriginal instanceof FiddledBiomeResolver fiddledBiomeResolver &&
                (Object) randomState instanceof IMixinRandomState mixinRandomState) {
            long biomeManagerSeed = BiomeManager.obfuscateSeed(mixinRandomState.worldSeed());
            return new WrappedFiddledBiomeResolver(fiddledBiomeResolver, biomeManagerSeed);
        }

        return biomeSourceOriginal;
    }

}
