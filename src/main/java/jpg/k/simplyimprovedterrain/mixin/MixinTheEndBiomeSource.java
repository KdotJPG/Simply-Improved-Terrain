package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.biome.BiomeFiddleHelper;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinSimplexNoise;
import jpg.k.simplyimprovedterrain.terrain.MetaballEndIslandNoise;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TheEndBiomeSource.class)
public class MixinTheEndBiomeSource {

    private static final int OUTER_ISLANDS_BLOCK_DISTANCE = 1024;
    private static final int OUTER_ISLANDS_QUART_DISTANCE_SQUARED = Mth.square(QuartPos.fromBlock(OUTER_ISLANDS_BLOCK_DISTANCE));
    private static final int QUART_LOOKUP_OFFSET = BiomeFiddleHelper.BLOCK_XYZ_OFFSET;
    private static final int END_VANILLA_TRILERP_CELL_WIDTH = 8;

    private static final double THRESHOLD_HIGHLANDS = 40.0;
    private static final double THRESHOLD_MIDLANDS = 0.0;
    private static final double THRESHOLD_SMALL_ISLANDS = -20.0;

    @Shadow @Final private SimplexNoise islandNoise;
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
        if (Mth.square((long)quartX) + Mth.square((long)quartZ) <= OUTER_ISLANDS_QUART_DISTANCE_SQUARED) {
            return this.end;
        } else {
            double value = MetaballEndIslandNoise.INSTANCE.getNoise(((IMixinSimplexNoise)this.islandNoise).getPermutationTable(),
                    QuartPos.toBlock(quartX) + QUART_LOOKUP_OFFSET, QuartPos.toBlock(quartZ) + QUART_LOOKUP_OFFSET);
            if (value > THRESHOLD_HIGHLANDS) {
                return this.highlands;
            } else if (value >= THRESHOLD_MIDLANDS) {
                return this.midlands;
            } else {
                return value < THRESHOLD_SMALL_ISLANDS ? this.islands : this.barrens;
            }

        }
    }

    /**
     * @author K.jpg
     * @reason Simply Improved Terrain island noise.
     */
    @Overwrite
    public static float getHeightValue(SimplexNoise simplexNoiseForPermutationTable, int quartX, int quartZ) {
        return (float)MetaballEndIslandNoise.INSTANCE.getNoise(((IMixinSimplexNoise)simplexNoiseForPermutationTable).getPermutationTable(),
                quartX * END_VANILLA_TRILERP_CELL_WIDTH, quartZ * END_VANILLA_TRILERP_CELL_WIDTH);
    }


}
