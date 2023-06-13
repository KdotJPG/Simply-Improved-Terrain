package jpg.k.simplyimprovedterrain.fabriclike;

import jpg.k.simplyimprovedterrain.SimplyImprovedTerrain;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class SimplyImprovedTerrainFabricLike {
    public static void init() {
        SimplyImprovedTerrain.bootstrap((serializedName, object) -> Registry.register(
                BuiltInRegistries.DENSITY_FUNCTION_TYPE, SimplyImprovedTerrain.toResourceLocation(serializedName), object
        ));
    }
}
