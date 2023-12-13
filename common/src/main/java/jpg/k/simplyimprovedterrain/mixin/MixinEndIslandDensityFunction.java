package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.mixinapi.IMixinSimplexNoise;
import jpg.k.simplyimprovedterrain.terrain.MetaballEndIslandNoise;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = DensityFunctions.EndIslandDensityFunction.class, priority = 250)
public class MixinEndIslandDensityFunction {

    private static final double END_VALUE_OFFSET = -8.0;
    private static final double END_VALUE_MULTIPLIER = 1.0 / 128.0;

    @Shadow @Final SimplexNoise islandNoise;

    /**
     * @author K.jpg
     * @reason Simply Improved Terrain island noise.
     */
    @Overwrite
    public double compute(DensityFunction.FunctionContext functionContext) {
        double endValue = MetaballEndIslandNoise.INSTANCE.getNoise(((IMixinSimplexNoise)this.islandNoise).getPermutationTable(),
                functionContext.blockX(), functionContext.blockZ());
        return (endValue + END_VALUE_OFFSET) * END_VALUE_MULTIPLIER;
    }

}
