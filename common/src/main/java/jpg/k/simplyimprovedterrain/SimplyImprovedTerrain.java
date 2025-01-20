package jpg.k.simplyimprovedterrain;

import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.CustomDensityFunctions;
import jpg.k.simplyimprovedterrain.terrain.formulamodification.TerrainFormulaModification;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.function.BiConsumer;

public class SimplyImprovedTerrain {

    public static final String MOD_ID = "simplyimprovedterrain";

    public static void bootstrap(BiConsumer<String, MapCodec<? extends DensityFunction>> callback) {
        CustomDensityFunctions.bootstrap(callback);
        TerrainFormulaModification.bootstrap(callback);
    }

    public static ResourceLocation toResourceLocation(String serializedName) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, serializedName);
    }

}
