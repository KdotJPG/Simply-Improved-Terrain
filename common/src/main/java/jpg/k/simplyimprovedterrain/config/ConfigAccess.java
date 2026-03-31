package jpg.k.simplyimprovedterrain.config;

import com.google.common.base.Suppliers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jpg.k.simplyimprovedterrain.ModLoaderSpecifics;
import jpg.k.simplyimprovedterrain.SimplyImprovedTerrain;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Supplier;

public final class ConfigAccess {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FILENAME = SimplyImprovedTerrain.MOD_ID + ".json";

    private static final Config INITIAL = new Config(
            Map.of("examplemod:planet_perlerp", new DimensionConfig(false)),
            new DimensionConfig(true)
    );

    private static final Supplier<Config> CONFIG_SUPPLIER = Suppliers.memoize(() -> {
        var configPath = ModLoaderSpecifics.INSTANCE.getConfigPath();
        var configFilePath = configPath.resolve(FILENAME);
        try {
            if (!Files.exists(configFilePath)) {
                Files.writeString(configFilePath, GSON.toJson(INITIAL));
                return INITIAL;
            } else {
                return GSON.fromJson(Files.newBufferedReader(configFilePath), Config.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load config: " + configFilePath, e);
        }
    });

    public static Config get() {
        return CONFIG_SUPPLIER.get();
    }

}
