package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public final class SamplingUtils {

    private static final double EVALUATION_SPREAD_BASE_XZ = 16384;
    private static final double EVALUATION_SPREAD_BASE_Y = (64 + 384) / 2;
    private static final double EVALUATION_START_BASE_Y = EVALUATION_SPREAD_BASE_Y - 64;
    private static final double EVALUATION_PAIR_SEPARATION_XZ = 4;
    private static final double EVALUATION_PAIR_SEPARATION_Y = 8;
    private static final long EVALUATION_SEGMENTS_SEED = 0; // Does not change per world seed, because the base formula shouldn't.
    private static final int N_EVALUATION_SEGMENTS = 512;
    private static final double AVERAGE_INTERPOLATION_STRIDE = Math.pow(EVALUATION_PAIR_SEPARATION_XZ * EVALUATION_PAIR_SEPARATION_XZ * EVALUATION_PAIR_SEPARATION_Y, 1.0 / 3.0);

    public static double calculateInterpolationSmoothingFactor(DensityFunction function) {
        double sumOfPowers = 0.0;
        for (SinglePointContextPair pair : EVALUATION_POINT_PAIRS) {
            double valueA = function.compute(pair.a());
            double valueB = function.compute(pair.b());
            double slopeToPower = (valueB - valueA) * (valueB - valueA) * pair.reciprocalDistanceSquared();
            slopeToPower *= slopeToPower;
            slopeToPower *= slopeToPower;
            sumOfPowers += slopeToPower;
        }
        double meanPower = sumOfPowers * (1.0 / EVALUATION_POINT_PAIRS.length);
        return Math.pow(meanPower, 1.0 / 8.0) * AVERAGE_INTERPOLATION_STRIDE / 2; // TODO remove /2
    }

    private record SinglePointContextPair(DensityFunction.SinglePointContext a, DensityFunction.SinglePointContext b, double reciprocalDistanceSquared) { }
    private static final SinglePointContextPair[] EVALUATION_POINT_PAIRS = new SinglePointContextPair[N_EVALUATION_SEGMENTS];
    static {
        RandomSource random = new XoroshiroRandomSource(EVALUATION_SEGMENTS_SEED);
        for (int i = 0; i < N_EVALUATION_SEGMENTS; i++) {

            // Distribute base sampling points in an ellipsoid.
            // An isotropic distribution doesn't matter too much here, but there are places that it really does matter, such as world feature generation.
            // Having experience implementing this there makes it straightforward and quick to also use them where it maybe only slightly matters.
            double x0, y0, z0;
            {
                double sphereY = random.nextDouble() * 2.0 - 1.0;
                double sphereTheta = random.nextDouble() * (2.0 * Math.PI);
                double sphereXZScale = Math.sqrt(1.0 - sphereY * sphereY);
                double radiusScale = (1.0 - Math.abs(random.nextDouble() - random.nextDouble()));
                double radiusScaleXZ = radiusScale * EVALUATION_SPREAD_BASE_XZ;
                double radiusScaleY = radiusScale * EVALUATION_SPREAD_BASE_Y;

                x0 = radiusScaleXZ * sphereXZScale * Math.cos(sphereTheta);
                y0 = radiusScaleY * sphereY + EVALUATION_START_BASE_Y;
                z0 = radiusScaleXZ * sphereXZScale * Math.sin(sphereTheta);
            }

            // Displace a second point from the first in a similar manner.
            double x1, y1, z1;
            {
                double sphereY = random.nextDouble() * 2.0 - 1.0;
                double sphereTheta = random.nextDouble() * (2.0 * Math.PI);
                double sphereXZScale = Math.sqrt(1.0 - sphereY * sphereY);
                double radiusScale = (1.0 - Math.abs(random.nextDouble() - random.nextDouble()));
                double radiusScaleXZ = radiusScale * EVALUATION_PAIR_SEPARATION_XZ;
                double radiusScaleY = radiusScale * EVALUATION_PAIR_SEPARATION_Y;

                x1 = x0 + radiusScaleXZ * sphereXZScale * Math.cos(sphereTheta);
                y1 = y0 + radiusScaleY * sphereY;
                z1 = z0 + radiusScaleXZ * sphereXZScale * Math.sin(sphereTheta);
            }

            EVALUATION_POINT_PAIRS[i] = new SinglePointContextPair(
                    new DensityFunction.SinglePointContext((int)Math.round(x0), (int)Math.round(y0), (int)Math.round(z0)),
                    new DensityFunction.SinglePointContext((int)Math.round(x1), (int)Math.round(y1), (int)Math.round(z1)),
                    1.0 / ((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0))
            );
        }
    }

}
