package jpg.k.simplyimprovedterrain.util.noise;

import java.util.ArrayList;

public class MetaballEndIslandNoise
{

    private static final double TRIANGLE_EDGE_LENGTH = Math.sqrt(2.0 / 3.0);
    private static final double TRIANGLE_HEIGHT = Math.sqrt(1.0 / 2.0);
    private static final double TRIANGLE_CIRCUMRADIUS = TRIANGLE_HEIGHT * (2.0 / 3.0);

    private static final double OUTPUT_MAX = 100;
    private static final double OUTPUT_MIN = -100;
    private static final double OUTPUT_RESCALE = OUTPUT_MAX - OUTPUT_MIN;
    private static final double RADIUS_MULTIPLIER_INVERSE = Math.sqrt(1 - Math.pow(-OUTPUT_MIN / OUTPUT_RESCALE, 1.0 / 3.0));
    private static final double RADIUS_MULTIPLIER = 1.0 / RADIUS_MULTIPLIER_INVERSE;
    private static final double OUTPUT_CLAMP = 80;

    private final double mainIslandRadiusRescaledSqInverse;
    private final double islandRadiusInverseRangeHashMultiplier;
    private final double islandMinRadiusRescaledInverse;
    private final double outerIslandBufferDistanceSq;
    private final double islandFrequency;
    private final LatticePoint[] pointsToSearch;

    private static final double JITTER_AMOUNT = TRIANGLE_EDGE_LENGTH;
    private static final double[] JITTER_VECTORS_128 = {
            0.130526192220052,  0.99144486137381,   0.38268343236509,   0.923879532511287,  0.608761429008721,  0.793353340291235,  0.793353340291235,  0.608761429008721,
            0.923879532511287,  0.38268343236509,   0.99144486137381,   0.130526192220051,  0.99144486137381,  -0.130526192220051,  0.923879532511287, -0.38268343236509,
            0.793353340291235, -0.60876142900872,   0.608761429008721, -0.793353340291235,  0.38268343236509,  -0.923879532511287,  0.130526192220052, -0.99144486137381,
            -0.130526192220052, -0.99144486137381,  -0.38268343236509,  -0.923879532511287, -0.608761429008721, -0.793353340291235, -0.793353340291235, -0.608761429008721,
            -0.923879532511287, -0.38268343236509,  -0.99144486137381,  -0.130526192220052, -0.99144486137381,   0.130526192220051, -0.923879532511287,  0.38268343236509,
            -0.793353340291235,  0.608761429008721, -0.608761429008721,  0.793353340291235, -0.38268343236509,   0.923879532511287, -0.130526192220052,  0.99144486137381,
            0.130526192220052,  0.99144486137381,   0.38268343236509,   0.923879532511287,  0.608761429008721,  0.793353340291235,  0.793353340291235,  0.608761429008721,
            0.923879532511287,  0.38268343236509,   0.99144486137381,   0.130526192220051,  0.99144486137381,  -0.130526192220051,  0.923879532511287, -0.38268343236509,
            0.793353340291235, -0.60876142900872,   0.608761429008721, -0.793353340291235,  0.38268343236509,  -0.923879532511287,  0.130526192220052, -0.99144486137381,
            -0.130526192220052, -0.99144486137381,  -0.38268343236509,  -0.923879532511287, -0.608761429008721, -0.793353340291235, -0.793353340291235, -0.608761429008721,
            -0.923879532511287, -0.38268343236509,  -0.99144486137381,  -0.130526192220052, -0.99144486137381,   0.130526192220051, -0.923879532511287,  0.38268343236509,
            -0.793353340291235,  0.608761429008721, -0.608761429008721,  0.793353340291235, -0.38268343236509,   0.923879532511287, -0.130526192220052,  0.99144486137381,
            0.130526192220052,  0.99144486137381,   0.38268343236509,   0.923879532511287,  0.608761429008721,  0.793353340291235,  0.793353340291235,  0.608761429008721,
            0.923879532511287,  0.38268343236509,   0.99144486137381,   0.130526192220051,  0.99144486137381,  -0.130526192220051,  0.923879532511287, -0.38268343236509,
            0.793353340291235, -0.60876142900872,   0.608761429008721, -0.793353340291235,  0.38268343236509,  -0.923879532511287,  0.130526192220052, -0.99144486137381,
            -0.130526192220052, -0.99144486137381,  -0.38268343236509,  -0.923879532511287, -0.608761429008721, -0.793353340291235, -0.793353340291235, -0.608761429008721,
            -0.923879532511287, -0.38268343236509,  -0.99144486137381,  -0.130526192220052, -0.99144486137381,   0.130526192220051, -0.923879532511287,  0.38268343236509,
            -0.793353340291235,  0.608761429008721, -0.608761429008721,  0.793353340291235, -0.38268343236509,   0.923879532511287, -0.130526192220052,  0.99144486137381,
            0.130526192220052,  0.99144486137381,   0.38268343236509,   0.923879532511287,  0.608761429008721,  0.793353340291235,  0.793353340291235,  0.608761429008721,
            0.923879532511287,  0.38268343236509,   0.99144486137381,   0.130526192220051,  0.99144486137381,  -0.130526192220051,  0.923879532511287, -0.38268343236509,
            0.793353340291235, -0.60876142900872,   0.608761429008721, -0.793353340291235,  0.38268343236509,  -0.923879532511287,  0.130526192220052, -0.99144486137381,
            -0.130526192220052, -0.99144486137381,  -0.38268343236509,  -0.923879532511287, -0.608761429008721, -0.793353340291235, -0.793353340291235, -0.608761429008721,
            -0.923879532511287, -0.38268343236509,  -0.99144486137381,  -0.130526192220052, -0.99144486137381,   0.130526192220051, -0.923879532511287,  0.38268343236509,
            -0.793353340291235,  0.608761429008721, -0.608761429008721,  0.793353340291235, -0.38268343236509,   0.923879532511287, -0.130526192220052,  0.99144486137381,
            0.130526192220052,  0.99144486137381,   0.38268343236509,   0.923879532511287,  0.608761429008721,  0.793353340291235,  0.793353340291235,  0.608761429008721,
            0.923879532511287,  0.38268343236509,   0.99144486137381,   0.130526192220051,  0.99144486137381,  -0.130526192220051,  0.923879532511287, -0.38268343236509,
            0.793353340291235, -0.60876142900872,   0.608761429008721, -0.793353340291235,  0.38268343236509,  -0.923879532511287,  0.130526192220052, -0.99144486137381,
            -0.130526192220052, -0.99144486137381,  -0.38268343236509,  -0.923879532511287, -0.608761429008721, -0.793353340291235, -0.793353340291235, -0.608761429008721,
            -0.923879532511287, -0.38268343236509,  -0.99144486137381,  -0.130526192220052, -0.99144486137381,   0.130526192220051, -0.923879532511287,  0.38268343236509,
            -0.793353340291235,  0.608761429008721, -0.608761429008721,  0.793353340291235, -0.38268343236509,   0.923879532511287, -0.130526192220052,  0.99144486137381,
            0.38268343236509,   0.923879532511287,  0.923879532511287,  0.38268343236509,   0.923879532511287, -0.38268343236509,   0.38268343236509,  -0.923879532511287,
            -0.38268343236509,  -0.923879532511287, -0.923879532511287, -0.38268343236509,  -0.923879532511287,  0.38268343236509,  -0.38268343236509,   0.923879532511287,
    };
    private static final int[] REMAINING_HASH_LOOKUP = new int[256];
    private static final int REMAINING_HASH_MAX_VALUE = 10;
    static {
        for (int i = 0; i < JITTER_VECTORS_128.length; i++) {
            JITTER_VECTORS_128[i] *= JITTER_AMOUNT;
            REMAINING_HASH_LOOKUP[i] = i > 240 ? 10 : (i & 1) + (i / 48) * 2;
        }
    }

