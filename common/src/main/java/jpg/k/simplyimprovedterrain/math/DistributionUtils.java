package jpg.k.simplyimprovedterrain.math;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;

public class DistributionUtils {

    private static final float SQUARE_AREA_UNIT = 4.0f;
    private static final float CIRCLE_AREA_UNIT = Mth.PI;
    private static final float CUBE_VOLUME_UNIT = 8.0f;
    private static final float SPHERE_VOLUME_UNIT = 4.0f * Mth.PI / 3.0f;

    public static final float AREA_RATIO_CIRCLE_TO_SQUARE = SQUARE_AREA_UNIT / CIRCLE_AREA_UNIT;
    public static final float RADIUS_RATIO_CIRCLE_TO_SQUARE = Mth.sqrt(AREA_RATIO_CIRCLE_TO_SQUARE);

    public static final float VOLUME_RATIO_SPHERE_TO_CUBE = CUBE_VOLUME_UNIT / SPHERE_VOLUME_UNIT;
    public static final float RADIUS_RATIO_SPHERE_TO_CUBE = (float)Math.pow(VOLUME_RATIO_SPHERE_TO_CUBE, 1.0f / 3.0f);

    public static <R> R sampleCircleCenterBiasedSpread(float radius, RandomSource random, VectorCallback2D<R> callback) {
        float theta = random.nextFloat() * Mth.TWO_PI;

        // Outwardly-fading probability.
        // Use `1-|rand-rand|` or `sqrt(rand)` instead, for a true uniform circular distribution.
        float distance = random.nextFloat() * radius;

        return callback.apply(
                Mth.cos(theta) * distance,
                Mth.sin(theta) * distance
        );
    }

    public static <R> R sampleEllipsoidCenterBiasedSpread(float radiusXZ, float radiusY, RandomSource random, VectorCallback3D<R> callback) {
        float sphereY = random.nextFloat() * 2.0f - 1.0f;
        float sphereTheta = random.nextFloat() * Mth.TWO_PI;
        float sphereXZScale = Mth.sqrt(1.0f - sphereY * sphereY);

        // Same distribution as `sqrt(rand)`.
        // Outwardly-fading probability. Use a cube root instead, for a true uniform spherical distribution.
        float radiusScale = 1 - Math.abs(random.nextFloat() - random.nextFloat());

        return callback.apply(
                radiusScale * sphereXZScale * radiusXZ * Mth.cos(sphereTheta),
                radiusScale * sphereY * radiusY,
                radiusScale * sphereXZScale * radiusXZ * Mth.sin(sphereTheta)
        );
    }

    public interface VectorCallback2D<R> {
        R apply(float dx, float dz);
    }

    public interface VectorCallback3D<R> {
        R apply(float dx, float dy, float dz);
    }

    public static Matrix3f randomRotation3D(RandomSource random) {

        // Random unit vector + random angle -> random rotation!
        float sphereY = random.nextFloat() * 2.0f - 1.0f;
        float sphereTheta = random.nextFloat() * Mth.TWO_PI;
        float sphereXZScale = Mth.sqrt(1.0f - sphereY * sphereY);
        float otherAngle = random.nextFloat() * Mth.TWO_PI;
        return new Matrix3f().rotation(
                otherAngle,
                sphereXZScale * Mth.cos(sphereTheta),
                sphereY,
                sphereXZScale * Mth.sin(sphereTheta)
        );
    }

}