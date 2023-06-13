package jpg.k.simplyimprovedterrain.fabric;

import jpg.k.simplyimprovedterrain.fabriclike.SimplyImprovedTerrainFabricLike;
import net.fabricmc.api.ModInitializer;

public class SimplyImprovedTerrainFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SimplyImprovedTerrainFabricLike.init();
    }
}
