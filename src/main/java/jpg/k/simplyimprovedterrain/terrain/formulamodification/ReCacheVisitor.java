package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.apache.commons.lang3.NotImplementedException;

public class ReCacheVisitor implements CourseAlteringVisitor {

    private record CacheEntry(DensityFunction cachedFunction, DensityFunctions.Marker cacheAllInCell, DensityFunctions.Marker cacheOnce, DensityFunctions.Marker cache2d) {}
    private final boolean insideInterpolated;
    private final boolean insideCache2D;
    private final Map<DensityFunction, CacheEntry> cache;

    private ReCacheVisitor(boolean insideInterpolated, boolean insideCache2D, Map<DensityFunction, CacheEntry> cache) {
        this.insideInterpolated = insideInterpolated;
        this.insideCache2D = insideCache2D;
        this.cache = cache;
    }

    public ReCacheVisitor() {
        this(false, false, new HashMap<>());
    }

    public ReCacheVisitor withInsideInterpolated(boolean insideInterpolated) {
        return new ReCacheVisitor(insideInterpolated, insideCache2D, cache);
    }

    public ReCacheVisitor withInsideCache2D(boolean insideCache2D) {
        return new ReCacheVisitor(insideInterpolated, insideCache2D, cache);
    }

    public DensityFunction mapAllFor(DensityFunction function) {
        DensityFunction.Visitor visitor = this;
        if (function instanceof DensityFunctions.Marker marker) {
            if (marker.type() == DensityFunctions.Marker.Type.Interpolated) {
                visitor = this.withInsideInterpolated(true);
            } else if (marker.type() == DensityFunctions.Marker.Type.Cache2D) {
                visitor = this.withInsideCache2D(true);
            }
        }
        return function.mapAll(visitor);
    }

    public DensityFunction apply(DensityFunction function) {

        throw new NotImplementedException(); // TODO

    }
}
