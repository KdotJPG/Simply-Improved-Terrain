package jpg.k.simplyimprovedterrain.terrain.tieredgen;

import net.minecraft.util.Mth;

public interface PointDistributionPlugin3D {

    void forPointsInChunk(
            double x, double y, double z,
            ConvexPolytope3D queryShape, float withinPadding, float notContainedByPadding,
            TieredChunkGrid3D.TieredChunk3D chunk, float dxChunk, float dyChunk, float dzChunk,
            PointIterationHandler handler
    );

    @FunctionalInterface
    interface PointIterationHandler {
        boolean handle(Point3D point);
    }

    record Point3D(
            float x, float y, float z,
            Object[] dataEntries
    ) {
        public float distanceSquared(float x, float y, float z) {
            return  Mth.square(x - this.x) +
                    Mth.square(y - this.y) +
                    Mth.square(z - this.z);
        }
    }

}
