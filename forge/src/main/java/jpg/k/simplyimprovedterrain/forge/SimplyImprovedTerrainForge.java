package jpg.k.simplyimprovedterrain.forge;

//import dev.architectury.platform.forge.EventBuses;
import jpg.k.simplyimprovedterrain.SimplyImprovedTerrain;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod(SimplyImprovedTerrain.MOD_ID)
public class SimplyImprovedTerrainForge {
    public SimplyImprovedTerrainForge() {
        // Submit our event bus to let architectury register our content on the right time
        //EventBuses.registerModEventBus(SimplyImprovedTerrain.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        MinecraftForge.EVENT_BUS.addListener(SimplyImprovedTerrainForge::register);
    }

    private static void register(RegisterEvent event) {
        SimplyImprovedTerrain.bootstrap((serializedName, object) -> event.register(
                Registries.DENSITY_FUNCTION_TYPE, SimplyImprovedTerrain.toResourceLocation(serializedName), () -> object
        ));
    }
}
