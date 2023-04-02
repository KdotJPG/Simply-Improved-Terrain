package jpg.k.simplyimprovedterrain.util.noise;

import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * K.jpg's Domain-Rotated Shelf Noise. Now with tilt!
 * To serve as replacement for Minecraft's terrain noise. This produces discontinuities to create shelf effects,
 * but it eliminates the visible square alignment in X/Z slices and varies the spacing of the shelves locally.
 * It also takes a smoothing factor to eliminate the need for interpolation to smooth the discontinuities.
 *
 * Perlin grid alignment is hidden by using a domain rotation so that Y points up the main diagonal of the noise.
 * The shelves are oriented perpendicular to this axis, then are either given an upward offset or are omitted.
 * The shelves work by stopping the gradient contribution above the boundary, with a smooth transition.
 *
 * Always domain-rotate your Perlin, folks.
 */
public class DomainRotatedShelfNoise {

    private static final double SHELF_OCCURRENCE_RATE = 0.65;
    private static final double SHELF_MAX_TILT_AMOUNT = 0.072;

    private static final double ROOT3 = 1.7320508075688772;
    private static final double ROOT3OVER2 = 0.8660254037844386;
    private static final double ROOT3OVER3 = 0.577350269189626;
    private static final double ROTATE_3D_ORTHOGONALIZER = -0.211324865405187;

    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Y = 0x598CD327003817B5L;
    private static final long PRIME_Z = 0x5BCC226E9FA0BACBL;
    private static final long HASH_MULTIPLIER_A = 0x53A3F72DEEC546F5L;
    private static final long HASH_MULTIPLIER_B = 0x7DF3BB00907DD40DL;

    private static final int N_GRADS = 48;
    private static final int N_GRADS_PADDED = N_GRADS * 4 / 3;
    private static final int N_GRADS_PADDED_MASK = N_GRADS_PADDED - 1;

    private static final int GRAD_PSEUDOMOD_BITS = 26;
    private static final int GRAD_PSEUDOMOD_MASK = (1 << GRAD_PSEUDOMOD_BITS) - 1;
    private static final int GRAD_PSEUDOMOD_MULTIPLIER = (1 << GRAD_PSEUDOMOD_BITS) * 4 / 3;

    private static final int SHELF_OFFSET_BITS = Math.min(31, 64 - 2 * GRAD_PSEUDOMOD_BITS);
    private static final int SHELF_OFFSET_DIVISOR = 1 << SHELF_OFFSET_BITS;
    private static final int SHELF_OFFSET_MASK = SHELF_OFFSET_DIVISOR - 1;
    private static final double SHELF_OFFSET_FROM_HASH = 1.0 / (SHELF_OFFSET_DIVISOR * SHELF_OCCURRENCE_RATE);

    public static final double NOISE_NORMALIZATION_DIVISOR = 2.742445288166158;

    private final long seedA, seedB;

    public DomainRotatedShelfNoise(Random rand) {
        seedA = rand.nextLong();
        seedB = rand.nextLong();
    }

