package jpg.k.simplyimprovedterrain.terrain.tieredgen;

import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

public interface TieredChunkGrid3D {

    int TIER_INDICATOR_OUTSIDE_GENERATION_AREA = Integer.MAX_VALUE;

    void validatePlugin(PluginInternal internal, int pluginIndex);
    void forChunks(
            double x, double y, double z,
            ConvexPolytope3D queryShape, float queryPadding, float rejectionPadding,
            ChunkInfoIterationHandler handler
    );
    TieredChunk3D getOrCreate(Object chunkKey);

    @FunctionalInterface
    interface ChunkInfoIterationHandler {
        boolean handle(Object key, float dxChunk, float dyChunk, float dzChunk);
    }

    record TieredChunk3D(
            Object[] dataEntries,
            Object chunkKey,
            double x, double y, double z
    ) { }

    record NeighborTieredChunk3D(
            Object[] dataEntries,
            float dx, float dy, float dz,
            int neighborOrdinal,
            int tier
    ) { }

    interface PluginRegistrar {
        ConvexPolytope3D chunkShape();
        int registerChunkDataIndices(int count);
        int registerPlugin(PluginInternal registeredPlugin);
        float chunkSpacingWithinTier();
    }

    interface PluginInternal {
        void populateChunkData(
                Object[] chunkDataEntries, NeighborTieredChunk3D[] neighborhood,
                long unseededHash, int tier, ChunkUtils chunkUtils
        );

        record Unvalidated(PluginInternal wrapped) implements PluginInternal {

            @Override
            public void populateChunkData(
                    Object[] chunkDataEntries, NeighborTieredChunk3D[] neighborhood,
                    long unseededHash, int tier, ChunkUtils chunkUtils
            ) {
                throw new UnvalidatedPluginException();
            }

            public static class UnvalidatedPluginException extends RuntimeException { }
            public static class PluginAlreadyValidatedPluginException extends RuntimeException { }
        }
    }

    interface ChunkUtils {
        ConvexPolytope3D chunkShape();
        Vector3f randomPointInChunk(RandomSource random, Vector3f destination);
        int getChunkTierForPoint(float x, float y, float z, int currentTier);
    }
}
