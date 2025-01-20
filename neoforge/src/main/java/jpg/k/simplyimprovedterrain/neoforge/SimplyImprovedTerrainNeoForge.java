package jpg.k.simplyimprovedterrain.neoforge;

import jpg.k.simplyimprovedterrain.SimplyImprovedTerrain;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(SimplyImprovedTerrain.MOD_ID)
public class SimplyImprovedTerrainNeoForge {

    @SubscribeEvent
    private static void register(RegisterEvent event) {
        SimplyImprovedTerrain.bootstrap((serializedName, object) -> event.register(
                Registries.DENSITY_FUNCTION_TYPE, SimplyImprovedTerrain.toResourceLocation(serializedName), () -> object
        ));
    }

}