    public double noise3(double x, double y, double z, double shelfSmoothFactor) {

        // Start each noise layer in the center of a rotated cell to give spawn variety
        // in absence of full per-layer offsetting.
        y += ROOT3OVER2;

        // Re-orient the cubic lattice without skewing, to make Y point down <1,1,1>.
        // This hides the vast majority of the square alignment characteristic of Perlin, in X/Z planes.
        double xz = x + z;
        double s2 = xz * ROTATE_3D_ORTHOGONALIZER;
        double yy = y * ROOT3OVER3;
        double xr = x + s2 + yy; double zr = z + s2 + yy;
        double yr = xz * -ROOT3OVER3 + yy;

        // The rest is a modified Perlin.
        int xrb = MathHelper.floor(xr), yrb = MathHelper.floor(yr), zrb = MathHelper.floor(zr);
        double xri = xr - xrb, yri = yr - yrb, zri = zr - zrb;
        long xrbp = xrb * PRIME_X, yrbp = yrb * PRIME_Y, zrbp = zrb * PRIME_Z;
        double inverseShelfSmoothFactor = 1.0 / shelfSmoothFactor;
        double g000 = grad(xrbp, yrbp, zrbp, xri, yri, zri, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g001 = grad(xrbp, yrbp, zrbp + PRIME_Z, xri, yri, zri - 1, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g010 = grad(xrbp, yrbp + PRIME_Y, zrbp, xri, yri - 1, zri, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g011 = grad(xrbp, yrbp + PRIME_Y, zrbp + PRIME_Z, xri, yri - 1, zri - 1, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g100 = grad(xrbp + PRIME_X, yrbp, zrbp, xri - 1, yri, zri, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g101 = grad(xrbp + PRIME_X, yrbp, zrbp + PRIME_Z, xri - 1, yri, zri - 1, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g110 = grad(xrbp + PRIME_X, yrbp + PRIME_Y, zrbp, xri - 1, yri - 1, zri, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g111 = grad(xrbp + PRIME_X, yrbp + PRIME_Y, zrbp + PRIME_Z, xri - 1, yri - 1, zri - 1, shelfSmoothFactor, inverseShelfSmoothFactor);
        double fadeX = fadeCurve(xri);
        double fadeY = fadeCurve(yri);
        double fadeZ = fadeCurve(zri);
        double g00Z = (1 - fadeZ) * g000 + fadeZ * g001;
        double g01Z = (1 - fadeZ) * g010 + fadeZ * g011;
        double g10Z = (1 - fadeZ) * g100 + fadeZ * g101;
        double g11Z = (1 - fadeZ) * g110 + fadeZ * g111;
        double g0YZ = (1 - fadeY) * g00Z + fadeY * g01Z;
        double g1YZ = (1 - fadeY) * g10Z + fadeY * g11Z;
        return (1 - fadeX) * g0YZ + fadeX * g1YZ;
    }

    /*
     * Utility
     */

    private double grad(long xrbp, long yrbp, long zrbp, double dx, double dy, double dz, double shelfSmoothFactor, double inverseShelfSmoothFactor) {
        long hash = xrbp ^ yrbp ^ zrbp;
        long hashA = ((seedA ^ hash) * HASH_MULTIPLIER_A);
        long hashB = ((seedB ^ hash) * HASH_MULTIPLIER_B);
        hash = hashA ^ (hashB >> (64 - GRAD_PSEUDOMOD_BITS));

        // Pseudo-modulo to sample the 48 gradients as uniformly and efficiently as possible at the same time.
        int pseudoModGrad = (int)(hash & GRAD_PSEUDOMOD_MASK) * GRAD_PSEUDOMOD_MULTIPLIER;
        pseudoModGrad >>= (GRAD_PSEUDOMOD_BITS - 2);
        int gradIndex = pseudoModGrad & (N_GRADS_PADDED_MASK << 2);
        double value = GRADIENTS_PADDED[gradIndex | 0] * dx + GRADIENTS_PADDED[gradIndex | 1] * dy + GRADIENTS_PADDED[gradIndex | 2] * dz;

        // Shelf offset 0 to 1 -> offset from vertex along tilted domain-rotated vertical direction. Above -> discard
        double shelfOffset = (int)((hash >> (2 * GRAD_PSEUDOMOD_BITS)) & SHELF_OFFSET_MASK) * SHELF_OFFSET_FROM_HASH;
        if (shelfOffset < 1.0) {

            // Now use it to choose a tilt vector
            int pseudoModTilt = (int) ((hash >> GRAD_PSEUDOMOD_BITS) & GRAD_PSEUDOMOD_MASK) * GRAD_PSEUDOMOD_MULTIPLIER;
            pseudoModTilt >>= (GRAD_PSEUDOMOD_BITS - 2);
            int tiltIndex = pseudoModTilt & (N_GRADS_PADDED_MASK << 2);
            double toShelf = SHELF_TILT_VECTORS[tiltIndex | 0] * dx + SHELF_TILT_VECTORS[tiltIndex | 1] * dy + SHELF_TILT_VECTORS[tiltIndex | 2] * dz - shelfOffset;

            // Above shelf and smoothing -> no gradient contribution here.
            if (toShelf - shelfSmoothFactor > 0) {
                return 0;
            }

            // Gradient fades out within shelf smoothing range.
            if (toShelf + shelfSmoothFactor > 0) {
                value *= shelfFadeCurve(toShelf * inverseShelfSmoothFactor);
            }
        }

        // Otherwise, full gradient contribution.
        return value;
    }

    private static double fadeCurve(double t) {
        return t * t * t * (10 + t * (-15 + t * 6));
    }

    // Domain [-1,1] instead of [0,1]. Output flipped 1->0 instead of 0->1.
    private static double shelfFadeCurve(double t) {
        return 0.5 + t * (-0.9375 + t * t * (0.625 + t * t * -0.1875));
    }

    /*
     * Gradients
     */

    private static final double[] GRADIENTS_PADDED;
    private static final double[] SHELF_TILT_VECTORS;
    static {
        double[] gradients = {
                 2.22474487139f,       2.22474487139f,      -1.0f,
                 2.22474487139f,       2.22474487139f,       1.0f,
                 3.0862664687972017f,  1.1721513422464978f,  0.0f,
                 1.1721513422464978f,  3.0862664687972017f,  0.0f,
                -2.22474487139f,       2.22474487139f,      -1.0f,
                -2.22474487139f,       2.22474487139f,       1.0f,
                -1.1721513422464978f,  3.0862664687972017f,  0.0f,
                -3.0862664687972017f,  1.1721513422464978f,  0.0f,
                -1.0f,                -2.22474487139f,      -2.22474487139f,
                 1.0f,                -2.22474487139f,      -2.22474487139f,
                 0.0f,                -3.0862664687972017f, -1.1721513422464978f,
                 0.0f,                -1.1721513422464978f, -3.0862664687972017f,
                -1.0f,                -2.22474487139f,       2.22474487139f,
                 1.0f,                -2.22474487139f,       2.22474487139f,
                 0.0f,                -1.1721513422464978f,  3.0862664687972017f,
                 0.0f,                -3.0862664687972017f,  1.1721513422464978f,
                -2.22474487139f,      -2.22474487139f,      -1.0f,
                -2.22474487139f,      -2.22474487139f,       1.0f,
                -3.0862664687972017f, -1.1721513422464978f,  0.0f,
                -1.1721513422464978f, -3.0862664687972017f,  0.0f,
                -2.22474487139f,      -1.0f,                -2.22474487139f,
                -2.22474487139f,       1.0f,                -2.22474487139f,
                -1.1721513422464978f,  0.0f,                -3.0862664687972017f,
                -3.0862664687972017f,  0.0f,                -1.1721513422464978f,
                -2.22474487139f,      -1.0f,                 2.22474487139f,
                -2.22474487139f,       1.0f,                 2.22474487139f,
                -3.0862664687972017f,  0.0f,                 1.1721513422464978f,
                -1.1721513422464978f,  0.0f,                 3.0862664687972017f,
                -1.0f,                 2.22474487139f,      -2.22474487139f,
                 1.0f,                 2.22474487139f,      -2.22474487139f,
                 0.0f,                 1.1721513422464978f, -3.0862664687972017f,
                 0.0f,                 3.0862664687972017f, -1.1721513422464978f,
                -1.0f,                 2.22474487139f,       2.22474487139f,
                 1.0f,                 2.22474487139f,       2.22474487139f,
                 0.0f,                 3.0862664687972017f,  1.1721513422464978f,
                 0.0f,                 1.1721513422464978f,  3.0862664687972017f,
                 2.22474487139f,      -2.22474487139f,      -1.0f,
                 2.22474487139f,      -2.22474487139f,       1.0f,
                 1.1721513422464978f, -3.0862664687972017f,  0.0f,
                 3.0862664687972017f, -1.1721513422464978f,  0.0f,
                 2.22474487139f,      -1.0f,                -2.22474487139f,
                 2.22474487139f,       1.0f,                -2.22474487139f,
                 3.0862664687972017f,  0.0f,                -1.1721513422464978f,
                 1.1721513422464978f,  0.0f,                -3.0862664687972017f,
                 2.22474487139f,      -1.0f,                 2.22474487139f,
                 2.22474487139f,       1.0f,                 2.22474487139f,
                 1.1721513422464978f,  0.0f,                 3.0862664687972017f,
                 3.0862664687972017f,  0.0f,                 1.1721513422464978f,
        };
        final int N_GROUPED_GRADS_SOURCE = 3;
        final int N_GROUPED_GRADS_DESTINATION = 4;
        final int N_COMPONENTS_PER_GRAD_SOURCE = 3;
        final int N_COMPONENTS_PER_GRAD_DESTINATION = 4;
        final double GRADIENT_MAGNITUDE_UNSCALED = 3.3013602477694275;
        final double GRADIENT_TO_ROOT_3_MAGNITUDE = ROOT3 / GRADIENT_MAGNITUDE_UNSCALED;
        GRADIENTS_PADDED = new double[N_GRADS_PADDED * N_COMPONENTS_PER_GRAD_DESTINATION];
        SHELF_TILT_VECTORS = new double[N_GRADS_PADDED * N_COMPONENTS_PER_GRAD_DESTINATION];

        // Pre-process the noise gradients and generate shelf tilt vectors using them as a base.
        double[] shelfTiltVectors = new double[gradients.length];
        for (int gradIndex = 0; gradIndex < N_GRADS; gradIndex++) {

            // Scale the noise gradient to give the noise a value range of -1 to 1.
            // Also compute its dot product with <1, 1, 1> (the domain-rotated vertical direction).
            double gradientComponentSum = 0;
            for (int componentIndex = 0; componentIndex < N_COMPONENTS_PER_GRAD_SOURCE; componentIndex++) {
                int arrayIndex = gradIndex * N_COMPONENTS_PER_GRAD_SOURCE + componentIndex;
                double component = gradients[arrayIndex];
                gradients[arrayIndex] = component / NOISE_NORMALIZATION_DIVISOR;
                shelfTiltVectors[arrayIndex] = component;
                gradientComponentSum += component;
            }

            // Use SHELF_TILT_AMOUNT to pick vectors between <1, 1, 1> and the gradients (rescaled to the same magnitude)
            double tiltVectorMagnitudeSquared = 0;
            double gradientFlipAndToRoot3Magnitude = (gradientComponentSum < 0 ? -1 : 1) * GRADIENT_TO_ROOT_3_MAGNITUDE;
            for (int componentIndex = 0; componentIndex < N_COMPONENTS_PER_GRAD_SOURCE; componentIndex++) {
                int arrayIndex = gradIndex * N_COMPONENTS_PER_GRAD_SOURCE + componentIndex;
                double component = shelfTiltVectors[arrayIndex] * gradientFlipAndToRoot3Magnitude;
                component = MathHelper.lerp(SHELF_MAX_TILT_AMOUNT, 1.0, component);
                shelfTiltVectors[arrayIndex] = component;
                tiltVectorMagnitudeSquared += component * component;
            }

            // Now rescale the tilt vectors to be the same magnitude as <1, 1, 1> again.
            double tiltVectorBackToRoot3Magnitude = Math.sqrt(3 / tiltVectorMagnitudeSquared);
            for (int componentIndex = 0; componentIndex < N_COMPONENTS_PER_GRAD_SOURCE; componentIndex++) {
                int arrayIndex = gradIndex * N_COMPONENTS_PER_GRAD_SOURCE + componentIndex;
                shelfTiltVectors[arrayIndex] *= tiltVectorBackToRoot3Magnitude;
            }

        }

        // Copy gradients repeating every first of three, like 0,0,1,2,3,3,4,5,6,6,7,8,...
        // where also each three-component entry is padded with a fourth coordinate.
        // This enables simple pseudo-modulo and bit-shift access.
        final int N_GRAD_GROUPS = N_GRADS / N_GROUPED_GRADS_SOURCE;
        for (int groupIndex = 0; groupIndex < N_GRAD_GROUPS; groupIndex++) {

            // Copy first gradient to be repeated.
            // Also copy tilt vector.
            {
                int srcArrayIndex = groupIndex * N_GROUPED_GRADS_SOURCE * N_COMPONENTS_PER_GRAD_SOURCE;
                int destArrayIndex = groupIndex * N_GROUPED_GRADS_DESTINATION * N_COMPONENTS_PER_GRAD_DESTINATION;
                for (int componentIndex = 0; componentIndex < N_COMPONENTS_PER_GRAD_SOURCE; componentIndex++) {
                    GRADIENTS_PADDED[destArrayIndex + componentIndex] = gradients[srcArrayIndex + componentIndex];
                    SHELF_TILT_VECTORS[destArrayIndex + componentIndex] = shelfTiltVectors[srcArrayIndex + componentIndex];
                }
                GRADIENTS_PADDED[destArrayIndex + 3] = 0.0f;
                SHELF_TILT_VECTORS[destArrayIndex + 3] = 0.0f;
            }

            // Fill gradients into remaining destination array slots, including repetition of the above.
            // Also copy tilt vectors.
            for (int groupGradIndex = 0; groupGradIndex < 3; groupGradIndex++) {
                int srcArrayIndex = (groupIndex * N_GROUPED_GRADS_SOURCE + groupGradIndex) * N_COMPONENTS_PER_GRAD_SOURCE;
                int destArrayIndex = (groupIndex * N_GROUPED_GRADS_DESTINATION + (groupGradIndex + 1)) * N_COMPONENTS_PER_GRAD_DESTINATION;
                for (int componentIndex = 0; componentIndex < N_COMPONENTS_PER_GRAD_SOURCE; componentIndex++) {
                    GRADIENTS_PADDED[destArrayIndex + componentIndex] = gradients[srcArrayIndex + componentIndex];
                    SHELF_TILT_VECTORS[destArrayIndex + componentIndex] = shelfTiltVectors[srcArrayIndex + componentIndex];
                }
                GRADIENTS_PADDED[destArrayIndex + 3] = 0.0f;
                SHELF_TILT_VECTORS[destArrayIndex + 3] = 0.0f;
            }

        }
    }

}
