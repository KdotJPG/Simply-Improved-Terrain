package jpg.k.simplyimprovedterrain.math;

import net.minecraft.util.Mth;

public final class Vecords {
    public record Vector2d(double x, double z) {
        public static Vector2d add(Vector2d a, Vector2d b) {
            return new Vector2d(a.x + b.x, a.z + b.z);
        }
        public static Vector2d subtract(Vector2d a, Vector2d b) {
            return new Vector2d(a.x - b.x, a.z - b.z);
        }
        public static double dot(Vector2d a, Vector2d b) {
            return a.x * b.x + a.z * b.z;
        }
    }
    public record Vector3d(double x, double y, double z) {
        public static Vector3d add(Vector3d a, Vector3d b) {
            return new Vector3d(a.x + b.x, a.y + b.y, a.z + b.z);
        }
        public static Vector3d subtract(Vector3d a, Vector3d b) {
            return new Vector3d(a.x - b.x, a.y - b.y, a.z - b.z);
        }
        public static Vector3d multiply(Vector3d a, double b) {
            return new Vector3d(a.x * b, a.y * b, a.z * b);
        }
        public static Vector3d multiply(Vector3d a, Vector3d b) {
            return new Vector3d(a.x * b.x, a.y * b.y, a.z * b.z);
        }
        public static double dot(Vector3d a, Vector3d b) {
            return a.x * b.x + a.y * b.y + a.z * b.z;
        }
        public static Vector3d abs(Vector3d a) {
            return new Vector3d(Math.abs(a.x), Math.abs(a.y), Math.abs(a.z));
        }
        public static Vector3d clamp(Vector3d a, int min, int max) {
            return new Vector3d(Mth.clamp(a.x, min, max), Mth.clamp(a.y, min, max), Mth.clamp(a.z, min, max));
        }
        public static double distanceSq(Vector3d a, Vector3d b) {
            return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z);
        }
        public Vector2d xz() {
            return new Vector2d(x, z);
        }
    }
    public record Vector3i(int x, int y, int z) {
        public static Vector3i add(Vector3i a, Vector3i b) {
            return new Vector3i(a.x + b.x, a.y + b.y, a.z + b.z);
        }
        public static Vector3i subtract(Vector3i a, Vector3i b) {
            return new Vector3i(a.x - b.x, a.y - b.y, a.z - b.z);
        }
    }
}
