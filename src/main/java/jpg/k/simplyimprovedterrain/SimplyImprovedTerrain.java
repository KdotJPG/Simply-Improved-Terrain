package jpg.k.simplyimprovedterrain;

import jpg.k.simplyimprovedterrain.terrain.CustomMathDensityFunctions;
import jpg.k.simplyimprovedterrain.terrain.SplitBlendedNoise;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;

public class SimplyImprovedTerrain implements ModInitializer {

    @Override
    public void onInitialize() {
        SplitBlendedNoise.bootstrap(Registry.DENSITY_FUNCTION_TYPES);
        CustomMathDensityFunctions.bootstrap(Registry.DENSITY_FUNCTION_TYPES);
    }

}
