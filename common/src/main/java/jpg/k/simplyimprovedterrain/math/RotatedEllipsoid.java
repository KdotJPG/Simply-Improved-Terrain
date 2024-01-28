package jpg.k.simplyimprovedterrain.math;

import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;

public record RotatedEllipsoid(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {

    public static RotatedEllipsoid createFromRandomAndRadii(RandomSource random, float radiusA, float radiusB, float radiusC) {
        return createFromRotationAndRadii(DistributionUtils.randomRotation3D(random), radiusA, radiusB, radiusC);
    }

    public static RotatedEllipsoid createFromRandomAndRadii(RandomSource random, float maxTiltY, float radiusA, float radiusB, float radiusC) {
        return createFromRotationAndRadii(DistributionUtils.randomRotation3D(random, maxTiltY), radiusA, radiusB, radiusC);
    }

    public static RotatedEllipsoid createFromRotationAndRadii(Matrix3f rotationMatrix, float radiusA, float radiusB, float radiusC) {
        return new RotatedEllipsoid(
                rotationMatrix.m00 / radiusA,
                rotationMatrix.m01 / radiusA,
                rotationMatrix.m02 / radiusA,
                rotationMatrix.m10 / radiusB,
                rotationMatrix.m11 / radiusB,
                rotationMatrix.m12 / radiusB,
                rotationMatrix.m20 / radiusC,
                rotationMatrix.m21 / radiusC,
                rotationMatrix.m22 / radiusC
        );
    }

    public float compute(float x, float y, float z) {
        float dA = ax * x + ay * y + az * z;
        float dB = bx * x + by * y + bz * z;
        float dC = cx * x + cy * y + cz * z;
        return dA * dA + dB * dB + dC * dC;
    }

}
