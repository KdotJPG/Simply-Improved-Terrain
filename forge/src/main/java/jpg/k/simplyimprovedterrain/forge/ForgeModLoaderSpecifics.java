package jpg.k.simplyimprovedterrain.forge;

import jpg.k.simplyimprovedterrain.ModLoaderSpecifics;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class ForgeModLoaderSpecifics implements ModLoaderSpecifics {

    @Override
    public Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get();
    }
}
