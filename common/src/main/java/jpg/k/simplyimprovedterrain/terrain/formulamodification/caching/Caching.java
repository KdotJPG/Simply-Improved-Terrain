package jpg.k.simplyimprovedterrain.terrain.formulamodification.caching;

import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.List;
import java.util.function.BiFunction;

public class Caching {

    public static <T> T applyCacheMarkers(T subject, BiFunction<T, DensityFunction.Visitor, T> mapAllFunction, DensityFunction.Visitor cacheNodeFunctionTreeStubVisitor) {
        BuildCacheVisitor buildCacheVisitor = new BuildCacheVisitor();
        subject = mapAllFunction.apply(subject, buildCacheVisitor);

        List<CacheEntry> indexedCachedVisitedFunctionEntries = buildCacheVisitor.indexedCachedVisitedFunctionEntries();

        return new CacheEntryMapper<T>(indexedCachedVisitedFunctionEntries, cacheNodeFunctionTreeStubVisitor)
                .fillInCacheEntries(subject, mapAllFunction);
    }
}
