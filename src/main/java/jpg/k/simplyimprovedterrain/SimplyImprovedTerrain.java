package jpg.k.simplyimprovedterrain;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jpg.k.simplyimprovedterrain.world.WorldTypeImproved;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("simplyimprovedterrain")
public class SimplyImprovedTerrain
{
    public static final String MODID = "simplyimprovedterrain";

    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();

    public SimplyImprovedTerrain() {
        WorldTypeImproved.init();
    }


}
