package jpg.k.simplyimprovedterrain.mixin;

import java.util.List;

import jpg.k.simplyimprovedterrain.biome.CachedScatteredBiomeAccessType;
import net.minecraft.world.ChunkRegion;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.biome.source.HorizontalVoronoiBiomeAccessType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.biome.source.BiomeAccess;

/**
 * Created by user on 5/17/2021.
 */
@Mixin(ChunkRegion.class)
public class MixinChunkRegion {

    @Shadow
    private BiomeAccess biomeAccess;

    @Shadow
    private @Final long seed;

    @Inject(method = "<init>(Lnet/minecraft/server/world/ServerWorld;Ljava/util/List;Lnet/minecraft/world/chunk/ChunkStatus;I)V", at = @At("TAIL"))
    private void injectConstructor(ServerWorld world, List<Chunk> chunks, ChunkStatus chunkStatus, int i, CallbackInfo ci) {

        if (world.getChunkManager().getChunkGenerator() instanceof NoiseChunkGenerator
                && world.getDimension().getBiomeAccessType() instanceof HorizontalVoronoiBiomeAccessType) {
            this.biomeAccess = new BiomeAccess((ChunkRegion)(Object)this, BiomeAccess.hashSeed(this.seed), CachedScatteredBiomeAccessType.INSTANCE);
        }

    }

}
