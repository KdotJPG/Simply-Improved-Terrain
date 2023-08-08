package jpg.k.simplyimprovedterrain.terrain.formulamodification.caching;

import jpg.k.simplyimprovedterrain.terrain.formulamodification.CourseAlteringNode;
import jpg.k.simplyimprovedterrain.terrain.formulamodification.FunctionEvaluationSituation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public record CacheEntry(int usageCount, FunctionEvaluationSituation situation, DecisionState decisionState, DensityFunction visitedFunction) {

    public CacheEntry withIncrementedUsageCount() {
        return new CacheEntry(usageCount + 1, situation, decisionState, visitedFunction);
    }

    public CacheEntry withVisitedFunction(DensityFunction visitedFunction) {
        return new CacheEntry(usageCount, situation, decisionState, visitedFunction);
    }

    public CacheEntry decideMarker(DensityFunction finalFunction) {
        if (decisionState != DecisionState.UNDECIDED) {
            throw new IllegalStateException("Called decideMarker() on a cache entry whose marker decision was already made.");
        }
        return new CacheEntry(usageCount, situation, DecisionState.MARKER_DECIDED, finalFunction);
    }

    public CacheEntry decideMarker(boolean shallApplyMarker) {
        return decideMarker(
                !shallApplyMarker ?
                        visitedFunction :
                        switch (situation) {
                            case ORDINARY -> DensityFunctions.cacheAllInCell(new CourseAlteringNode(visitedFunction));
                            case INSIDE_CACHE_2D -> DensityFunctions.cache2d(new CourseAlteringNode(visitedFunction));
                            case INSIDE_INTERPOLATED -> DensityFunctions.cacheOnce(new CourseAlteringNode(visitedFunction));
                        }
        );
    }

    public CacheEntry fullyDecide(DensityFunction finalFunction) {
        if (decisionState == DecisionState.FULLY_DECIDED) {
            throw new IllegalStateException("Called fullyDecide() on a cache entry whose final decision was already made.");
        }
        return new CacheEntry(usageCount, situation, DecisionState.FULLY_DECIDED, finalFunction);
    }

    public enum DecisionState {
        UNDECIDED,
        MARKER_DECIDED,
        FULLY_DECIDED
    }

}
