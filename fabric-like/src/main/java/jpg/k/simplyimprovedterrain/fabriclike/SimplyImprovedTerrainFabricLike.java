package jpg.k.simplyimprovedterrain.fabriclike;

import jpg.k.simplyimprovedterrain.SimplyImprovedTerrain;
import net.minecraft.core.Registry;

public class SimplyImprovedTerrainFabricLike {
    public static void init() {
        SimplyImprovedTerrain.bootstrap((serializedName, object) -> Registry.register(
                Registry.DENSITY_FUNCTION_TYPES, SimplyImprovedTerrain.toResourceLocation(serializedName), object
        ));
    }
}
