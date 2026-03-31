package jpg.k.simplyimprovedterrain.fabriclike;

import jpg.k.simplyimprovedterrain.ModLoaderSpecifics;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class FabricLikeModLoaderSpecifics implements ModLoaderSpecifics {

    @Override
    public Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
