package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

// TODO make class with private members instead
record CleanUpMarkersVisitor(boolean insideInterpolated, boolean insideCache2D) implements CourseAlteringVisitor {
    public static final CleanUpMarkersVisitor DEFAULT = new CleanUpMarkersVisitor();

    public CleanUpMarkersVisitor(boolean insideInterpolated, boolean insideCache2D) {
        this.insideInterpolated = insideInterpolated;
        this.insideCache2D = insideCache2D;
    }

    public CleanUpMarkersVisitor(CleanUpMarkersVisitor base) {
        this(base.insideInterpolated, base.insideCache2D);
    }

    public CleanUpMarkersVisitor() {
        this(false, false);
    }

    public CleanUpMarkersVisitor withInsideInterpolated(boolean insideInterpolated) {
        return new CleanUpMarkersVisitor(insideInterpolated, insideCache2D);
    }

    public CleanUpMarkersVisitor withInsideCache2D(boolean insideCache2D) {
        return new CleanUpMarkersVisitor(insideInterpolated, insideCache2D);
    }

    public DensityFunction mapAllFor(DensityFunction function) {
        if (!(function instanceof DensityFunctions.Marker marker)) return function.mapAll(this);
        return MarkerTypeHandler.fromType(marker.type()).mapAllFor(this, marker);
    }

    public DensityFunction apply(DensityFunction densityFunction) {
        if (!(densityFunction instanceof DensityFunctions.Marker marker)) return densityFunction;
        return MarkerTypeHandler.fromType(marker.type()).apply(this, marker);
    }

    private enum MarkerTypeHandler {
        Interpolated(DensityFunctions.Marker.Type.Interpolated) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {

                return visitor.insideInterpolated() || visitor.insideCache2D() ?

                        // Remove marker if inside `Cache2D` or another `Interpolated`
                        marker.wrapped().mapAll(visitor) :

                        // Otherwise skip `apply()` on this `Interpolated` instance, then wrap its argument tree accordingly.
                        DensityFunctions.interpolated(marker.wrapped().mapAll(visitor.withInsideInterpolated(true)));

            }

            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        },

        FlatCache(DensityFunctions.Marker.Type.FlatCache) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {

                return visitor.insideCache2D() ?

                        // Remove marker if inside `Cache2D`
                        marker.wrapped().mapAll(visitor) :

                        // Otherwise skip `apply()` on this `FlatCache` instance, replace it with `Cache2D,
                        // and wrap its argument tree accordingly.
                        DensityFunctions.cache2d(marker.wrapped().mapAll(visitor.withInsideCache2D(true)));

            }

            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        },

        Cache2D(DensityFunctions.Marker.Type.Cache2D) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {

                return visitor.insideCache2D() ?

                        // Remove marker if inside another `Cache2D`
                        marker.wrapped().mapAll(visitor) :

                        // Otherwise skip `apply()` on this `Cache2D` instance, them wrap its argument tree accordingly.
                        DensityFunctions.cache2d(marker.wrapped().mapAll(visitor.withInsideCache2D(true)));

            }

            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        },

        CacheOnce(DensityFunctions.Marker.Type.CacheOnce) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {

                // Remove `CacheOnce`.
                return marker.wrapped().mapAll(visitor);

            }

            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        },

        CacheAllInCell(DensityFunctions.Marker.Type.CacheAllInCell) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {

                // Remove `CacheAllInCell`. We'll add these back later, dynamically.
                return marker.wrapped().mapAll(visitor);

            }

            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        },

        NoOp(null) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker.mapAll(visitor);
            }
            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        };

        private static final Map<DensityFunctions.Marker.Type, MarkerTypeHandler> BY_TYPE =
                Arrays.stream(values()).filter(entry -> entry.markerType != null)
                        .collect(Collectors.toUnmodifiableMap(entry -> entry.markerType, value -> value));

        private static final MarkerTypeHandler fromType(DensityFunctions.Marker.Type type) {
            return BY_TYPE.getOrDefault(type, NoOp);
        }

        private final DensityFunctions.Marker.Type markerType;

        private MarkerTypeHandler(DensityFunctions.Marker.Type markerType) {
            this.markerType = markerType;
        }

        abstract DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker);
        abstract DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker);

    }
}
