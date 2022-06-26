package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.biome.BiomeFiddleHelper;
import jpg.k.simplyimprovedterrain.biome.FiddledBiomeResolver;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinClimateSampler;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinSimplexNoise;
import jpg.k.simplyimprovedterrain.terrain.MetaballEndIslandNoise;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TheEndBiomeSource.class)
public class MixinTheEndBiomeSource implements FiddledBiomeResolver {

    private static final int OUTER_ISLANDS_BLOCK_DISTANCE = 1024;

    private static final double THRESHOLD_HIGHLANDS = 0.25;
    private static final double THRESHOLD_MIDLANDS = -0.0625;
    private static final double THRESHOLD_SMALL_ISLANDS = -0.21875;

    @Shadow @Final private Holder<Biome> end;
    @Shadow @Final private Holder<Biome> highlands;
    @Shadow @Final private Holder<Biome> midlands;
    @Shadow @Final private Holder<Biome> islands;
    @Shadow @Final private Holder<Biome> barrens;

    /**
     * @author K.jpg
     * @reason Simply Improved Terrain island noise.
     */
    @Overwrite
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return this.getBlockNoiseBiome(QuartPos.toBlock(quartX), QuartPos.toBlock(quartY), QuartPos.toBlock(quartZ), sampler);
    }

    @Override
    public Holder<Biome> getFiddledNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler, long fiddleSeed) {
        return BiomeFiddleHelper.sampleFiddled(quartX, quartY, quartZ, fiddleSeed,
                (blockX, blockY, blockZ) -> this.getBlockNoiseBiome(blockX, blockY, blockZ, sampler));
    }

    private Holder<Biome> getBlockNoiseBiome(int blockX, int blockY, int blockZ, Climate.Sampler sampler) {
        if (Mth.square((long)blockX) + Mth.square((long)blockZ) <= OUTER_ISLANDS_BLOCK_DISTANCE * OUTER_ISLANDS_BLOCK_DISTANCE) {
            return this.end;
        } else {
            double value = sampler.erosion().compute(new DensityFunction.SinglePointContext(blockX, blockY, blockZ));
            if (value > THRESHOLD_HIGHLANDS) {
                return this.highlands;
            } else if (value >= THRESHOLD_MIDLANDS) {
                return this.midlands;
            } else {
                return value < THRESHOLD_SMALL_ISLANDS ? this.islands : this.barrens;
            }

        }
    }
}
