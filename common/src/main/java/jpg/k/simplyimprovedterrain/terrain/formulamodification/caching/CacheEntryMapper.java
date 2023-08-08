package jpg.k.simplyimprovedterrain.terrain.formulamodification.caching;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.List;
import java.util.function.BiFunction;

public class CacheEntryMapper<T> {

    private final List<CacheEntry> indexedCachedVisitedFunctionEntries;
    private final DensityFunction.Visitor cacheNodeFunctionTreeStubVisitor;
    private final FillInCacheMarkersVisitor fillInCacheMarkersVisitor;

    public CacheEntryMapper(List<CacheEntry> indexedCachedVisitedFunctionEntries, DensityFunction.Visitor cacheNodeFunctionTreeStubVisitor) {
        this.indexedCachedVisitedFunctionEntries = indexedCachedVisitedFunctionEntries;
        this.cacheNodeFunctionTreeStubVisitor = cacheNodeFunctionTreeStubVisitor;
        fillInCacheMarkersVisitor = new FillInCacheMarkersVisitor(this::mapCacheEntry);
    }

    public T fillInCacheEntries(T subject, BiFunction<T, DensityFunction.Visitor, T> mapAllFunction) {
        subject = mapAllFunction.apply(subject, cacheNodeFunctionTreeStubVisitor);
        subject = mapAllFunction.apply(subject, fillInCacheMarkersVisitor);
        return subject;
    }

    private DensityFunction mapCacheEntry(CacheMarkerNode cacheMarkerNode) {

        int index = cacheMarkerNode.index();
        CacheEntry cacheEntry = indexedCachedVisitedFunctionEntries.get(index);

        CacheEntry.DecisionState decisionStateAsFound = cacheEntry.decisionState();

        if (decisionStateAsFound == CacheEntry.DecisionState.UNDECIDED) {
            DensityFunction visitedFunction = cacheEntry.visitedFunction();
            boolean shallApplyMarker = cacheEntry.usageCount() > 1 && !KnownFastFunctionUtils.isKnownFast(visitedFunction);
            cacheEntry = cacheEntry.decideMarker(shallApplyMarker);
        }

        if (decisionStateAsFound != CacheEntry.DecisionState.FULLY_DECIDED) {
            DensityFunction visitedFunction = cacheEntry.visitedFunction();
            visitedFunction = visitedFunction.mapAll(cacheNodeFunctionTreeStubVisitor);
            visitedFunction = visitedFunction.mapAll(fillInCacheMarkersVisitor);
            cacheEntry = cacheEntry.fullyDecide(visitedFunction);
        }

        if (decisionStateAsFound != CacheEntry.DecisionState.FULLY_DECIDED) {
            indexedCachedVisitedFunctionEntries.set(index, cacheEntry);
        }

        return cacheEntry.visitedFunction();
    }
}
