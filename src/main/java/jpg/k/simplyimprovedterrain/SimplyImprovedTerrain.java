package jpg.k.simplyimprovedterrain;

import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.CustomDensityFunctions;
import jpg.k.simplyimprovedterrain.terrain.formulamodification.TerrainFormulaModification;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;

public class SimplyImprovedTerrain implements ModInitializer {

    private static final String REGISTRATION_PREFIX = "simplyimprovedterrain:";

    @Override
    public void onInitialize() {
        CustomDensityFunctions.bootstrap();
        TerrainFormulaModification.bootstrap();
    }

    public static <T> T register(Registry<? super T> registry, String serializedName, T object) {
        return Registry.register(registry, REGISTRATION_PREFIX + serializedName, object);
    }

}
