package jpg.k.simplyimprovedterrain.terrain.tieredgen;

import net.minecraft.util.Mth;

import java.util.List;

public abstract class ConvexPolytope3D {

    public record Vec3f(float x, float y, float z) { }

    private final List<Vec3f> vertices;
    private final Vec3f center, axialMin, axialMax;

    public ConvexPolytope3D(List<Vec3f> vertices) {
        double xCenter = 0, yCenter = 0, zCenter = 0;
        float xMin = Float.POSITIVE_INFINITY, yMin = Float.POSITIVE_INFINITY, zMin = Float.POSITIVE_INFINITY;
        float xMax = Float.NEGATIVE_INFINITY, yMax = Float.NEGATIVE_INFINITY, zMax = Float.NEGATIVE_INFINITY;
        this.vertices = vertices;
        for (Vec3f point : vertices) {
            xCenter += point.x();
            yCenter += point.y();
            zCenter += point.z();
            xMin = Math.min(xMin, point.x());
            yMin = Math.min(yMin, point.y());
            zMin = Math.min(zMin, point.z());
            xMax = Math.max(xMax, point.x());
            yMax = Math.max(yMax, point.y());
            zMax = Math.max(zMax, point.z());
        }
        xCenter /= vertices.size();
        yCenter /= vertices.size();
        zCenter /= vertices.size();
        center = new Vec3f((float)xCenter, (float)yCenter, (float)zCenter);
        axialMin = new Vec3f(xMin, yMin, zMin);
        axialMax = new Vec3f(xMax, yMax, zMax);
    }

    public List<Vec3f> vertices() {
        return vertices;
    }

    public abstract boolean isPointInRange(float x, float y, float z, float boundaryExtensionDistance);

    public Vec3f center() {
        return center;
    }
    public Vec3f axialMin() {
        return axialMin;
    }
    public Vec3f axialMax() {
        return axialMax;
    }

    public boolean intersectsAssumingSymmetry(
            float dxOther, float dyOther, float dzOther,
            float shapePadding, ConvexPolytope3D other
    ) {

        for (Vec3f otherVertex : other.vertices()) {
            if (this.isPointInRange(
                    dxOther + otherVertex.x(),
                    dyOther + otherVertex.y(),
                    dzOther + otherVertex.z(),
                    shapePadding
            )) {
                return true;
            }
        }

        for (Vec3f vertex : this.vertices()) {
            if (other.isPointInRange(
                    vertex.x() - dxOther,
                    vertex.y() - dyOther,
                    vertex.z() - dzOther,
                    shapePadding
            )) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(
            float dxOther, float dyOther, float dzOther,
            float shapePadding, ConvexPolytope3D other
    ) {

        for (Vec3f otherVertex : other.vertices()) {
            if (!this.isPointInRange(
                    dxOther + otherVertex.x(),
                    dyOther + otherVertex.y(),
                    dzOther + otherVertex.z(),
                    shapePadding
            )) {
                return false;
            }
        }

        return true;
    }

    public static final ConvexPolytope3D SIMPLE_POINT = new Point();
    private static class Point extends ConvexPolytope3D {

        public Point() {
            super(List.of(new Vec3f(0, 0, 0)));
        }

        @Override
        public boolean isPointInRange(float x, float y, float z, float boundaryExtensionDistance) {
            return boundaryExtensionDistance > 0 && Mth.square(x) + Mth.square(y) + Mth.square(z) < Mth.square(boundaryExtensionDistance);
        }
    }
}