    static class LatticePoint {
        public int xsv, zsv;
        public double dx, dz;
        public LatticePoint(int xsv, int zsv) {
            System.out.println(xsv + "," + zsv);
            this.xsv = xsv;
            this.zsv = zsv;
            double t = (xsv + zsv) * -0.211324865405187;
            this.dx = -(xsv + t);
            this.dz = -(zsv + t);
        }
    }

    public static final MetaballEndIslandNoise INSTANCE = new MetaballEndIslandNoise(100, 38, 89, 0x400, 0.004);

    public MetaballEndIslandNoise(double mainIslandRadius, double outerIslandMinRadius, double outerIslandMaxRadius, int outerIslandBufferDistance, double islandFrequency) {
        this.mainIslandRadiusRescaledSqInverse = (RADIUS_MULTIPLIER_INVERSE * RADIUS_MULTIPLIER_INVERSE)
                / ((mainIslandRadius * islandFrequency) * (mainIslandRadius * islandFrequency));
        outerIslandBufferDistance += outerIslandMaxRadius; // Prevent any islands from leaking into the void region, so their chorus fruit "biome" isn't cutoff.
        this.outerIslandBufferDistanceSq = (outerIslandBufferDistance * outerIslandBufferDistance) * (islandFrequency * islandFrequency);

        this.islandMinRadiusRescaledInverse = RADIUS_MULTIPLIER_INVERSE / (islandFrequency * outerIslandMinRadius);
        double islandMaxRadiusRescaledInverse = RADIUS_MULTIPLIER_INVERSE / (islandFrequency * outerIslandMaxRadius);
        this.islandRadiusInverseRangeHashMultiplier = (islandMaxRadiusRescaledInverse - islandMinRadiusRescaledInverse) / REMAINING_HASH_MAX_VALUE;

        this.islandFrequency = islandFrequency;
        double maxContributingDistance = (outerIslandMaxRadius/* + CHUNK_WIDTH*/) * islandFrequency * RADIUS_MULTIPLIER + (JITTER_AMOUNT + TRIANGLE_CIRCUMRADIUS);
        double maxContributingDistanceSq = maxContributingDistance * maxContributingDistance;
        double latticeSearchRadius = maxContributingDistance / TRIANGLE_HEIGHT;

        ArrayList<LatticePoint> pointsToSearchList = new ArrayList<>();
        pointsToSearchList.add(new LatticePoint(0, 0));
        for (int i = 1; i < latticeSearchRadius; i++) {
            int xsv = i;
            int zsv = 0;

            while (zsv < i) {
                LatticePoint point = new LatticePoint(xsv, zsv);
                if (point.dx * point.dx + point.dz * point.dz < maxContributingDistanceSq)
                    pointsToSearchList.add(point);
                zsv++;
            }

            while (xsv > 0) {
                LatticePoint point = new LatticePoint(xsv, zsv);
                if (point.dx * point.dx + point.dz * point.dz < maxContributingDistanceSq)
                    pointsToSearchList.add(point);
                xsv--;
            }

            while (xsv > -i) {
                LatticePoint point = new LatticePoint(xsv, zsv);
                if (point.dx * point.dx + point.dz * point.dz < maxContributingDistanceSq)
                    pointsToSearchList.add(point);
                xsv--;
                zsv--;
            }

            while (zsv > -i) {
                LatticePoint point = new LatticePoint(xsv, zsv);
                if (point.dx * point.dx + point.dz * point.dz < maxContributingDistanceSq)
                    pointsToSearchList.add(point);
                zsv--;
            }

            while (xsv < 0) {
                LatticePoint point = new LatticePoint(xsv, zsv);
                if (point.dx * point.dx + point.dz * point.dz < maxContributingDistanceSq)
                    pointsToSearchList.add(point);
                xsv++;
            }

            while (zsv < 0) {
                LatticePoint point = new LatticePoint(xsv, zsv);
                if (point.dx * point.dx + point.dz * point.dz < maxContributingDistanceSq)
                    pointsToSearchList.add(point);
                xsv++;
                zsv++;
            }
        }
        System.out.println(pointsToSearchList.size());

        pointsToSearch = pointsToSearchList.toArray(new LatticePoint[0]);
    }

