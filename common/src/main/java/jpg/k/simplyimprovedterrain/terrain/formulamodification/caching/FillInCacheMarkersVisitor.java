package jpg.k.simplyimprovedterrain.terrain.formulamodification.caching;

import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.function.Function;

public record FillInCacheMarkersVisitor(Function<CacheMarkerNode, DensityFunction> cacheMarkerNodeReplacer) implements DensityFunction.Visitor {

    @Override
    public DensityFunction apply(DensityFunction function) {
        if (!(function instanceof CacheMarkerNode cacheMarkerNode)) return function;

        // We expect `cacheMarkerNodeReplacer` to call FillInCacheMarkersVisitor again as needed.
        return cacheMarkerNodeReplacer.apply(cacheMarkerNode);
    }

}
