package jpg.k.simplyimprovedterrain.terrain.tieredgen;

import jpg.k.simplyimprovedterrain.util.ConcurrentLinkedHashCache;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class HexPrismTieredChunkGrid implements TieredChunkGrid3D {

    /*
     * The 3D space is divided into hexagonal prism chunks with a finite height and an indefinite horizontal extent.
     * The chunks are assigned tiers (0,1,2), each a dependency on the previous, such that no two connecting neighbors are the same tier.
     *
     * In this scenario, hexagonal chunk topology is preferable to a square layout for four primary reasons:
     * - it reduces the required number of tiers to 3 (N+1) from 4 (2^N),
     * - it reduces the maximum number of chunk neighbor dependencies to 6 (2(2^N) - 2) from 8 (3^N - 1),
     * - it sidesteps the awkward decision of axis generation order, and
     * - any boundary bias in the hosted generative algorithms is yet less discernible.
     */

    /*
     * We will use the skew/unskew from 2D simplex noise to address the base hexagonal layout.
     * Each vertex on the triangular grid corresponds to a regular hexagon which forms its Voronoi cell.
     */
    private static final double SKEW_FACTOR_2D = 0.366025403784439;
    private static final double UNSKEW_FACTOR_2D = -0.211324865405187;

    /*
     * Prior to any frequency rescaling, the side length of one of those hexagons has a certain length.
     * This length defines the distance between chunks in the same generation tier, prior to rescaling.
     */
    private static final double HEX_NEIGHBOR_CENTER_DISTANCE_SQUARED = 2.0 / 3.0;
    private static final double HEX_NEIGHBOR_CENTER_DISTANCE = Math.sqrt(HEX_NEIGHBOR_CENTER_DISTANCE_SQUARED);
    private static final double HEX_SIDE_LENGTH_RATIO_TO_NEIGHBOR_OFFSET_SQUARED = 1.0 / 3.0;
    private static final double HEX_EDGE_LENGTH_SQUARED =
            HEX_NEIGHBOR_CENTER_DISTANCE_SQUARED * HEX_SIDE_LENGTH_RATIO_TO_NEIGHBOR_OFFSET_SQUARED;
    private static final double HEX_EDGE_LENGTH = Math.sqrt(HEX_EDGE_LENGTH_SQUARED);

    private static final double RANDOM_SAMPLING_NATURAL_HEX_EDGE_LENGTH_SQUARED = 2.0;
    private static final double RANDOM_SAMPLING_HEX_RESCALE_SQUARED = HEX_EDGE_LENGTH_SQUARED / RANDOM_SAMPLING_NATURAL_HEX_EDGE_LENGTH_SQUARED;
    private static final double RANDOM_SAMPLING_HEX_RESCALE = Math.sqrt(RANDOM_SAMPLING_HEX_RESCALE_SQUARED);

    private static final int HEX_EDGE_COUNT = 6;

    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Z = 0x5BCC226E9FA0BACBL;
    private static final long HASH_MULTIPLIER = 0x53A3F72DEEC546F5L;

    /*
     * builder / registrar
     */

    public static PluginRegistrarBuilder builder(float edgeLength, float fixedHeight) {
        return new PluginRegistrarBuilder(edgeLength, fixedHeight);
    }

    public static class PluginRegistrarBuilder implements PluginRegistrar {

        private final float edgeLength;
        private final double xzScale;
        private final float fixedHeight;
        private final ChunkShape chunkShape;

        private final List<TieredChunkGrid3D.PluginInternal> registeredPlugins;
        private int chunkDataIndexCurrent;

        private PluginRegistrarBuilder(float edgeLength, float fixedHeight) {
            this.edgeLength = edgeLength;
            this.xzScale = edgeLength / HEX_EDGE_LENGTH;
            this.fixedHeight = fixedHeight;
            this.registeredPlugins = new ArrayList<>();
            this.chunkDataIndexCurrent = 0;
            this.chunkShape = new ChunkShape(xzScale, fixedHeight);
        }

        public HexPrismTieredChunkGrid build() {
            return new HexPrismTieredChunkGrid(
                    xzScale, fixedHeight, chunkDataIndexCurrent, chunkShape,
                    registeredPlugins.toArray(new TieredChunkGrid3D.PluginInternal[registeredPlugins.size()])
            );
        }

        @Override
        public ConvexPolytope3D chunkShape() {
            return chunkShape;
        }

        @Override
        public int registerChunkDataIndices(int count) {
            int index = chunkDataIndexCurrent;
            chunkDataIndexCurrent += count;
            return index;
        }

        @Override
        public int registerPlugin(TieredChunkGrid3D.PluginInternal registeredPlugin) {
            int index = this.registeredPlugins.size();
            this.registeredPlugins.add(new PluginInternal.Unvalidated(registeredPlugin));
            return index;
        }

        @Override
        public float chunkSpacingWithinTier() {
            return edgeLength;
        }

    }

    public void validatePlugin(TieredChunkGrid3D.PluginInternal pluginInternal, int pluginIndex) {
        if (!(registeredPlugins[pluginIndex] instanceof PluginInternal.Unvalidated unvalidated)) {
            throw new PluginInternal.Unvalidated.PluginAlreadyValidatedPluginException();
        }

        registeredPlugins[pluginIndex] = unvalidated.wrapped();
    }

    /*
     * constructor & fields
     */

    private final ConcurrentLinkedHashCache<HexAxialCoordinate, TieredChunk3D> hexPrismChunkCache;
    private final float xzScale;
    private final float xzScaleForRandom;
    private final double xzFrequency;
    private final float fixedHeight;
    private final ChunkShape chunkShape;

    private final int chunkDataEntryCount;
    private final TieredChunkGrid3D.PluginInternal[] registeredPlugins;

    private HexPrismTieredChunkGrid(
            double xzScale, float fixedHeight, int chunkDataEntryCount,
            ChunkShape chunkShape, TieredChunkGrid3D.PluginInternal[] registeredPlugins
        ) {
        this.hexPrismChunkCache = new ConcurrentLinkedHashCache<>(24, 384, 32);

        this.xzScale = (float)xzScale;
        this.xzScaleForRandom = (float)(xzScale * RANDOM_SAMPLING_HEX_RESCALE);
        this.xzFrequency = 1.0 / xzScale;
        this.fixedHeight = fixedHeight;

        this.chunkDataEntryCount = chunkDataEntryCount;
        this.registeredPlugins = registeredPlugins;

        this.chunkShape = chunkShape;
    }

    /*
     * public methods
     */

    @Override
    public void forChunks(
            double x, double y, double z,
            ConvexPolytope3D queryShape, float queryPadding, float rejectionPadding,
            ChunkInfoIterationHandler handler
    ) {

        double xRescaled = x * xzFrequency;
        double zRescaled = z * xzFrequency;

        // Find closest vertex on triangle lattice (=> containing hex) in skewed coordinates.
        double s = (xRescaled + zRescaled) * SKEW_FACTOR_2D;
        double xs = xRescaled + s, zs = zRescaled + s;
        int xsBase = Mth.floor(xs), zsBase = Mth.floor(zs);
        double xsDelta = xs - xsBase, zsDelta = zs - zsBase;
        double p = 2 * xsDelta - zsDelta;
        double q = 2 * zsDelta - xsDelta;
        if (xsDelta + zsDelta > 1) {
            if (p < 0) { zsBase += 1; zsDelta -= 1; }
            else if (q < 0) { xsBase += 1; xsDelta -= 1; }
            else { xsBase += 1; zsBase += 1; xsDelta -= 1; zsDelta -= 1; }
        } else {
            if (p > 1) { xsBase += 1; xsDelta -= 1; }
            else if (q > 1) { zsBase += 1; zsDelta -= 1; }
        }

        HexAxialCoordinate centerChunkKey = new HexAxialCoordinate(xsBase, zsBase);
        float dxCenterChunk, dzCenterChunk;
        {
            double xzDeltaUnskew = (xsDelta + zsDelta) * (float)UNSKEW_FACTOR_2D;
            dxCenterChunk = (float)(xsDelta + xzDeltaUnskew) * xzScale;
            dzCenterChunk = (float)(zsDelta + xzDeltaUnskew) * xzScale;
        }

        if (rejectionPadding == Float.NEGATIVE_INFINITY ||
                !queryShape.contains(dxCenterChunk, 0, dzCenterChunk, rejectionPadding, chunkShape)) {
            boolean continueIteration = handler.handle(centerChunkKey, dxCenterChunk, 0, dzCenterChunk);
            if (!continueIteration) return;
        }

        boolean foundChunksInRangeThisLayer;
        int hexLayerEdgeLengthAndRadius = 1;
        do {
            foundChunksInRangeThisLayer = false;
            int xHexBase = hexLayerEdgeLengthAndRadius;
            int zHexBase = 0;
            for (int hexEdgeIndex = 0; hexEdgeIndex < HEX_EDGE_COUNT; hexEdgeIndex++) {
                for (int hexEdgeProgress = 0; hexEdgeProgress < hexLayerEdgeLengthAndRadius; hexEdgeProgress++) {

                    HexAxialCoordinate chunkKey = new HexAxialCoordinate(centerChunkKey.x() + xHexBase, centerChunkKey.z() + zHexBase);
                    float dxChunk, dzChunk;
                    {
                        float xzDeltaUnskew = (xHexBase + zHexBase) * (float)UNSKEW_FACTOR_2D;
                        dxChunk = dxCenterChunk - (xHexBase + xzDeltaUnskew) * xzScale;
                        dzChunk = dzCenterChunk - (zHexBase + xzDeltaUnskew) * xzScale;
                    }

                    boolean withinTargetRange = queryShape.intersectsAssumingSymmetry(dxChunk, 0, dzChunk, queryPadding, chunkShape);
                    foundChunksInRangeThisLayer |= withinTargetRange;

                    if (rejectionPadding != Float.NEGATIVE_INFINITY &&
                            queryShape.contains(dxCenterChunk, 0, dzCenterChunk, rejectionPadding, chunkShape)) {
                        continue;
                    }

                    if (withinTargetRange) {
                        boolean continueIteration = handler.handle(chunkKey, dxChunk, 0, dzChunk);
                        if (!continueIteration) return;
                    }

                    // Advance along hexagonal edges in simplex-skewed coordinates.
                    switch (hexEdgeIndex) {
                        case 0 -> zHexBase++;
                        case 1 -> xHexBase--;
                        case 2 -> { xHexBase--; zHexBase--; }
                        case 3 -> zHexBase--;
                        case 4 -> xHexBase++;
                        case 5 -> { xHexBase++; zHexBase++; }
                    }
                }
            }

            hexLayerEdgeLengthAndRadius++;
        } while (foundChunksInRangeThisLayer);
    }

    public TieredChunk3D getOrCreate(Object chunkKey) {
        if (!(chunkKey instanceof HexAxialCoordinate coordinate)) throw new IllegalArgumentException("chunkKey must be of type HexAxialCoordinate.");
        return hexPrismChunkCache.computeIfAbsent(coordinate, this::create);
    }

    /*
     * Utility
     */

    private TieredChunk3D create(HexAxialCoordinate hexChunkAxialCoordinate) {
        int tier = hexChunkAxialCoordinate.tier();
        double s = (hexChunkAxialCoordinate.x() + hexChunkAxialCoordinate.z()) * UNSKEW_FACTOR_2D;
        double xChunkTrue = (hexChunkAxialCoordinate.x() + s) * xzScale;
        double zChunkTrue = (hexChunkAxialCoordinate.z() + s) * xzScale;

        // TODO optimization opportunity: this can be parallelized.
        NeighborTieredChunk3D[] neighborChunks = new NeighborTieredChunk3D[tier * 3];
        int neighborChunksIndex = 0;
        for (HexNeighbor neighbor : HexNeighbor.valuesExceptSelf()) {
            if (tier + neighbor.deltaTierNegative < 0) continue;
            TieredChunk3D neighboringChunk = getOrCreate(hexChunkAxialCoordinate.offset(neighbor));
            neighborChunks[neighborChunksIndex++] = new NeighborTieredChunk3D(
                    neighboringChunk.dataEntries(),
                    neighbor.dx * xzScale, 0, neighbor.dz * xzScale,
                    neighbor.ordinal(), tier + neighbor.deltaTierNegative
            );
        }

        Object[] chunkDataEntries = chunkDataEntryCount == 0 ? null : new Object[chunkDataEntryCount];
        for (int i = 0; i < registeredPlugins.length; i++) {
            registeredPlugins[i].populateChunkData(chunkDataEntries, neighborChunks, hash(0, hexChunkAxialCoordinate.x, hexChunkAxialCoordinate.z), tier, chunkUtils);
        }

        return new TieredChunk3D(chunkDataEntries, hexChunkAxialCoordinate, xChunkTrue, 0, zChunkTrue);
    }

    private static boolean isWithinHexagon(float dxFromCenter, float dzFromCenter, int hexagonalDiameter) {
        float skewDelta = (dxFromCenter + dzFromCenter) * (float)SKEW_FACTOR_2D;
        float dxSkewed = dxFromCenter + skewDelta, dzSkewed = dzFromCenter + skewDelta;

        return areSkewedCoordinatesWithinHexagon(dxSkewed, dzSkewed, hexagonalDiameter);
    }

    private static boolean areSkewedCoordinatesWithinHexagon(float dxSkewed, float dzSkewed, int hexagonalDiameter) {
        return  Math.abs(dxSkewed + dzSkewed) <= hexagonalDiameter &&
                Math.abs(2 * dxSkewed - dzSkewed) <= hexagonalDiameter &&
                Math.abs(2 * dzSkewed - dxSkewed) <= hexagonalDiameter;
    }

    private static long hash(long seed, int x, int z) {
        return (seed ^ (x * PRIME_X) ^ (z * PRIME_Z)) * HASH_MULTIPLIER;
    }

    public record HexAxialCoordinate(int x, int z) {
        private int tier() {
            int tier = (x + z) % 3;
            if (tier < 0) tier += 3;
            return tier;
        }
        public HexAxialCoordinate offset(HexNeighbor neighbor) {
            return new HexAxialCoordinate(x + neighbor.dxAxial, z + neighbor.dzAxial);
        }
    }

    private enum HexNeighbor {
        SELF(0, 0, 0),
        FORWARD_X(1, 0, 1),
        FORWARD_Z(0, 1, 1),
        BACKWARD_XZ(-1, -1, 1),
        BACKWARD_X(-1, 0, 2),
        BACKWARD_Z(0, -1, 2),
        FORWARD_XZ(1, 1, 2);

        private static final HexNeighbor[] VALUES = values();

        public final int dxAxial, dzAxial;
        public final int deltaTierPositive, deltaTierNegative;
        public final float dx, dz;

        HexNeighbor(int dxAxial, int dzAxial, int deltaTierPositive) {
            this.dxAxial = dxAxial;
            this.dzAxial = dzAxial;
            this.deltaTierPositive = deltaTierPositive;
            this.deltaTierNegative = (deltaTierPositive - 3) % 3;
            double unskewDelta = (dxAxial + dzAxial) * UNSKEW_FACTOR_2D;
            dx = (float) (dxAxial + unskewDelta);
            dz = (float) (dzAxial + unskewDelta);
        }

        public static HexNeighbor[] valuesExceptSelf() {
            return new HexNeighbor[] { FORWARD_X, FORWARD_Z, BACKWARD_XZ, BACKWARD_X, BACKWARD_Z, FORWARD_XZ };
        }

        public static HexNeighbor valueOf(int ordinal) {
            return VALUES[ordinal];
        }
    }

    private static class ChunkShape extends ConvexPolytope3D {

        private static final double ROOT_HALF = (float) Math.sqrt(0.5);
        private static final double ROOT_THREE_HALVES = (float) Math.sqrt(1.5);

        private static final float[] VERTICES_UNSCALED = {
                (float)( 1 + 1 * UNSKEW_FACTOR_2D), 0, (float)( 0 + 1 * UNSKEW_FACTOR_2D),
                (float)( 1 + 2 * UNSKEW_FACTOR_2D), 0, (float)( 1 + 2 * UNSKEW_FACTOR_2D),
                (float)( 0 + 1 * UNSKEW_FACTOR_2D), 0, (float)( 1 + 1 * UNSKEW_FACTOR_2D),
                (float)( 0 - 1 * UNSKEW_FACTOR_2D), 0, (float)(-1 - 1 * UNSKEW_FACTOR_2D),
                (float)(-1 - 2 * UNSKEW_FACTOR_2D), 0, (float)(-1 - 2 * UNSKEW_FACTOR_2D),
                (float)(-1 - 1 * UNSKEW_FACTOR_2D), 0, (float)( 0 - 1 * UNSKEW_FACTOR_2D),
                (float)( 1 + 1 * UNSKEW_FACTOR_2D), 1, (float)( 0 + 1 * UNSKEW_FACTOR_2D),
                (float)( 1 + 2 * UNSKEW_FACTOR_2D), 1, (float)( 1 + 2 * UNSKEW_FACTOR_2D),
                (float)( 0 + 1 * UNSKEW_FACTOR_2D), 1, (float)( 1 + 1 * UNSKEW_FACTOR_2D),
                (float)( 0 - 1 * UNSKEW_FACTOR_2D), 1, (float)(-1 - 1 * UNSKEW_FACTOR_2D),
                (float)(-1 - 2 * UNSKEW_FACTOR_2D), 1, (float)(-1 - 2 * UNSKEW_FACTOR_2D),
                (float)(-1 - 1 * UNSKEW_FACTOR_2D), 1, (float)( 0 - 1 * UNSKEW_FACTOR_2D)
        };

        private final float fixedHeight;
        private final float hexInnerRadius;

        private ChunkShape(double xzScale, float fixedHeight) {
            super(generateVertices(xzScale, fixedHeight));
            this.fixedHeight = fixedHeight;
            this.hexInnerRadius = (float)(xzScale * (HEX_NEIGHBOR_CENTER_DISTANCE / 2.0));
        }

        private static List<ConvexPolytope3D.Vec3f> generateVertices(double xzScale, float fixedHeight) {
            ConvexPolytope3D.Vec3f[] vertices = new ConvexPolytope3D.Vec3f[VERTICES_UNSCALED.length / 3];
            for (int i = 0; i < vertices.length; i++) {
                vertices[i] = new ConvexPolytope3D.Vec3f(
                        (float)(VERTICES_UNSCALED[i * 3    ] * xzScale),
                        VERTICES_UNSCALED[i * 3 + 1] * fixedHeight,
                        (float)(VERTICES_UNSCALED[i * 3 + 2] * xzScale)
                );
            }
            return List.of(vertices);
        }

        @Override
        @SuppressWarnings("RedundantIfStatement")
        public boolean isPointInRange(float x, float y, float z, float boundaryExtensionDistance) {

            if (-y > boundaryExtensionDistance) return false;
            if (y - fixedHeight > boundaryExtensionDistance) return false;

            float a = (x + z) * (float)ROOT_HALF;
            if (Math.abs(a) > hexInnerRadius + boundaryExtensionDistance) return false;

            float b = x * (float)(UNSKEW_FACTOR_2D * ROOT_THREE_HALVES) + z * (float)((1 + UNSKEW_FACTOR_2D) * ROOT_THREE_HALVES);
            if (Math.abs(b) > hexInnerRadius + boundaryExtensionDistance) return false;

            float c = z * (float)(UNSKEW_FACTOR_2D * ROOT_THREE_HALVES) + x * (float)((1 + UNSKEW_FACTOR_2D) * ROOT_THREE_HALVES);
            if (Math.abs(c) > hexInnerRadius + boundaryExtensionDistance) return false;

            return true;
        }
    }

    private final ChunkUtils chunkUtils = new ChunkUtils();
    private class ChunkUtils implements TieredChunkGrid3D.ChunkUtils {

        @Override
        public ConvexPolytope3D chunkShape() {
            return chunkShape;
        }

        @Override
        public Vector3f randomPointInChunk(RandomSource random, Vector3f destination) {

            // Hexagonal random
            float p = random.nextFloat();
            float q = random.nextFloat();
            if (p + q > 1) {
                p -= 1;
                q -= 1;
            }
            int section = random.nextInt(3);
            if (section == 1) p = -p - q;
            else if (section == 2) q = -p - q;

            float s = (p + q) * (float)SKEW_FACTOR_2D;
            float x = p + s;
            float z = q + s;

            float y = random.nextFloat();

            return destination.set(
                    x * xzScaleForRandom,
                    y * fixedHeight,
                    z * xzScaleForRandom
            );

        }

        @Override
        public int getChunkTierForPoint(float x, float y, float z, int currentTier) {
            if (y < 0 || y > fixedHeight) return TIER_INDICATOR_OUTSIDE_GENERATION_AREA;

            x *= xzFrequency;
            z *= xzFrequency;
            float s = (x + z) * (float)SKEW_FACTOR_2D;
            float xs = x + s, zs = z + s;
            int xsBase = Mth.floor(xs), zsBase = Mth.floor(zs);
            float xsDelta = xs - xsBase, zsDelta = zs - zsBase;

            // Find closest vertex on triangle lattice (=> containing hex)
            float p = 2 * xsDelta - zsDelta;
            float q = 2 * zsDelta - xsDelta;
            if (xsDelta + zsDelta > 1) {
                if (p < 0) zsBase += 1;
                else if (q < 0) xsBase += 1;
                else {
                    xsBase += 1; zsBase += 1;
                }
            } else {
                if (p > 1) xsBase += 1;
                else if (q > 1) zsBase += 1;
            }

            return Math.floorMod(xsBase + zsBase + currentTier, 3);
        }
    }
}
