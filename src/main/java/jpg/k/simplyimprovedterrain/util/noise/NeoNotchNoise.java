package jpg.k.simplyimprovedterrain.util.noise;

/**
 * K.jpg's Neo-Notch Noise.
 * To serve as replacement for Minecraft's "Notch noise". This produces discontinuities to create shelf effects,
 * but it also eliminates the visible square alignment in X/Z slices, and varies the spacing of the shelves.
 * It also takes a smoothing factor, to eliminate the need for interpolation to smooth the discontinuities.
 *
 * Perlin grid alignment is hidden by using a domain rotation so that Y points up the main diagonal of the noise.
 * The shelves are oriented perpendicular to this axis, then are either given an upward offset or are omitted.
 * The shelves work by stopping the gradient contribution above the boundary, with a smooth transition.
 * 
 * Always domain-rotate your Perlin, folks.
 */
public class NeoNotchNoise {

    private static final int PSIZE = 2048;
    private static final int PMASK = 2047;

    private short[] perm;

    public NeoNotchNoise(long seed) {
        perm = new short[PSIZE];
        short[] source = new short[PSIZE];
        for (short i = 0; i < PSIZE; i++)
            source[i] = i;
        for (int i = PSIZE - 1; i >= 0; i--) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int r = (int)((seed + 31) % (i + 1));
            if (r < 0)
                r += (i + 1);
            perm[i] = source[r];
            source[r] = source[i];
        }
    }

    public NeoNotchNoise(java.util.Random rand) {
        perm = new short[PSIZE];
        short[] source = new short[PSIZE];
        for (short i = 0; i < PSIZE; i++)
            source[i] = i;
        for (int i = PSIZE - 1; i >= 0; i--) {
            int r = rand.nextInt(i + 1);
            if (r < 0)
                r += (i + 1);
            perm[i] = source[r];
            source[r] = source[i];
        }
    }

    public double noise3(double x, double y, double z, double shelfSmoothFactor) {

        // Re-orient the cubic lattice without skewing, to make Y point down <1,1,1>.
        // This hides the vast majority of the square alignment characteristic of Perlin, in X/Z planes.
        double xz = x + z;
        double s2 = xz * -0.211324865405187;
        double yy = y * 0.577350269189626;
        double xr = x + s2 + yy; double zr = z + s2 + yy;
        double yr = xz * -0.577350269189626 + yy;

        // The rest is a modified Perlin.
        int xrb = fastFloor(xr), yrb = fastFloor(yr), zrb = fastFloor(zr);
        double xri = xr - xrb, yri = yr - yrb, zri = zr - zrb;
        double inverseShelfSmoothFactor = 1.0 / shelfSmoothFactor;
        double g000 = grad3(perm[perm[perm[xrb & PMASK] ^ (yrb & PMASK)] ^ (zrb & PMASK)] << 2, xri, yri, zri, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g001 = grad3(perm[perm[perm[xrb & PMASK] ^ (yrb & PMASK)] ^ ((zrb + 1) & PMASK)] << 2, xri, yri, zri - 1, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g010 = grad3(perm[perm[perm[xrb & PMASK] ^ ((yrb + 1) & PMASK)] ^ (zrb & PMASK)] << 2, xri, yri - 1, zri, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g011 = grad3(perm[perm[perm[xrb & PMASK] ^ ((yrb + 1) & PMASK)] ^ ((zrb + 1) & PMASK)] << 2, xri, yri - 1, zri - 1, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g100 = grad3(perm[perm[perm[(xrb + 1) & PMASK] ^ (yrb & PMASK)] ^ (zrb & PMASK)] << 2, xri - 1, yri, zri, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g101 = grad3(perm[perm[perm[(xrb + 1) & PMASK] ^ (yrb & PMASK)] ^ ((zrb + 1) & PMASK)] << 2, xri - 1, yri, zri - 1, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g110 = grad3(perm[perm[perm[(xrb + 1) & PMASK] ^ ((yrb + 1) & PMASK)] ^ (zrb & PMASK)] << 2, xri - 1, yri - 1, zri, shelfSmoothFactor, inverseShelfSmoothFactor);
        double g111 = grad3(perm[perm[perm[(xrb + 1) & PMASK] ^ ((yrb + 1) & PMASK)] ^ ((zrb + 1) & PMASK)] << 2, xri - 1, yri - 1, zri - 1, shelfSmoothFactor, inverseShelfSmoothFactor);
        double fadeX = fadeCurve(xri);
        double fadeY = fadeCurve(yri);
        double fadeZ = fadeCurve(zri);
        double g00Z = (1 - fadeZ) * g000 + fadeZ * g001;
        double g01Z = (1 - fadeZ) * g010 + fadeZ * g011;
        double g10Z = (1 - fadeZ) * g100 + fadeZ * g101;
        double g11Z = (1 - fadeZ) * g110 + fadeZ * g111;
        double g0YZ = (1 - fadeY) * g00Z + fadeY * g01Z;
        double g1YZ = (1 - fadeY) * g10Z + fadeY * g11Z;
        double gXYZ = (1 - fadeX) * g0YZ + fadeX * g1YZ;

        return gXYZ;
    }

    /*
     * Utility
     */

    private static int fastFloor(double x) {
        int xi = (int)x;
        return x < xi ? xi - 1 : xi;
    }

    private static double grad3(int index, double dx, double dy, double dz, double shelfSmoothFactor, double inverseShelfSmoothFactor) {
        double toShelf = dx + dy + dz + GRADIENTS_3D[index | 3];
        if (toShelf - shelfSmoothFactor > 0) return 0;
        double value = GRADIENTS_3D[index | 0] * dx +GRADIENTS_3D[index | 1] * dy + GRADIENTS_3D[index | 2] * dz;
        if (toShelf + shelfSmoothFactor > 0) {
            value *= shelfFadeCurve(toShelf * inverseShelfSmoothFactor);
        }
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

    public static final double N3 = 2.742445288166158;
    private static final double[] GRADIENTS_3D;
    static {
        double[] grad3 = {
                -2.22474487139,      -2.22474487139,      -1.0,                 0.0,
                -2.22474487139,      -2.22474487139,       1.0,                 0.0,
                -3.0862664687972017, -1.1721513422464978,  0.0,                 0.0,
                -1.1721513422464978, -3.0862664687972017,  0.0,                 0.0,
                -2.22474487139,      -1.0,                -2.22474487139,       0.0,
                -2.22474487139,       1.0,                -2.22474487139,       0.0,
                -1.1721513422464978,  0.0,                -3.0862664687972017,  0.0,
                -3.0862664687972017,  0.0,                -1.1721513422464978,  0.0,
                -2.22474487139,      -1.0,                 2.22474487139,       0.0,
                -2.22474487139,       1.0,                 2.22474487139,       0.0,
                -3.0862664687972017,  0.0,                 1.1721513422464978,  0.0,
                -1.1721513422464978,  0.0,                 3.0862664687972017,  0.0,
                -2.22474487139,       2.22474487139,      -1.0,                 0.0,
                -2.22474487139,       2.22474487139,       1.0,                 0.0,
                -1.1721513422464978,  3.0862664687972017,  0.0,                 0.0,
                -3.0862664687972017,  1.1721513422464978,  0.0,                 0.0,
                -1.0,                -2.22474487139,      -2.22474487139,       0.0,
                 1.0,                -2.22474487139,      -2.22474487139,       0.0,
                 0.0,                -3.0862664687972017, -1.1721513422464978,  0.0,
                 0.0,                -1.1721513422464978, -3.0862664687972017,  0.0,
                -1.0,                -2.22474487139,       2.22474487139,       0.0,
                 1.0,                -2.22474487139,       2.22474487139,       0.0,
                 0.0,                -1.1721513422464978,  3.0862664687972017,  0.0,
                 0.0,                -3.0862664687972017,  1.1721513422464978,  0.0,
                -1.0,                 2.22474487139,      -2.22474487139,       0.0,
                 1.0,                 2.22474487139,      -2.22474487139,       0.0,
                 0.0,                 1.1721513422464978, -3.0862664687972017,  0.0,
                 0.0,                 3.0862664687972017, -1.1721513422464978,  0.0,
                -1.0,                 2.22474487139,       2.22474487139,       0.0,
                 1.0,                 2.22474487139,       2.22474487139,       0.0,
                 0.0,                 3.0862664687972017,  1.1721513422464978,  0.0,
                 0.0,                 1.1721513422464978,  3.0862664687972017,  0.0,
                 2.22474487139,      -2.22474487139,      -1.0,                 0.0,
                 2.22474487139,      -2.22474487139,       1.0,                 0.0,
                 1.1721513422464978, -3.0862664687972017,  0.0,                 0.0,
                 3.0862664687972017, -1.1721513422464978,  0.0,                 0.0,
                 2.22474487139,      -1.0,                -2.22474487139,       0.0,
                 2.22474487139,       1.0,                -2.22474487139,       0.0,
                 3.0862664687972017,  0.0,                -1.1721513422464978,  0.0,
                 1.1721513422464978,  0.0,                -3.0862664687972017,  0.0,
                 2.22474487139,      -1.0,                 2.22474487139,       0.0,
                 2.22474487139,       1.0,                 2.22474487139,       0.0,
                 1.1721513422464978,  0.0,                 3.0862664687972017,  0.0,
                 3.0862664687972017,  0.0,                 1.1721513422464978,  0.0,
                 2.22474487139,       2.22474487139,      -1.0,                 0.0,
                 2.22474487139,       2.22474487139,       1.0,                 0.0,
                 3.0862664687972017,  1.1721513422464978,  0.0,                 0.0,
                 1.1721513422464978,  3.0862664687972017,  0.0,                 0.0
        };
        int nGrad3 = grad3.length / 4;

        GRADIENTS_3D = new double[PSIZE * 4];
        for (int i = 0; i < grad3.length; i++) {
            grad3[i] /= N3;
        }
        for (int i = 0; i < PSIZE; i++) {
            double j = i / nGrad3; // j range 0 to 42, because floor((PSIZE-1)/nGrad3 = 2047/48). Spends less time at 42 than the others.
            double shelfOffset = j > 24 ? Double.NEGATIVE_INFINITY : -j / 24.0; // Range -1 to 0, or negative infinity for no shelf.

            int grad3Index = (i % nGrad3) * 4;

            GRADIENTS_3D[i * 4 + 0] = grad3[grad3Index + 0];
            GRADIENTS_3D[i * 4 + 1] = grad3[grad3Index + 1];
            GRADIENTS_3D[i * 4 + 2] = grad3[grad3Index + 2];
            GRADIENTS_3D[i * 4 + 3] = shelfOffset;
        }
    }

}