    public double getNoise(int[] perm256, double x, double z) {
        x *= islandFrequency; z *= islandFrequency;

        double s = (x + z) * 0.366025403784439;
        double xs = x + s, zs = z + s;

        int xsb = (int)xs; if (xs < xsb) xsb -= 1;
        int zsb = (int)zs; if (zs < zsb) zsb -= 1;
        double xsi = xs - xsb, zsi = zs - zsb;

        // Find closest vertex on triangle lattice.
        double p = 2 * xsi - zsi;
        double q = 2 * zsi - xsi;
        double r = xsi + zsi;
        if (r > 1) {
            if (p < 0) {
                zsb += 1;
            } else if (q < 0) {
                xsb += 1;
            } else {
                xsb += 1; zsb += 1;
            }
        } else {
            if (p > 1) {
                xsb += 1;
            } else if (q > 1) {
                zsb += 1;
            }
        }

        // Redo xsi, zsi, and compute xi,yi
        xsi = xs - xsb; zsi = zs - zsb;
        double t = (xsi + zsi) * -0.211324865405187;
        double xi = xsi + t, zi = zsi + t;

        double value = 1 - (x * x + z * z) * mainIslandRadiusRescaledSqInverse;
        if (value < 0) value = 0;
        else value *= value * value;

        // Loop through nearby vertices which might define a contributing island.
        for (int i = 0; i < pointsToSearch.length; i++) {
            LatticePoint point = pointsToSearch[i];

            int xsv = xsb + point.xsv;
            int zsv = zsb + point.zsv;

            int hash = perm256[perm256[xsv & 0xFF] ^ (zsv & 0xFF)];

            int islandSizeHash = REMAINING_HASH_LOOKUP[hash];
            double islandRadiusInverse = islandSizeHash * islandRadiusInverseRangeHashMultiplier + islandMinRadiusRescaledInverse;

            double dx = point.dx + xi + JITTER_VECTORS_128[hash & 0xFE];
            double dz = point.dz + zi + JITTER_VECTORS_128[hash | 0x01];

            if ((dx-x)*(dx-x)+(dz-z)*(dz-z) < outerIslandBufferDistanceSq) continue;

            double falloff = 1 - (dx * dx + dz * dz) * (islandRadiusInverse * islandRadiusInverse);
            if (falloff < 0) falloff = 0; else {
                falloff *= falloff * falloff; // Make it smooth so we can just add everything together.
            }

            value += falloff;
        }

        value = value * OUTPUT_RESCALE + OUTPUT_MIN;
        if (value > OUTPUT_CLAMP) value = OUTPUT_CLAMP; // TODO reconsider hard clamping
        return value;
    }

}