package jpg.k.simplyimprovedterrain.terrain;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class IrreguLerper {

    // Blending consists of cylindrical weight falloff functions around each terrain sampling point.
    // Upon those a randomly-rotated square crease is added to produce a non-griddy but trilerp-reminiscent effect.
    // These control how that ends up looking in the world.
    private static final double SQUARE_CREASE_WIDTH_RATIO = 0.75; // How much smaller than the circle the rotated square is. Note: Values greater than 1 produce discontinuities.
    private static final double SQUARE_CREASE_WEIGHT_RATIO = 3; // How much higher-weighted the square is compared to the circle, at its peak contribution value.

    // For handling a (jittered) tri/hex grid, used as layers to form the (jittered) domain-rotated BCC grid.
    private static final double SKEW2 = -0.21132486540518713;
    private static final double UNSKEW2 = 0.366025403784439;
    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double SQRTHALF = Math.sqrt(0.5);
    private static final double GRIDSCALE_TRIANGLE_EDGE_LENGTH = SQRT2;
    private static final double GRIDSCALE_TRIANGLE_HEIGHT = Math.sqrt(6.0) / 2.0;
    private static final double GRIDSCALE_INVERSE_TRIANGLE_HEIGHT = 1.0 / GRIDSCALE_TRIANGLE_HEIGHT;
    private static final double GRIDSCALE_TRIANGLE_CIRCUMRADIUS = GRIDSCALE_TRIANGLE_HEIGHT * (2.0 / 3.0);
    private static final double GRIDSCALE_JITTER_AMOUNT = SQRTHALF;
    private static final double GRIDSCALE_MAX_DISTANCE_TO_CLOSEST_POINT_IN_LAYER = GRIDSCALE_JITTER_AMOUNT + GRIDSCALE_TRIANGLE_CIRCUMRADIUS;

    // Spacing between layers
    private static final double GRIDSCALE_LAYER_TRIO_SPACING_Y = Math.sqrt(3.0) / 2.0;
    private static final double GRIDSCALE_LAYER_SPACING_Y = GRIDSCALE_LAYER_TRIO_SPACING_Y / 3.0;
    private static final double GRIDSCALE_LAYER_SPACING_XZ = GRIDSCALE_TRIANGLE_CIRCUMRADIUS * SQRTHALF;
    private static final double GRIDSCALE_LAYER_TRIO_FREQUENCY_Y = 1.0 / GRIDSCALE_LAYER_TRIO_SPACING_Y;
    private static final double GRIDSCALE_LAYER_FREQUENCY_Y = 1.0 / GRIDSCALE_LAYER_SPACING_Y;

    // Radii big enough so there are no gaps
    private static final double RADIUS_PADDING_XZ = 0.5;//0.01; // TODO revert padding and try slope-aware quadratic blending.
    private static final double RADIUS_PADDING_Y = 0.5;//0.01;
    private static final double GRIDSCALE_RADIUS_XZ = GRIDSCALE_TRIANGLE_EDGE_LENGTH / 3.0 + GRIDSCALE_JITTER_AMOUNT + RADIUS_PADDING_XZ;
    private static final double GRIDSCALE_ROTATED_SQUARE_WIDTH_HALF = GRIDSCALE_RADIUS_XZ * SQRTHALF * SQUARE_CREASE_WIDTH_RATIO;
    private static final double GRIDSCALE_RADIUS_Y = GRIDSCALE_LAYER_SPACING_Y / 2.0 + GRIDSCALE_JITTER_AMOUNT + RADIUS_PADDING_Y;
    private static final double LAYER_TRIO_SPACING_SCALE_RADIUS_Y = GRIDSCALE_RADIUS_Y * GRIDSCALE_LAYER_TRIO_FREQUENCY_Y;

    // Primes for hash function
    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Y = 0x598CD327003817B5L;
    private static final long PRIME_Z = 0x5BCC226E9FA0BACBL;
    private static final long HASH_MULTIPLIER = 0x53A3F72DEEC546F5L;

    private final long seed;
    private final int halfChunkWidth, chunkHeight;
    private final double spacingXZ, spacingY, layerTrioSpacingY;
    private final double frequencyXZ, layerSpacingY, layerTrioFrequencyY;
    private final double radiusXZ, radiusY, rotatedSquareWidthHalf, scaledSquareCreaseWeightRatio;
    private final int nGridLayerTrios, maxPointsInRange;
    private final LayerBasePoint[] pointsToSearch;

    public static IrreguLerper Create(long seed, int chunkWidth, int chunkHeight, double spacingXZ, double spacingY) {
        return new IrreguLerper(seed, chunkWidth, chunkHeight, spacingXZ, spacingY);
    }

    private IrreguLerper(long seed, int chunkWidth, int chunkHeight, double spacingXZ, double spacingY) {
        this.seed = seed;
        this.halfChunkWidth = (chunkWidth + 1) >> 1;
        this.chunkHeight = chunkHeight;
        this.spacingXZ = spacingXZ;
        this.spacingY = spacingY;
        this.layerSpacingY = spacingY * GRIDSCALE_LAYER_SPACING_Y;
        this.layerTrioSpacingY = spacingY * GRIDSCALE_LAYER_TRIO_SPACING_Y;
        this.frequencyXZ = 1.0 / spacingXZ;
        this.layerTrioFrequencyY = 1.0 / layerTrioSpacingY;
        this.radiusXZ = spacingXZ * GRIDSCALE_RADIUS_XZ;
        this.radiusY = spacingY * GRIDSCALE_RADIUS_Y;
        this.rotatedSquareWidthHalf = spacingXZ * GRIDSCALE_ROTATED_SQUARE_WIDTH_HALF;
        this.scaledSquareCreaseWeightRatio = (radiusXZ * radiusXZ) * (radiusXZ * radiusXZ) * SQUARE_CREASE_WEIGHT_RATIO / rotatedSquareWidthHalf;

        // How many layers could possibly be in range of our chunk?
        double gridFootprintY = layerTrioFrequencyY * chunkHeight + (2 * LAYER_TRIO_SPACING_SCALE_RADIUS_Y);
        this.nGridLayerTrios = (int)Math.ceil(gridFootprintY);

        // Radius of circle that circumscribes the chunk.
        double chunkRadiusXZ = halfChunkWidth * SQRT2;

        // If LayerBasePoint(0, 0) is the closest layer point to the horizontal chunk center,
        // this upper-bounds how far out a point can be from that and still possibly contribute.
        // i.e. if a point is jittered towards the center by JITTER_AMOUNT,
        // is 45 degrees away from the chunk such that the full chunkRadiusXZ applies, and
        // has its square falloff rotation aligned so that GRIDSCALE_RADIUS_XZ reaches into range.
        double maxLayerBasePointContributingDistanceToChunk = (GRIDSCALE_RADIUS_XZ + GRIDSCALE_JITTER_AMOUNT + GRIDSCALE_MAX_DISTANCE_TO_CLOSEST_POINT_IN_LAYER) * spacingXZ;
        double maxLayerBasePointContributingDistanceToCenter = maxLayerBasePointContributingDistanceToChunk + chunkRadiusXZ;
        double maxLayerBasePointContributingDistanceToChunkSq = maxLayerBasePointContributingDistanceToChunk * maxLayerBasePointContributingDistanceToChunk;
        int layerSearchRadius = (int)Math.ceil(maxLayerBasePointContributingDistanceToCenter * frequencyXZ * GRIDSCALE_INVERSE_TRIANGLE_HEIGHT);

        // Generate lookup table to find points in range on each tri/hex grid layer.
        ArrayList<LayerBasePoint> pointsToSearchList = new ArrayList<>();
        pointsToSearchList.add(LayerBasePoint.Create(0, 0, spacingXZ));
        for (int i = 1; i < layerSearchRadius; i++) {
            int xsv = i;
            int zsv = 0;

            while (zsv < i) {
                LayerBasePoint point = LayerBasePoint.Create(xsv, zsv, spacingXZ);
                //if (point.wxv * point.wxv + point.wzv * point.wzv < maxLayerContributingDistanceSq)
                if (circleIntersectsSquare(point.wxv, point.wzv, halfChunkWidth, maxLayerBasePointContributingDistanceToChunk, maxLayerBasePointContributingDistanceToChunkSq))
                    pointsToSearchList.add(point);
                zsv++;
                xsv--;
            }

            while (xsv > -i) {
                LayerBasePoint point = LayerBasePoint.Create(xsv, zsv, spacingXZ);
                if (circleIntersectsSquare(point.wxv, point.wzv, halfChunkWidth, maxLayerBasePointContributingDistanceToChunk, maxLayerBasePointContributingDistanceToChunkSq))
                    pointsToSearchList.add(point);
                xsv--;
            }

            while (zsv > 0) {
                LayerBasePoint point = LayerBasePoint.Create(xsv, zsv, spacingXZ);
                if (circleIntersectsSquare(point.wxv, point.wzv, halfChunkWidth, maxLayerBasePointContributingDistanceToChunk, maxLayerBasePointContributingDistanceToChunkSq))
                    pointsToSearchList.add(point);
                zsv--;
            }

            while (xsv < 0) {
                LayerBasePoint point = LayerBasePoint.Create(xsv, zsv, spacingXZ);
                if (circleIntersectsSquare(point.wxv, point.wzv, halfChunkWidth, maxLayerBasePointContributingDistanceToChunk, maxLayerBasePointContributingDistanceToChunkSq))
                    pointsToSearchList.add(point);
                zsv--;
                xsv++;
            }

            while (xsv < i) {
                LayerBasePoint point = LayerBasePoint.Create(xsv, zsv, spacingXZ);
                if (circleIntersectsSquare(point.wxv, point.wzv, halfChunkWidth, maxLayerBasePointContributingDistanceToChunk, maxLayerBasePointContributingDistanceToChunkSq))
                    pointsToSearchList.add(point);
                xsv++;
            }

            while (zsv < 0) {
                LayerBasePoint point = LayerBasePoint.Create(xsv, zsv, spacingXZ);
                if (circleIntersectsSquare(point.wxv, point.wzv, halfChunkWidth, maxLayerBasePointContributingDistanceToChunk, maxLayerBasePointContributingDistanceToChunkSq))
                    pointsToSearchList.add(point);
                zsv++;
            }
        }

        pointsToSearch = pointsToSearchList.toArray(new LayerBasePoint[0]);
        this.maxPointsInRange = pointsToSearch.length * nGridLayerTrios * 3;
    }

    public ChunkColumnSampler chunkColumnSampler() {
        return new ChunkColumnSampler();
    }

    public class ChunkColumnSampler {
        private boolean running;

        private int localX, localZ, lastY;
        private double xPlusRadius, xMinusRadius;
        private double zPlusRadius, zMinusRadius;

        private int nInterpolatedValues;
        private int lowerSortedPointsStartIndex, lowerSortedPointsEndIndex, upperSortedPointsEndIndex;
        private int nSortedDatapoints;
        private DatapointDataArray sortedDatapointData;
        private double[] currentValuesAndSlopes;
        private double[] currentNormalizedValues;
        private final double[] datapointDeltaContributions;
        private final LayerBasePoint[] layerBasePoints;
        private final Registrar registrar;
        private DensityFunction.FunctionContext masterFunctionContext;

        // For cellular-wrapped functions.
        private int currentCellularIndex;
        private double currentCellularDeltaWeight;
        private double currentCellularDatapointY;
        private int nextKnownCellularIndex;
        private double nextKnownCellularDeltaWeight;
        private double nextKnownCellularDatapointY;
        private double yUpdateCellularIndex;

        public ChunkColumnSampler() {
            this.datapointDeltaContributions = new double[maxPointsInRange];
            this.layerBasePoints = new LayerBasePoint[3];
            this.registrar = new Registrar();
        }

        public Registrar beginRegistration() {
            registrar.clear();
            return registrar;
        }

        public class Registrar {
            private final List<DensityFunction> interpolated3DFunctions;
            private final List<DensityFunction> cellular3DFunctions;

            private Registrar() {
                this.interpolated3DFunctions = new ArrayList<DensityFunction>();
                this.cellular3DFunctions = new ArrayList<DensityFunction>();
            }

            private void clear() {
                this.interpolated3DFunctions.clear();
                this.cellular3DFunctions.clear();
            }

            public InterpolatedRegistration registerInterpolated(DensityFunction function) {
                int index = this.interpolated3DFunctions.size();
                this.interpolated3DFunctions.add(function);
                return new InterpolatedRegistration(index, function);
            }

            public CellularRegistration registerCellular(DensityFunction function) {
                int index = this.cellular3DFunctions.size();
                this.cellular3DFunctions.add(function);
                return new CellularRegistration(index, function);
            }

            public void commit(int chunkBlockX, int chunkBlockY, int chunkBlockZ, DensityFunction.FunctionContext masterFunctionContext) {
                ChunkColumnSampler.this.initForChunk(chunkBlockX, chunkBlockY, chunkBlockZ, masterFunctionContext, this.interpolated3DFunctions, this.cellular3DFunctions);
            }
        }

        public class InterpolatedRegistration implements DensityFunction.SimpleFunction {
            private final int index;
            private final double min, max;
            private DensityFunction base;

            private InterpolatedRegistration(int index, DensityFunction base) {
                this.index = index;
                this.min = base.minValue();
                this.max = base.maxValue();
                this.base = base;
            }

            @Override
            public double compute(FunctionContext functionContext) {
                if (functionContext != ChunkColumnSampler.this.masterFunctionContext)
                    return base.compute(functionContext);
                else
                    return ChunkColumnSampler.this.getCurrentInterpolatedValue(this.index);
            }

            @Override
            public double minValue() {
                return this.min;
            }

            @Override
            public double maxValue() {
                return this.max;
            }

            @Override
            public Codec<? extends DensityFunction> codec() {
                return null;
            }
        }

        public class CellularRegistration implements DensityFunction.SimpleFunction {
            private final int index;
            private final double min, max;
            private DensityFunction base;

            private CellularRegistration(int index, DensityFunction base) {
                this.index = index;
                this.min = base.minValue();
                this.max = base.maxValue();
                this.base = base;
            }

            @Override
            public double compute(FunctionContext functionContext) {
                if (functionContext != ChunkColumnSampler.this.masterFunctionContext)
                    return base.compute(functionContext);
                else
                    return ChunkColumnSampler.this.getCurrentCellularValue(this.index);
            }

            @Override
            public double minValue() {
                return this.min;
            }

            @Override
            public double maxValue() {
                return this.max;
            }

            @Override
            public Codec<? extends DensityFunction> codec() {
                return null;
            }
        }

        public void initForChunk(int chunkBlockX, int chunkBlockY, int chunkBlockZ, DensityFunction.FunctionContext masterFunctionContext, List<DensityFunction> interpolated3DFunctions, List<DensityFunction> cellular3DFunctions) {
            this.masterFunctionContext = masterFunctionContext;
            this.running = true;

            // Define the horizontal base points for the three alternating layer offsets.
            {
                int chunkCenterX = chunkBlockX + halfChunkWidth;
                int chunkCenterZ = chunkBlockZ + halfChunkWidth;
                double gridscaleChunkCenterX = chunkCenterX * frequencyXZ;
                double gridscaleChunkCenterZ = chunkCenterZ * frequencyXZ;
                double gridscaleChunkCenterSkew = (gridscaleChunkCenterX + gridscaleChunkCenterZ) * SKEW2;
                double xs = gridscaleChunkCenterX + gridscaleChunkCenterSkew;
                double zs = gridscaleChunkCenterZ + gridscaleChunkCenterSkew;
                double xzOffset = 0;
                for (int i = 0; i < 3; i++) {

                    // Base vertex of skewed square.
                    int xsb = fastFloor(xs), zsb = fastFloor(zs);
                    double xsi = xs - xsb, zsi = zs - zsb;
                    double t = (xsb + zsb) * UNSKEW2 + xzOffset;
                    double xv = xsb + t, zv = zsb + t;

                    // Find closest vertex on triangle grid layer
                    double p = 2 * xsi + zsi;
                    double q = 2 * zsi + xsi;
                    if (zsi > xsi) {
                        if (p > 2) {
                            xsb++;
                            zsb++;
                            xv += 1 + 2*UNSKEW2;
                            zv += 1 + 2*UNSKEW2;
                        } else if (q > 1) {
                            zsb++;
                            xv += 0 + UNSKEW2;
                            zv += 1 + UNSKEW2;
                        }
                    } else {
                        if (q > 2) {
                            xsb++;
                            zsb++;
                            xv += 1 + 2*UNSKEW2;
                            zv += 1 + 2*UNSKEW2;
                        } else if (p > 1) {
                            xsb++;
                            xv += 1 + UNSKEW2;
                            zv += 0 + UNSKEW2;
                        }
                    }

                    // Set this as the point that all points in this layer are relative to.
                    // And make this itself relative to the chunk coordinates.
                    layerBasePoints[i] = LayerBasePoint.Create(xsb * PRIME_X, zsb * PRIME_Z,
                            xv * spacingXZ - chunkBlockX, zv * spacingXZ - chunkBlockZ);

                    // Offsets to skewed input and unskewed output for next grid layer.
                    xs -= 1.0 / 3.0;
                    zs -= 1.0 / 3.0;
                    xzOffset += GRIDSCALE_LAYER_SPACING_XZ;
                }
            }

            // Vertical range of the grid, in layers, possibly in range of any point in the chunk.
            // Accounts for radius and jitter.
            int layerTrioBase = fastFloor((chunkBlockY * layerTrioFrequencyY) - LAYER_TRIO_SPACING_SCALE_RADIUS_Y);
            int layerTrioEnd = layerTrioBase + nGridLayerTrios;

            // For range checking purposes.
            double radiusXZSq = radiusXZ * radiusXZ;
            double minDatapointY = -radiusY;
            double maxDatapointY = chunkHeight + radiusY;
            double layerVerticalReach = (GRIDSCALE_RADIUS_Y + GRIDSCALE_JITTER_AMOUNT) * spacingY;
            double minLayerY = -layerVerticalReach;
            double maxLayerY = chunkHeight + layerVerticalReach;

            // Start at first subLayer that may possibly have points in range.
            // Start at the bottom of the trio and skip up to two layers.
            double gyWorldOffset = layerTrioBase * layerTrioSpacingY - chunkBlockY;
            long gyPrime = layerTrioBase * (3 * PRIME_Y);
            int subLayer = 0;
            for (int i = 0; i < 2; i++) {
                if (gyWorldOffset > minLayerY) break;
                gyWorldOffset += layerSpacingY;
                gyPrime += PRIME_Y;
                subLayer++;
            }

            // Go over each layer that may have points in range.
            this.nSortedDatapoints = 0;
            this.nInterpolatedValues = interpolated3DFunctions.size();
            int nCellularValues = cellular3DFunctions.size();
            int nTotalValues = nInterpolatedValues + nCellularValues;
            this.sortedDatapointData = DatapointDataArray.create(maxPointsInRange, nTotalValues); // TOOD only reinit if diff # total values? But we might not do the threadlocal thing anyway so...
            this.currentValuesAndSlopes = new double[2 * nInterpolatedValues + 2];
            this.currentNormalizedValues = new double[nInterpolatedValues];
            for (int layerTrio = layerTrioBase;;) {
                LayerBasePoint pointOffset = layerBasePoints[subLayer];

                for (LayerBasePoint unoffsetPoint : pointsToSearch) {
                    long gxPrime = unoffsetPoint.xsvp + pointOffset.xsvp;
                    long gzPrime = unoffsetPoint.zsvp + pointOffset.zsvp;
                    double gxWorldOffset = unoffsetPoint.wxv + pointOffset.wxv;
                    double gzWorldOffset = unoffsetPoint.wzv + pointOffset.wzv;

                    // Gridpoint jitter and rotation hash.
                    long hash = primeHash(gxPrime, gyPrime, gzPrime);

                    // Rotation, to ensure we get jaggedness that treats different cliff face angles more fairly.
                    int rotationIndex = (int)(hash << 1) & 0xFE;
                    double rx = ROTATION_VECTORS_128[rotationIndex | 0];
                    double rz = ROTATION_VECTORS_128[rotationIndex | 1];

                    // Jitter, to ensure our effects are local rather than global.
                    int jitterIndex = (int)(hash >> 54) & 0x3FC;
                    double jx = JITTER_VECTORS_256[jitterIndex | 0] * spacingXZ;
                    double jy = JITTER_VECTORS_256[jitterIndex | 1] * spacingY;
                    double jz = JITTER_VECTORS_256[jitterIndex | 2] * spacingXZ;

                    // Jittered grid point relative to the chunk start coordinate.
                    double gwojx = gxWorldOffset + jx;
                    double gwojy = gyWorldOffset + jy;
                    double gwojz = gzWorldOffset + jz;

                    // Check if in range of chunk. If so, add it.
                    if (circleIntersectsSquare(gwojx - halfChunkWidth, gwojz - halfChunkWidth, halfChunkWidth, radiusXZ, radiusXZSq)
                            && gwojy > minDatapointY && gwojy < maxDatapointY) {

                        // Make space for it in the right place, preserving Y sort order.
                        int j = this.sortedDatapointData.makeSortedSpaceFor(gwojy, nSortedDatapoints);
                        nSortedDatapoints++;

                        // Set most of the parameters.
                        this.sortedDatapointData.setX(j, gwojx);
                        this.sortedDatapointData.setY(j, gwojy);
                        this.sortedDatapointData.setZ(j, gwojz);
                        this.sortedDatapointData.setRX(j, rx);
                        this.sortedDatapointData.setRZ(j, rz);

                        // Proper world coordinate.
                        double worldX = chunkBlockX + gwojx;
                        double worldY = chunkBlockY + gwojy;
                        double worldZ = chunkBlockZ + gwojz;
                        var point = new DensityFunction.SinglePointContext((int)worldX, (int)worldY, (int)worldZ);

                        // Populate the cellular values.
                        // Need these first so the next ones can reference them. (TODO lazyload)
                        this.currentCellularIndex = j;
                        int valueIndex = this.nInterpolatedValues;
                        for (DensityFunction function : cellular3DFunctions) {
                            this.sortedDatapointData.setValue(j, valueIndex, function.compute(point));
                            valueIndex++;
                        }

                        // Populate the values to be interpolated.
                        valueIndex = 0;
                        for (DensityFunction function : interpolated3DFunctions) {
                            this.sortedDatapointData.setValue(j, valueIndex, function.compute(point));
                            valueIndex++;
                        }

                    }

                }

                // Advance to the next layer-trio or break.
                gyWorldOffset += layerSpacingY;
                if (gyWorldOffset >= maxLayerY) break;
                gyPrime += PRIME_Y;
                subLayer++;
                if (subLayer < 3) continue;
                subLayer = 0;
                layerTrio++;

                // Serves as a safeguard, so we don't have to worry about floating point inaccuracies
                // enabling any extra loop iterations that cause us to overflow sortedDatapointData.
                if (layerTrio >= layerTrioEnd) break;
            }
        }

        public void setXZ(int x, int z) {

            // Init column position
            this.localX = x;
            this.localZ = z;
            this.lastY = Integer.MIN_VALUE;

            // Offsets to X and Z so we don't have to repeat the calculations.
            this.xPlusRadius = x + radiusXZ;
            this.xMinusRadius = x - radiusXZ;
            this.zPlusRadius = z + radiusXZ;
            this.zMinusRadius = z - radiusXZ;

            // Reset the lerp.
            //for (int i = 0; i < currentValuesAndSlopes.length; i++) currentValuesAndSlopes[i] = 0;
            Arrays.fill(currentValuesAndSlopes, 0);

            // Start the indices to the point definition array at zero.
            this.lowerSortedPointsStartIndex = this.lowerSortedPointsEndIndex = this.upperSortedPointsEndIndex = 0;

            // Set up for immediate initial closest cellular point update
            this.currentCellularIndex = 0;
            this.yUpdateCellularIndex = Double.POSITIVE_INFINITY;
            this.currentCellularDeltaWeight = 0;
            this.currentCellularDatapointY = 0;
            this.nextKnownCellularDeltaWeight = 0;
            this.nextKnownCellularDatapointY = 0;
        }

        public void setY(int y) {
            if (y < this.lastY) throw new UnsupportedOperationException("Can only go upwards in the current implementation!");

            // First, continue along the linear extrapolation of the slope at our previous point.
            // We will make any corrections based on points moving in and out of range just below.
            int deltaY = y - this.lastY;
            for (int i = 0, j = nInterpolatedValues + 1; i <= nInterpolatedValues; i++, j++) {
                currentValuesAndSlopes[i] += currentValuesAndSlopes[j] * deltaY;
            }

            // Offsets to Y so we don't have to repeat the calculations.
            double yPlusRadius = y + radiusY;
            double yMinusRadius = y - radiusY;

            // Advance lowerSortedPointsStartIndex until it points to the first point up
            // where y (block coordinate) could be in range of its top falloff.
            for (; this.lowerSortedPointsStartIndex < nSortedDatapoints; this.lowerSortedPointsStartIndex++) {
                double datapointY = sortedDatapointData.getY(this.lowerSortedPointsStartIndex);

                // If this datapoint's vertical falloff range is completely below the current block, it's time to remove its effect
                if (datapointY <= yMinusRadius) {

                    // If the block position was previously in the top part of the datapoint's falloff range, we need to remove that effect.
                    if (this.lowerSortedPointsStartIndex < this.lowerSortedPointsEndIndex) {

                        // Get datapoint vertical weight delta step based on horizontal falloff.
                        double deltaWeight = this.datapointDeltaContributions[this.lowerSortedPointsStartIndex];

                        // Skip calculations if it's out of horizontal range -- everything will be zero
                        if (deltaWeight <= 0.0) continue;

                        // Cancel out this datapoint's current lerp extrapolation without adding anything else.
                        // Cancel out this datapoint's top falloff rate from the lerp going forward.
                        double falloffAbove = (datapointY - yMinusRadius) * deltaWeight; // 0 to const*deltaWeight down to datapoint.y
                        for (int i = 0; i < nInterpolatedValues; i++) {
                            double value = sortedDatapointData.getValue(this.lowerSortedPointsStartIndex, i);
                            currentValuesAndSlopes[i] -= falloffAbove * value;
                            currentValuesAndSlopes[i + nInterpolatedValues + 1] += deltaWeight * value;
                        }
                        currentValuesAndSlopes[nInterpolatedValues] -= falloffAbove;
                        currentValuesAndSlopes[2 * nInterpolatedValues + 1] += deltaWeight;

                        // If the block position was actually previously in the bottom part of the datapoint's falloff range, we need to remove that effect instead.
                    } else if (this.lowerSortedPointsStartIndex < this.upperSortedPointsEndIndex) {

                        // Get datapoint vertical weight delta step based on horizontal falloff.
                        double deltaWeight = this.datapointDeltaContributions[this.lowerSortedPointsStartIndex];

                        // Skip calculations if it's out of horizontal range -- everything will be zero
                        if (deltaWeight <= 0.0) continue;

                        // Cancel out this datapoint's current lerp extrapolation without adding anything else.
                        // Cancel out this datapoint's bottom falloff rate from the lerp going forward.
                        double falloffBelow = (yPlusRadius - datapointY) * deltaWeight; // 0 to const*deltaWeight up to datapoint.y
                        for (int i = 0; i < nInterpolatedValues; i++) {
                            double value = sortedDatapointData.getValue(this.lowerSortedPointsStartIndex, i);
                            currentValuesAndSlopes[i] -= falloffBelow * value;
                            currentValuesAndSlopes[i + nInterpolatedValues + 1] -= deltaWeight * value;
                        }
                        currentValuesAndSlopes[nInterpolatedValues] -= falloffBelow;
                        currentValuesAndSlopes[2 * nInterpolatedValues + 1] -= deltaWeight;

                    }

                    continue;
                }

                // If we shot past the list end, fix the end index.
                if (this.lowerSortedPointsEndIndex < this.lowerSortedPointsStartIndex)
                    this.lowerSortedPointsEndIndex = this.lowerSortedPointsStartIndex;

                break;
            }

            // Advance lowerSortedPointsEndIndex until it points to the first index after the last point up
            // whose top falloff our new block's y is in range of.
            for (; this.lowerSortedPointsEndIndex < nSortedDatapoints; this.lowerSortedPointsEndIndex++) {
                double datapointY = sortedDatapointData.getY(this.lowerSortedPointsEndIndex);

                // If the data point is low enough that our block is in range of its top falloff,
                // given it's not so low as to be out of range (which is guaranteed by previous loop),
                // it's time to add or change its effect.
                if (datapointY < y) {

                    // If the current block was previously in the bottom part of the datapoint's falloff range,
                    // it's time to replace that effect with that of the top part.
                    if (this.lowerSortedPointsEndIndex < this.upperSortedPointsEndIndex) {

                        // Get datapoint vertical weight delta step based on horizontal falloff.
                        double deltaWeight = this.datapointDeltaContributions[this.lowerSortedPointsEndIndex];

                        // Skip calculations if it's out of horizontal range -- everything will be zero.
                        if (deltaWeight <= 0.0) continue;

                        // For however far we stepped across and past the datapoint's Y coordinate,
                        // replace the value amounts we went up with the same amounts down.
                        // Update the lerp to follow this new trend.
                        deltaWeight *= 2;
                        double twiceFalloffFrom = (datapointY - y) * deltaWeight;
                        for (int i = 0; i < nInterpolatedValues; i++) {
                            double value = sortedDatapointData.getValue(this.lowerSortedPointsEndIndex, i);
                            currentValuesAndSlopes[i] += twiceFalloffFrom * value;
                            currentValuesAndSlopes[i + nInterpolatedValues + 1] -= deltaWeight * value;
                        }
                        currentValuesAndSlopes[nInterpolatedValues] += twiceFalloffFrom;
                        currentValuesAndSlopes[2 * nInterpolatedValues + 1] -= deltaWeight;

                        // Otherwise we're need to only add the top falloff.
                    } else {

                        // Calculate and store datapoint vertical weight delta step based on horizontal falloff.
                        double deltaWeight = this.datapointDeltaContributions[this.lowerSortedPointsEndIndex] = calcPointDeltaForXZ(this.lowerSortedPointsEndIndex);

                        // Skip calculations if it's out of horizontal range -- everything will be zero.
                        if (deltaWeight <= 0.0) continue;

                        // Add in this datapoint's top lerp extrapolation.
                        // Update the lerp to follow this new trend.
                        double falloffAbove = (datapointY - yMinusRadius) * deltaWeight; // 0 to const*deltaWeight down to p.y
                        for (int i = 0; i < nInterpolatedValues; i++) {
                            double value = sortedDatapointData.getValue(this.lowerSortedPointsEndIndex, i);
                            currentValuesAndSlopes[i] += falloffAbove * value;
                            currentValuesAndSlopes[i + nInterpolatedValues + 1] -= deltaWeight * value;
                        }
                        currentValuesAndSlopes[nInterpolatedValues] += falloffAbove;
                        currentValuesAndSlopes[2 * nInterpolatedValues + 1] -= deltaWeight;

                        // Check if the point beats the current for how soon it will become closer, in the current column.
                        double candidateDistanceAboveClosest = datapointY - this.currentCellularDatapointY;
                        double yNumeratorSubtrahend = this.currentCellularDeltaWeight * (IrreguLerper.this.radiusY - candidateDistanceAboveClosest);
                        double yNumeratorMinuend = deltaWeight * IrreguLerper.this.radiusY;
                        if (yNumeratorSubtrahend < yNumeratorMinuend) {
                            double yNumerator = yNumeratorMinuend - yNumeratorSubtrahend;
                            double yDenominator = deltaWeight + this.currentCellularDeltaWeight;
                            boolean shouldReplace = yNumerator > (datapointY - this.yUpdateCellularIndex) * yDenominator;
                            if (!shouldReplace) {
                                double currentNextFalloff = this.nextKnownCellularDeltaWeight * (this.nextKnownCellularDatapointY - yMinusRadius);
                                shouldReplace = falloffAbove > currentNextFalloff;
                            }
                            if (shouldReplace) {
                                this.nextKnownCellularIndex = this.lowerSortedPointsEndIndex;
                                this.nextKnownCellularDeltaWeight = deltaWeight;
                                this.nextKnownCellularDatapointY = datapointY;
                                this.yUpdateCellularIndex = datapointY - yNumerator / yDenominator;
                            }
                        }
                    }

                    continue;
                }

                // If we shot past the end of the next list which this starts, update that to correct.
                if (this.upperSortedPointsEndIndex < this.lowerSortedPointsEndIndex)
                    this.upperSortedPointsEndIndex = this.lowerSortedPointsEndIndex;

                break;
            }

            // Advance upperSortedPointsEndIndex until it points to the first index after the last point up
            // whose bottom falloff y is in range of.
            for (; this.upperSortedPointsEndIndex < nSortedDatapoints; this.upperSortedPointsEndIndex++) {
                double datapointY = sortedDatapointData.getY(this.upperSortedPointsEndIndex);

                // If the datapoint is low enough that our block is in range of its bottom falloff,
                // given it's not so low as to give it the top falloff or be out of range (guaranteed by previous loop),
                // it's time to add the bottom falloff effect.
                if (datapointY < yPlusRadius) {

                    // Calculate and store datapoint vertical weight delta step based on horizontal falloff.
                    double deltaWeight = this.datapointDeltaContributions[this.upperSortedPointsEndIndex] = calcPointDeltaForXZ(this.upperSortedPointsEndIndex);

                    // Skip if it's out of horizontal range.
                    if (deltaWeight <= 0.0) continue;

                    // Add in this datapoint's bottom lerp extrapolation.
                    // Update the lerp to follow this new trend.
                    double falloffBelow = (yPlusRadius - datapointY) * deltaWeight; // 0 to const*deltaWeight up to p.y
                    for (int i = 0; i < nInterpolatedValues; i++) {
                        double value = sortedDatapointData.getValue(this.upperSortedPointsEndIndex, i);
                        currentValuesAndSlopes[i] += falloffBelow * value;
                        currentValuesAndSlopes[i + nInterpolatedValues + 1] += deltaWeight * value;
                    }
                    currentValuesAndSlopes[nInterpolatedValues] += falloffBelow;
                    currentValuesAndSlopes[2 * nInterpolatedValues + 1] += deltaWeight;

                    // Check if the point beats the current for how soon it will become closer, in the current column.
                    double candidateDistanceAboveClosest = datapointY - this.currentCellularDatapointY;
                    double yNumeratorSubtrahend = this.currentCellularDeltaWeight * (IrreguLerper.this.radiusY - candidateDistanceAboveClosest);
                    double yNumeratorMinuend = deltaWeight * IrreguLerper.this.radiusY;
                    if (yNumeratorSubtrahend < yNumeratorMinuend) {
                        double yNumerator = yNumeratorMinuend - yNumeratorSubtrahend;
                        double yDenominator = deltaWeight + this.currentCellularDeltaWeight;
                        boolean shouldReplace = yNumerator > (datapointY - this.yUpdateCellularIndex) * yDenominator;
                        if (!shouldReplace) {
                            double currentNextFalloff = this.nextKnownCellularDeltaWeight * (IrreguLerper.this.radiusY - Math.abs(this.nextKnownCellularDatapointY - y));
                            shouldReplace = falloffBelow > currentNextFalloff;
                        }
                        if (shouldReplace) {
                            this.nextKnownCellularIndex = this.upperSortedPointsEndIndex;
                            this.nextKnownCellularDeltaWeight = deltaWeight;
                            this.nextKnownCellularDatapointY = datapointY;
                            this.yUpdateCellularIndex = datapointY - yNumerator / yDenominator;
                        }
                    }

                    continue;
                }

                break;
            }

            // Populate final blended ("interpolated") values.
            double inverseDenominator = 1.0 / this.currentValuesAndSlopes[nInterpolatedValues];
            for (int i = 0; i < nInterpolatedValues; i++) {
                currentNormalizedValues[i] = this.currentValuesAndSlopes[i] * inverseDenominator;
            }

            // Update the cellular state if it's time.
            if (y > this.yUpdateCellularIndex) {
                this.currentCellularIndex = this.nextKnownCellularIndex;
                this.currentCellularDatapointY = this.nextKnownCellularDatapointY;
                this.currentCellularDeltaWeight = this.nextKnownCellularDeltaWeight;
                this.yUpdateCellularIndex = Double.POSITIVE_INFINITY;

                this.yUpdateCellularIndex = Double.POSITIVE_INFINITY;
                double currentNextFalloff = Double.NEGATIVE_INFINITY;
                for (int i = this.nextKnownCellularIndex + 1; i < this.upperSortedPointsEndIndex; i++) {
                    double datapointY = this.sortedDatapointData.getY(i);
                    double deltaWeight = this.datapointDeltaContributions[i];
                    double candidateDistanceAboveClosest = datapointY - this.currentCellularDatapointY;
                    double yNumeratorSubtrahend = this.currentCellularDeltaWeight * (IrreguLerper.this.radiusY - candidateDistanceAboveClosest);
                    double yNumeratorMinuend = deltaWeight * IrreguLerper.this.radiusY;
                    if (yNumeratorSubtrahend < yNumeratorMinuend) {
                        double yNumerator = yNumeratorMinuend - yNumeratorSubtrahend;
                        double yDenominator = deltaWeight + this.currentCellularDeltaWeight;
                        boolean shouldReplace = yNumerator > (datapointY - this.yUpdateCellularIndex) * yDenominator;
                        if (!shouldReplace) {
                            double thisFalloff = deltaWeight * (IrreguLerper.this.radiusY - Math.abs(datapointY - y));
                            shouldReplace = thisFalloff > currentNextFalloff;
                        }
                        if (shouldReplace) {
                            this.nextKnownCellularIndex = i;
                            this.nextKnownCellularDeltaWeight = deltaWeight;
                            this.nextKnownCellularDatapointY = datapointY;
                            this.yUpdateCellularIndex = datapointY - yNumerator / yDenominator;
                            currentNextFalloff = this.nextKnownCellularDeltaWeight * (IrreguLerper.this.radiusY - Math.abs(this.nextKnownCellularDatapointY - y));
                        }
                    }

                }
            }

            this.lastY = y;
        }

        public void stopRunning() {
            // if (Arrays.asList(Thread.currentThread().getStackTrace()).stream().anyMatch(element -> Lifeguard.class.isAssignableFrom(Class.forName(element.getClassName())))) throw new IllegalCallerException();
            if (!this.running)
                throw new IllegalStateException("Trying to stop irregulerping when the column sample wasn't started.");
            this.running = false;
        }

        public double getCurrentInterpolatedValue(int i) {
            if (!this.running)
                throw new IllegalStateException("Trying to access interpolated value when the column sampler wasn't started.");
            return currentNormalizedValues[i];
        }

        public double getCurrentCellularValue(int i) {
            if (!this.running)
                throw new IllegalStateException("Trying to access cellular value when the column sampler wasn't started.");
            return this.sortedDatapointData.getValue(ChunkColumnSampler.this.currentCellularIndex, i + this.nInterpolatedValues);
        }

        private long primeHash(long gxPrime, long gyPrime, long gzPrime) {
            return (seed ^ gxPrime ^ gyPrime ^ gzPrime) * HASH_MULTIPLIER;
        }

        private double calcPointDeltaForXZ(int i) {

            // If the point is in vertical range, but not horizontal range (square containing circle), we're outside of horizontal range.
            double px = sortedDatapointData.getX(i);
            if (xPlusRadius <= px || xMinusRadius >= px) return 0;
            double pz = sortedDatapointData.getZ(i);
            if (zPlusRadius <= pz || zMinusRadius >= pz) return 0;

            // If the point is in vertical range, but not horizontal range (circle), we're outside of horizontal range.
            double dx = localX - px;
            double dz = localZ - pz;
            if (dx*dx + dz*dz >= radiusXZ * radiusXZ) return 0;

            // The faint round part of the falloff.
            double falloffXZ = radiusXZ * radiusXZ - dx*dx - dz*dz;
            falloffXZ *= falloffXZ;

            // If the point is in vertical range, but not horizontal range (rotated square in that circle), keep looking.
            double prx = sortedDatapointData.getRX(i);
            double prz = sortedDatapointData.getRZ(i);
            double ardx = Math.abs(dx * prx + dz * prz);
            if (ardx >= rotatedSquareWidthHalf) return falloffXZ;
            double ardz = Math.abs(dx * prz - dz * prx);
            if (ardz >= rotatedSquareWidthHalf) return falloffXZ;

            // The strong rotated square crease part of the falloff.
            falloffXZ += (rotatedSquareWidthHalf - Math.max(ardx, ardz)) * scaledSquareCreaseWeightRatio;

            // Full circle + rotated square kernel
            return falloffXZ;
        }

        private static final class DatapointDataArray {
            private static final int DATAPOINT_DATA_ARRAY_INDEX_X = 0;
            private static final int DATAPOINT_DATA_ARRAY_INDEX_Y = 2;
            private static final int DATAPOINT_DATA_ARRAY_INDEX_Z = 1;
            private static final int DATAPOINT_DATA_ARRAY_INDEX_RX = 3;
            private static final int DATAPOINT_DATA_ARRAY_INDEX_RZ = 4;
            private static final int DATAPOINT_DATA_ARRAY_INDEX_VALUES_START = 5;
            private static final int DATAPOINT_DATA_ARRAY_ENTRY_SIZE_BEFORE_VALUES = DATAPOINT_DATA_ARRAY_INDEX_VALUES_START;

            private final double[] array;
            private final int entrySize;

            public static DatapointDataArray create(int length, int nTotalValues) {
                return new DatapointDataArray(length, nTotalValues);
            }

            private DatapointDataArray(int length, int nTotalValues) {
                this.entrySize = DATAPOINT_DATA_ARRAY_ENTRY_SIZE_BEFORE_VALUES + nTotalValues;
                this.array = new double[this.entrySize * length];
            }

            public double getX(int i) {
                return array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_X];
            }

            public void setX(int i, double value) {
                array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_X] = value;
            }

            public double getY(int i) {
                return array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_Y];
            }

            public void setY(int i, double value) {
                array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_Y] = value;
            }

            public double getZ(int i) {
                return array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_Z];
            }

            public void setZ(int i, double value) {
                array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_Z] = value;
            }

            public double getRX(int i) {
                return array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_RX];
            }

            public void setRX(int i, double value) {
                array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_RX] = value;
            }

            public double getRZ(int i) {
                return array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_RZ];
            }

            public void setRZ(int i, double value) {
                array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_RZ] = value;
            }

            public double getValue(int i, int valueIndex) {
                return array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_VALUES_START + valueIndex];
            }

            public void setValue(int i, int valueIndex, double value) {
                array[i * entrySize + DATAPOINT_DATA_ARRAY_INDEX_VALUES_START + valueIndex] = value;
            }

            public int makeSortedSpaceFor(double y, int currentEntryCount) {

                // Find first index whose previous Y value is smaller than the provided Y.
                int i = currentEntryCount;
                while (i > 0 && this.getY(i - 1) > y) i--;

                // Shift-copy everything forward to make the space for the entry where we need it.
                for (int j = currentEntryCount * entrySize - 1; j >= i * entrySize; j--) {
                    array[j + entrySize] = array[j];
                }

                // Return the index to the new entry's slot.
                return i;
            }

        }
    }

    private static boolean circleIntersectsSquare(double dx, double dz, double halfSquareWidth, double radius, double radiusSq) {

        // Completely outside as if both were squares.
        double adxDelta = Math.abs(dx) - halfSquareWidth;
        if (adxDelta > radius) return false;
        double adzDelta = Math.abs(dz) - halfSquareWidth;
        if (adzDelta > radius) return false;

        // Within square on one axis = other axis decides completely.
        if (adxDelta <= 0) return adzDelta < radius;
        if (adzDelta <= 0) return adxDelta < radius;

        // Otherwise the circle determines intersection on a square corner.
        return adxDelta * adxDelta + adzDelta * adzDelta < radiusSq;
    }

    private static int fastFloor(double x) {
        int xi = (int)x;
        return x < xi ? xi - 1 : xi;
    }

    @FunctionalInterface
    public interface DatapointValueCallback {
        double getValue(double x, double y, double z);
    }

    @FunctionalInterface
    public interface DatapointValueConsumer {
        void provide(int index, double value);
    }

    @FunctionalInterface
    public interface DatapointValueProvider {
        void provideValues(double x, double y, double z, DatapointValueConsumer consumer);
    }

    private static final double[] ROTATION_VECTORS_128 = {
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

    private static final double[] JITTER_VECTORS_256 = new double[256 * 4];
    static {
        final double MAGNITUDE = Math.sqrt(2.22474487139*2.22474487139*2 + 1);
        final double ROOT3OVER3 = Math.sqrt(3.0) / 3.0;
        final double ROTATE3_ORTHOGONALIZER = UNSKEW2;
        double[] vectors48for256 = {
                -2.22474487139,       2.22474487139,      -1.0,                 0.0, //
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
                2.22474487139,       2.22474487139,      -1.0,                 0.0,
                2.22474487139,       2.22474487139,       1.0,                 0.0,
                3.0862664687972017,  1.1721513422464978,  0.0,                 0.0,
                1.1721513422464978,  3.0862664687972017,  0.0,                 0.0, //
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
        };
        for (int i = 0; i < vectors48for256.length; i += 4) {
            double gx = vectors48for256[i | 0];
            double gy = vectors48for256[i | 1];
            double gz = vectors48for256[i | 2];

            // Rotate to put the shape in best alignment with the point grid,
            // so we avoid neighboring jitter vectors pointing directly at each other.
            double xz = gx + gz;
            double s2 = xz * ROTATE3_ORTHOGONALIZER;
            double yy = gy * ROOT3OVER3;
            double gxr = gx + s2 + yy;
            double gzr = gz + s2 + yy;
            double gyr = xz * -ROOT3OVER3 + yy;

            // Rescale and store
            vectors48for256[i | 0] = gxr * (GRIDSCALE_JITTER_AMOUNT / MAGNITUDE);
            vectors48for256[i | 1] = gyr * (GRIDSCALE_JITTER_AMOUNT / MAGNITUDE);
            vectors48for256[i | 2] = gzr * (GRIDSCALE_JITTER_AMOUNT / MAGNITUDE);
        }
        for (int i = 0, j = 0; i < JITTER_VECTORS_256.length; i++, j++) {
            if (j == vectors48for256.length) j = 0;
            JITTER_VECTORS_256[i] = vectors48for256[j];
        }
    }

    private static final class LayerBasePoint {
        public final long xsvp, zsvp;
        public final double wxv, wzv;

        public static LayerBasePoint Create(long xsvp, long zsvp, double wxv, double wzv) {
            return new LayerBasePoint(xsvp, zsvp, wxv, wzv);
        }

        public static LayerBasePoint Create(int xsv, int zsv, double spacingXZ) {
            return new LayerBasePoint(xsv, zsv, spacingXZ);
        }

        private LayerBasePoint(long xsvp, long zsvp, double wxv, double wzv) {
            this.xsvp = xsvp;
            this.zsvp = zsvp;
            this.wxv = wxv;
            this.wzv = wzv;
        }

        private LayerBasePoint(int xsv, int zsv, double spacingXZ) {
            this.xsvp = xsv * PRIME_X;
            this.zsvp = zsv * PRIME_Z;
            double t = (xsv + zsv) * UNSKEW2;
            this.wxv = (xsv + t) * spacingXZ;
            this.wzv = (zsv + t) * spacingXZ;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LayerBasePoint that = (LayerBasePoint) o;
            return xsvp == that.xsvp && zsvp == that.zsvp && Double.compare(that.wxv, wxv) == 0 && Double.compare(that.wzv, wzv) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(xsvp, zsvp, wxv, wzv);
        }
    }
}
