package jpg.k.simplyimprovedterrain.neoforge;

import jpg.k.simplyimprovedterrain.ModLoaderSpecifics;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class NeoForgeModLoaderSpecifics implements ModLoaderSpecifics {

    @Override
    public Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get();
    }
}
