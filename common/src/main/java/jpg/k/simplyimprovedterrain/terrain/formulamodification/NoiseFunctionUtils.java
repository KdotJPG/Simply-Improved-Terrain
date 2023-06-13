package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.SplitBlendedNoise;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

import java.util.Set;

final class NoiseFunctionUtils {

    public static boolean isNoise(DensityFunction function) {
        return TYPES.contains(function.getClass());
    }

    private static final Set<Class<? extends DensityFunction>> TYPES = Set.of(
            DensityFunctions.Noise.class,
            BlendedNoise.class,
            DensityFunctions.EndIslandDensityFunction.class,
            DensityFunctions.ShiftA.class,
            DensityFunctions.ShiftB.class,
            DensityFunctions.Shift.class,
            DensityFunctions.ShiftedNoise.class,
            DensityFunctions.WeirdScaledSampler.class,
            SplitBlendedNoise.BlendedNoisePart.class
    );

}
