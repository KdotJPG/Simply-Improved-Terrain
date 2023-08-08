package jpg.k.simplyimprovedterrain.terrain.formulamodification.caching;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public class KnownFastFunctionUtils {

    public static boolean isKnownFast(DensityFunction function) {

        // This can be expanded to evaluate entire trees rather than just leaf nodes.
        return function instanceof DensityFunctions.Constant ||
               function instanceof DensityFunctions.YClampedGradient;

    }

}
