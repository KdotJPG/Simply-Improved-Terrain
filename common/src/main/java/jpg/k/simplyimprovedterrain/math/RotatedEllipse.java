package jpg.k.simplyimprovedterrain.math;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public record RotatedEllipse(float ax, float az, float bx, float bz) {

    public static RotatedEllipse createFromRandomAndRadii(RandomSource random, float radiusA, float radiusB) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        return createFromAngleAndRadii(angle, radiusA, radiusB);
    }

    public static RotatedEllipse createFromAngleAndRadii(float angle, float radiusA, float radiusB) {
        float rx = Mth.cos(angle), rz = Mth.sin(angle);
        return createFromUnitVectorAndRadii(rx, rz, radiusA, radiusB);
    }

    public static RotatedEllipse createFromUnitVectorAndRadii(float rx, float rz, float radiusA, float radiusB) {
        float ax = rx / radiusA, az =  rz / radiusA;
        float bx = rz / radiusB, bz = -rx / radiusB;
        return new RotatedEllipse(ax, az, bx, bz);
    }

    public float compute(float x, float z) {
        float dA = x * ax + z * az;
        float dB = x * bx + z * bz;
        return dA * dA + dB * dB;
    }

}
