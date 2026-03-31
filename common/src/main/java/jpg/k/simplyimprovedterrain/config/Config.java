package jpg.k.simplyimprovedterrain.config;


import net.minecraft.resources.ResourceLocation;
import java.util.Map;

public record Config(Map<String, DimensionConfig> dimensionConfigs, DimensionConfig global) {

    public DimensionConfig resolveForDimension(ResourceLocation location) {
        return resolveForDimension(location.toString());
    }

    public DimensionConfig resolveForDimension(String location) {
        var dimensionConfig = dimensionConfigs != null ?
                dimensionConfigs.getOrDefault(location, DimensionConfig.OOPS_ALL_NULL) :
                DimensionConfig.OOPS_ALL_NULL;

        return dimensionConfig.withFallback(global).withFallback(DimensionConfig.DEFAULT);
    }
}
