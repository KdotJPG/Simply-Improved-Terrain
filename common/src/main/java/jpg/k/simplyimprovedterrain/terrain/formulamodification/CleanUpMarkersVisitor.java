package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

record CleanUpMarkersVisitor(FunctionEvaluationSituation situation) implements CourseAlteringVisitor {
    public static final CleanUpMarkersVisitor DEFAULT = new CleanUpMarkersVisitor();

    public CleanUpMarkersVisitor(FunctionEvaluationSituation situation) {
        this.situation = situation;
    }

    public CleanUpMarkersVisitor(CleanUpMarkersVisitor base) {
        this(base.situation);
    }

    public CleanUpMarkersVisitor() {
        this(FunctionEvaluationSituation.ORDINARY);
    }

    public CleanUpMarkersVisitor withNewSituation(FunctionEvaluationSituation situation) {
        if (
                this.situation == FunctionEvaluationSituation.INSIDE_CACHE_2D && situation != FunctionEvaluationSituation.INSIDE_CACHE_2D ||
                this.situation != FunctionEvaluationSituation.ORDINARY && situation == FunctionEvaluationSituation.ORDINARY
        ) {
            throw new IllegalStateException("Trying to switch CleanUpMarkersVisitor from " + this.situation + " to " + situation + ".");
        }
        return new CleanUpMarkersVisitor(situation);
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

                return visitor.situation() != FunctionEvaluationSituation.ORDINARY ?

                        // Remove marker if inside `Cache2D` or another `Interpolated`
                        CourseAlteringNode.unwrap(marker.wrapped().mapAll(visitor)) :

                        // Otherwise skip `apply()` on this `Interpolated` instance, then wrap its argument tree accordingly.
                        DensityFunctions.interpolated(marker.wrapped().mapAll(visitor.withNewSituation(FunctionEvaluationSituation.INSIDE_INTERPOLATED)));

            }

            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        },

        FlatCache(DensityFunctions.Marker.Type.FlatCache) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {

                return visitor.situation() == FunctionEvaluationSituation.INSIDE_CACHE_2D ?

                        // Remove marker if inside `Cache2D`
                        CourseAlteringNode.unwrap(marker.wrapped().mapAll(visitor)) :

                        // Otherwise skip `apply()` on this `FlatCache` instance, replace it with `Cache2D,
                        // and wrap its argument tree accordingly.
                        DensityFunctions.cache2d(marker.wrapped().mapAll(visitor.withNewSituation(FunctionEvaluationSituation.INSIDE_CACHE_2D)));

            }

            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        },

        Cache2D(DensityFunctions.Marker.Type.Cache2D) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {

                return visitor.situation() == FunctionEvaluationSituation.INSIDE_CACHE_2D ?

                        // Remove marker if inside another `Cache2D`
                        CourseAlteringNode.unwrap(marker.wrapped().mapAll(visitor)) :

                        // Otherwise skip `apply()` on this `Cache2D` instance, them wrap its argument tree accordingly.
                        DensityFunctions.cache2d(marker.wrapped().mapAll(visitor.withNewSituation(FunctionEvaluationSituation.INSIDE_CACHE_2D)));

            }

            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        },

        CacheOnce(DensityFunctions.Marker.Type.CacheOnce) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {

                // Remove `CacheOnce`.
                return CourseAlteringNode.unwrap(marker.wrapped().mapAll(visitor));

            }

            public DensityFunction apply(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {
                return marker;
            }
        },

        CacheAllInCell(DensityFunctions.Marker.Type.CacheAllInCell) {
            public DensityFunction mapAllFor(CleanUpMarkersVisitor visitor, DensityFunctions.Marker marker) {

                // Remove `CacheAllInCell`. We'll add these back later, dynamically.
                return CourseAlteringNode.unwrap(marker.wrapped().mapAll(visitor));

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
