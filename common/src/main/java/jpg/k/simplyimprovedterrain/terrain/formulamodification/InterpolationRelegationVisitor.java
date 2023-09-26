package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

/**
 * Replaces all full-resolution cache markers with their interpolated counterparts.
 */
public enum InterpolationRelegationVisitor implements DensityFunction.Visitor {
    INSTANCE;

    @Override
    public DensityFunction apply(DensityFunction function) {

        if (function instanceof DensityFunctions.Marker marker) {
            if (marker.type() == DensityFunctions.Marker.Type.Cache2D) {
                return DensityFunctions.flatCache(marker.wrapped());
            }
            if (marker.type() == DensityFunctions.Marker.Type.CacheAllInCell) {
                return DensityFunctions.cacheOnce(marker.wrapped());
            }
        }

        return function;
    }

}
