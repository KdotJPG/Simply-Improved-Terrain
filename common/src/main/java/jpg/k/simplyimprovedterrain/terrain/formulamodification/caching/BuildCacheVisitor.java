package jpg.k.simplyimprovedterrain.terrain.formulamodification.caching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jpg.k.simplyimprovedterrain.terrain.formulamodification.CourseAlteringNode;
import jpg.k.simplyimprovedterrain.terrain.formulamodification.CourseAlteringVisitor;
import jpg.k.simplyimprovedterrain.terrain.formulamodification.FunctionEvaluationSituation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public record BuildCacheVisitor(FunctionEvaluationSituation situation, Map<CacheKey, Integer> cacheIndexMap, List<CacheEntry> indexedCachedVisitedFunctionEntries) implements CourseAlteringVisitor {

    private record CacheKey(FunctionEvaluationSituation situation, DensityFunction function) { }

    public BuildCacheVisitor() {
        this(FunctionEvaluationSituation.ORDINARY, new HashMap<>(), new ArrayList<>());
    }

    public BuildCacheVisitor withNewSituation(FunctionEvaluationSituation situation) {
        return new BuildCacheVisitor(situation, cacheIndexMap, indexedCachedVisitedFunctionEntries);
    }

    @Override
    public DensityFunction mapAllFor(DensityFunction function) {

        // If the node type is a vanilla Marker, it changes how we cache.
        // Let ORDINARY indicate no update.
        FunctionEvaluationSituation situationUpdate = FunctionEvaluationSituation.ORDINARY;
        DensityFunction keyFunction = function;
        if (function instanceof DensityFunctions.Marker marker) {
            situationUpdate = switch(marker.type()) {
                case Cache2D -> FunctionEvaluationSituation.INSIDE_CACHE_2D;
                case Interpolated -> FunctionEvaluationSituation.INSIDE_INTERPOLATED;
                default -> situationUpdate;
            };

            // Cache2D nodes already on the tree indicate that we must cache, so they have a special handling.
            // Part 1 is here: we will key the cache by the contents of the Cache2D, not the Cache2D node itself.
            // Part 2 is below, where we mark the cache entry as markerDecided, adding it into the indexed list with the Cache2D marker.
            if (situationUpdate == FunctionEvaluationSituation.INSIDE_CACHE_2D) {
                keyFunction = CourseAlteringNode.unwrap(marker.wrapped());
            }
        }

        // For Cache2D, this fulfills part 2 from the above.
        // For Interpolated, this has the effect of marking the node as something we never need to wrap with a cache marker.
        // Interpolator instances in NoiseChunk already function as caches on their own, so it would be redundant to
        // wrap an interpolation node with a CacheAllInCell.
        final boolean markerDecidedAlready = situationUpdate != FunctionEvaluationSituation.ORDINARY;

        FunctionEvaluationSituation newSituation = switch(situationUpdate) {
            case ORDINARY -> situation;
            case INSIDE_CACHE_2D, INSIDE_INTERPOLATED -> {
                if (situation != FunctionEvaluationSituation.ORDINARY) {
                    throw new IllegalStateException("Density Function subtree situation " + situation + " becomes " + situationUpdate + ". " +
                            "Previous transformations should not have left these marker nodes composed like this.");
                }
                yield situationUpdate;
            }
        };

        CacheKey key = new CacheKey(newSituation, keyFunction);
        int index = cacheIndexMap.compute(key, (cacheKey, cacheIndex) -> {

            if (cacheIndex == null) {

                // An equivalent function subtree has not yet been found, so create a new entry in the indexed list.
                cacheIndex = indexedCachedVisitedFunctionEntries.size();
                CacheEntry.DecisionState decisionState = markerDecidedAlready ? CacheEntry.DecisionState.MARKER_DECIDED : CacheEntry.DecisionState.UNDECIDED;
                indexedCachedVisitedFunctionEntries.add(new CacheEntry(1, situation, decisionState, function));

            } else {

                // An equivalent subtree has been found, so update it.
                CacheEntry existingEntry = indexedCachedVisitedFunctionEntries.get(cacheIndex);
                existingEntry = existingEntry.withIncrementedUsageCount();
                if (markerDecidedAlready && existingEntry.decisionState() == CacheEntry.DecisionState.UNDECIDED) {
                    // For Cache2D, this marks the node as markerDecided, and wraps the function with a Cache2D marker.
                    // For Interpolated, this marks the node as something we've decided not to wrap with a cache marker node.
                    DensityFunction existingFunction = existingEntry.visitedFunction();
                    DensityFunctions.Marker marker = (DensityFunctions.Marker) function;
                    boolean shallApplyMarker = !(existingFunction instanceof DensityFunctions.Marker existingMarker) || existingMarker.type() != marker.type();
                    existingEntry = existingEntry.decideMarker(shallApplyMarker);
                }
                indexedCachedVisitedFunctionEntries.set(cacheIndex, existingEntry);

            }
            return cacheIndex;
        });

        CacheEntry entry = indexedCachedVisitedFunctionEntries.get(index);

        // If this is the first time a copy of this function tree has been found,
        // continue the traversal and set the result to the index in the list.
        // Account for `newSituation` / `situationUpdate` when continuing the traversal.
        if (entry.usageCount() == 1) {
            DensityFunction.Visitor subVisitor = (newSituation != situation) ?
                    this.withNewSituation(newSituation) :
                    this;
            DensityFunction visitedFunction = (markerDecidedAlready &&
                        function instanceof DensityFunctions.Marker marker &&
                        marker.type() == DensityFunctions.Marker.Type.Cache2D) ?
                    // Prevents circular CacheEntry reference, where decided marker contents resolve to the same cache key as the marker itself (by design).
                    DensityFunctions.cache2d(new CourseAlteringNode(CourseAlteringNode.unwrap(marker.wrapped()).mapAll(subVisitor))) :
                    function.mapAll(subVisitor);
            indexedCachedVisitedFunctionEntries.set(
                    index,
                    entry.withVisitedFunction(visitedFunction)
            );
        }

        // Regardless of whether this is the first time a copy of this function tree was found,
        // replace it with a list index reference.
        return new CacheMarkerNode(index);
    }

    @Override
    public DensityFunction apply(DensityFunction function) {
        return function;
    }
}
