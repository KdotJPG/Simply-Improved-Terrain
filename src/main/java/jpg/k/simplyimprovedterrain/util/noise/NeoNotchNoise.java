package jpg.k.simplyimprovedterrain.util.noise;

import net.minecraft.world.gen.ChunkRandom;

/**
 * K.jpg's Neo-Notch Noise.
 * To serve as replacement for Minecraft's "Notch noise". This produces discontinuities to create shelf effects,
 * but it also eliminates the visible square alignment in X/Z slices, and varies the spacing of the shelves.
 * It also takes a smoothing factor , to eliminate the need for interpolation to smooth out the discontinuities.
 *
 * Perlin grid alignment is hidden by using a domain rotation so that Y points up the main diagonal of the noise.
 * The shelves are oriented perpendicular to this axis, then are either given an upward offset or are omitted.
 * The shelves work by stopping the gradient contribution above the boundary, with a smooth transition.
 */
public class NeoNotchNoise {

    private static final int PSIZE = 2048;
    private static final int PMASK = 2047;

    private short[] perm;
    private Grad3[] permGrad3;

    public NeoNotchNoise(long seed) {
        perm = new short[PSIZE];
        permGrad3 = new Grad3[PSIZE];
        short[] source = new short[PSIZE];
        for (short i = 0; i < PSIZE; i++)
            source[i] = i;
        for (int i = PSIZE - 1; i >= 0; i--) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int r = (int)((seed + 31) % (i + 1));
            if (r < 0)
                r += (i + 1);
            perm[i] = source[r];
            permGrad3[i] = GRADIENTS_3D[perm[i]];
            source[r] = source[i];
        }
    }

    public NeoNotchNoise(ChunkRandom rand) {
        perm = new short[PSIZE];
        permGrad3 = new Grad3[PSIZE];
        short[] source = new short[PSIZE];
        for (short i = 0; i < PSIZE; i++)
            source[i] = i;
        for (int i = PSIZE - 1; i >= 0; i--) {
            int r = rand.nextInt(i + 1);
            if (r < 0)
                r += (i + 1);
            perm[i] = source[r];
            permGrad3[i] = GRADIENTS_3D[perm[i]];
            source[r] = source[i];
        }
    }

    public double noise3(double x, double y, double z, double shelfSmoothFactor) {

        // Re-orient the cubic lattice without skewing, to make Y look down <1,1,1>.
        // This hides the vast majority of the square alignment characteristic of Perlin, in X/Z planes.
        double xz = x + z;
        double s2 = xz * -0.211324865405187;
        double yy = y * 0.577350269189626;
        double xr = x + s2 + yy; double zr = z + s2 + yy;
        double yr = xz * -0.577350269189626 + yy;

        // The rest is a modified Perlin.
        int xrb = fastFloor(xr), yrb = fastFloor(yr), zrb = fastFloor(zr);
        double xri = xr - xrb, yri = yr - yrb, zri = zr - zrb;
        double g000 = grad3(permGrad3[perm[perm[xrb & PMASK] ^ (yrb & PMASK)] ^ (zrb & PMASK)], xri, yri, zri, shelfSmoothFactor);
        double g001 = grad3(permGrad3[perm[perm[xrb & PMASK] ^ (yrb & PMASK)] ^ ((zrb + 1) & PMASK)], xri, yri, zri - 1, shelfSmoothFactor);
        double g010 = grad3(permGrad3[perm[perm[xrb & PMASK] ^ ((yrb + 1) & PMASK)] ^ (zrb & PMASK)], xri, yri - 1, zri, shelfSmoothFactor);
        double g011 = grad3(permGrad3[perm[perm[xrb & PMASK] ^ ((yrb + 1) & PMASK)] ^ ((zrb + 1) & PMASK)], xri, yri - 1, zri - 1, shelfSmoothFactor);
        double g100 = grad3(permGrad3[perm[perm[(xrb + 1) & PMASK] ^ (yrb & PMASK)] ^ (zrb & PMASK)], xri - 1, yri, zri, shelfSmoothFactor);
        double g101 = grad3(permGrad3[perm[perm[(xrb + 1) & PMASK] ^ (yrb & PMASK)] ^ ((zrb + 1) & PMASK)], xri - 1, yri, zri - 1, shelfSmoothFactor);
        double g110 = grad3(permGrad3[perm[perm[(xrb + 1) & PMASK] ^ ((yrb + 1) & PMASK)] ^ (zrb & PMASK)], xri - 1, yri - 1, zri, shelfSmoothFactor);
        double g111 = grad3(permGrad3[perm[perm[(xrb + 1) & PMASK] ^ ((yrb + 1) & PMASK)] ^ ((zrb + 1) & PMASK)], xri - 1, yri - 1, zri - 1, shelfSmoothFactor);
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

    private static double grad3(Grad3 grad, double dx, double dy, double dz, double shelfSmoothFactor) {
        double toShelf = dx + dy + dz + grad.shelfOffset;
        if (toShelf - shelfSmoothFactor > 0) return 0;
        double value = grad.dx * dx + grad.dy * dy + grad.dz * dz;
        if (toShelf + shelfSmoothFactor > 0) {
            value *= shelfFadeCurve(toShelf * (1.0 / shelfSmoothFactor)); // Will this multiply-by-inverse approach make the JVM more able to optimize?
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

    public static class Grad3 {
        double dx, dy, dz, shelfOffset;
        public Grad3(double dx, double dy, double dz) {
            this.dx = dx; this.dy = dy; this.dz = dz;
        }
        public Grad3(double dx, double dy, double dz, double shelfOffset) {
            this.dx = dx; this.dy = dy; this.dz = dz; this.shelfOffset = shelfOffset;
        }
    }

    public static final double N3 = 2.742445288166158;
    private static final Grad3[] GRADIENTS_3D;
    static {

        // Normalized expanded rhombic dodecahedron from OSN2 because I think it looks better than the cuboctahedron,
        // and we might as well take advantage of the fact that we can put anything we want here.
        Grad3[] grad3 = {
                new Grad3(-2.22474487139,      -2.22474487139,      -1.0),
                new Grad3(-2.22474487139,      -2.22474487139,       1.0),
                new Grad3(-3.0862664687972017, -1.1721513422464978,  0.0),
                new Grad3(-1.1721513422464978, -3.0862664687972017,  0.0),
                new Grad3(-2.22474487139,      -1.0,                -2.22474487139),
                new Grad3(-2.22474487139,       1.0,                -2.22474487139),
                new Grad3(-1.1721513422464978,  0.0,                -3.0862664687972017),
                new Grad3(-3.0862664687972017,  0.0,                -1.1721513422464978),
                new Grad3(-2.22474487139,      -1.0,                 2.22474487139),
                new Grad3(-2.22474487139,       1.0,                 2.22474487139),
                new Grad3(-3.0862664687972017,  0.0,                 1.1721513422464978),
                new Grad3(-1.1721513422464978,  0.0,                 3.0862664687972017),
                new Grad3(-2.22474487139,       2.22474487139,      -1.0),
                new Grad3(-2.22474487139,       2.22474487139,       1.0),
                new Grad3(-1.1721513422464978,  3.0862664687972017,  0.0),
                new Grad3(-3.0862664687972017,  1.1721513422464978,  0.0),
                new Grad3(-1.0,                -2.22474487139,      -2.22474487139),
                new Grad3( 1.0,                -2.22474487139,      -2.22474487139),
                new Grad3( 0.0,                -3.0862664687972017, -1.1721513422464978),
                new Grad3( 0.0,                -1.1721513422464978, -3.0862664687972017),
                new Grad3(-1.0,                -2.22474487139,       2.22474487139),
                new Grad3( 1.0,                -2.22474487139,       2.22474487139),
                new Grad3( 0.0,                -1.1721513422464978,  3.0862664687972017),
                new Grad3( 0.0,                -3.0862664687972017,  1.1721513422464978),
                new Grad3(-1.0,                 2.22474487139,      -2.22474487139),
                new Grad3( 1.0,                 2.22474487139,      -2.22474487139),
                new Grad3( 0.0,                 1.1721513422464978, -3.0862664687972017),
                new Grad3( 0.0,                 3.0862664687972017, -1.1721513422464978),
                new Grad3(-1.0,                 2.22474487139,       2.22474487139),
                new Grad3( 1.0,                 2.22474487139,       2.22474487139),
                new Grad3( 0.0,                 3.0862664687972017,  1.1721513422464978),
                new Grad3( 0.0,                 1.1721513422464978,  3.0862664687972017),
                new Grad3( 2.22474487139,      -2.22474487139,      -1.0),
                new Grad3( 2.22474487139,      -2.22474487139,       1.0),
                new Grad3( 1.1721513422464978, -3.0862664687972017,  0.0),
                new Grad3( 3.0862664687972017, -1.1721513422464978,  0.0),
                new Grad3( 2.22474487139,      -1.0,                -2.22474487139),
                new Grad3( 2.22474487139,       1.0,                -2.22474487139),
                new Grad3( 3.0862664687972017,  0.0,                -1.1721513422464978),
                new Grad3( 1.1721513422464978,  0.0,                -3.0862664687972017),
                new Grad3( 2.22474487139,      -1.0,                 2.22474487139),
                new Grad3( 2.22474487139,       1.0,                 2.22474487139),
                new Grad3( 1.1721513422464978,  0.0,                 3.0862664687972017),
                new Grad3( 3.0862664687972017,  0.0,                 1.1721513422464978),
                new Grad3( 2.22474487139,       2.22474487139,      -1.0),
                new Grad3( 2.22474487139,       2.22474487139,       1.0),
                new Grad3( 3.0862664687972017,  1.1721513422464978,  0.0),
                new Grad3( 1.1721513422464978,  3.0862664687972017,  0.0)
        };
        GRADIENTS_3D = new Grad3[PSIZE];
        for (int i = 0; i < grad3.length; i++) {
            grad3[i].dx /= N3; grad3[i].dy /= N3; grad3[i].dz /= N3;
        }
        for (int i = 0; i < PSIZE; i++) {
            double j = i / grad3.length; // j range 0 to 42, because floor(PSIZE/gradlength = 2047/48). Spends less time at 42 than the others.
            double shelfOffset = j > 24 ? Double.NEGATIVE_INFINITY : -j / 24.0; // Range -1 to 0, or negative infinity for no shelf.
            Grad3 grad = grad3[i % grad3.length];
            GRADIENTS_3D[i] = new Grad3(grad.dx, grad.dy, grad.dz, shelfOffset);
        }
    }
}
