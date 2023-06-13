package jpg.k.simplyimprovedterrain.quilt;

import jpg.k.simplyimprovedterrain.fabriclike.SimplyImprovedTerrainFabricLike;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

public class SimplyImprovedTerrainQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer mod) {
        SimplyImprovedTerrainFabricLike.init();
    }
}
