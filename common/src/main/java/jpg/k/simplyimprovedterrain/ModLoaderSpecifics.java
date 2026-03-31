package jpg.k.simplyimprovedterrain;

import java.nio.file.Path;
import java.util.ServiceLoader;

public interface ModLoaderSpecifics {
    ModLoaderSpecifics INSTANCE = ServiceLoader.load(ModLoaderSpecifics.class).findFirst().orElseThrow();
    Path getConfigPath();
}
