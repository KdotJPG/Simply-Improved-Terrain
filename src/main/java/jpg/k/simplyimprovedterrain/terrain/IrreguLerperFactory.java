package jpg.k.simplyimprovedterrain.terrain;

import java.util.concurrent.ConcurrentHashMap;

public class IrreguLerperFactory {

    private static record Key(long seed, int vanillaCellWidth, int vanillaCellHeight, int vanillaCellCountY) { }
    private static ConcurrentHashMap<Key, IrreguLerper> cache = new ConcurrentHashMap<>();

    public static IrreguLerper get(long seed, int vanillaCellWidth, int vanillaCellHeight, int vanillaCellCountY) {
        return cache.computeIfAbsent(new Key(seed, vanillaCellWidth, vanillaCellHeight, vanillaCellCountY), (key) -> {
            return IrreguLerper.Create(key.seed, 16, vanillaCellCountY * vanillaCellHeight, vanillaCellWidth * 2, vanillaCellHeight * 2);
        });
    }

}
