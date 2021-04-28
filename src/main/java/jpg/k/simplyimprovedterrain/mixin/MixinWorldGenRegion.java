package jpg.k.simplyimprovedterrain.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jpg.k.simplyimprovedterrain.biome.CachedScatteredBiomeMagnifier;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.ColumnFuzzedBiomeMagnifier;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.NoiseChunkGenerator;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.gen.WorldGenRegion;

@Mixin(WorldGenRegion.class)
public class MixinWorldGenRegion {

    @Shadow
    private BiomeManager biomeManager;

    @Shadow
    private long seed;

    @Inject(method = "<init>(Lnet/minecraft/world/server/ServerWorld;Ljava/util/List;)V", at = @At("TAIL"))
    private void injectConstructor(ServerWorld p_i50698_1_, List<IChunk> p_i50698_2_, CallbackInfo ci) {

        if (p_i50698_1_.getChunkProvider().getChunkGenerator() instanceof NoiseChunkGenerator
                && p_i50698_1_.func_230315_m_().getMagnifier() instanceof ColumnFuzzedBiomeMagnifier) {
            this.biomeManager = new BiomeManager((WorldGenRegion)(Object)this, BiomeManager.func_235200_a_(this.seed), CachedScatteredBiomeMagnifier.INSTANCE);
        }

    }

}
