package jpg.k.simplyimprovedterrain.terrain;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.math.Vecords.*;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.*;
import java.util.List;

import static jpg.k.simplyimprovedterrain.math.Vecords.Vector3d.distanceSq;

public final class IrreguLerper {

    // These extend the trilerp-emulation falloff function.
    private static final double FALLOFF_WIDTH_RATIO = 1.0;
    private static final double FALLOFF_HEIGHT_RATIO = 1.0;

    // IrreguLerper uses Voronoi patches of randomly-offset and XZ-rotated trilerp grids.
    // These control the sizes of those relative to the trilerp cell sizes.
    private final double PATCH_RELATIVE_SPACING_XZ = 7;
    private final double PATCH_RELATIVE_SPACING_Y = 7;

    // We'll need these.
    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double SQRT3 = Math.sqrt(3.0);

    // Voronoi cell searching.
    private final double PATCH_CELL_SEARCH_RADIUS_TRUE = SQRT3;
    private final int PATCH_CELL_SEARCH_RADIUS = fastCeil(PATCH_CELL_SEARCH_RADIUS_TRUE);
    private final int PATCH_CELL_SEARCH_DIAMETER = PATCH_CELL_SEARCH_RADIUS * 2 + 1;
    private final int PATCH_CELL_SEARCH_VOLUME = PATCH_CELL_SEARCH_DIAMETER * PATCH_CELL_SEARCH_DIAMETER * PATCH_CELL_SEARCH_DIAMETER;
    private final int N_CHUNK_SIDES = 6;

    // Primes for Voronoi hash.
    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Y = 0x598CD327003817B5L;
    private static final long PRIME_Z = 0x5BCC226E9FA0BACBL;
    private static final long HASH_MULTIPLIER = 0x53A3F72DEEC546F5L;

    // Set before chunk initialization.
    private final long seed;
    private final int chunkWidth, chunkHeight;
    private final double spacingXZ, spacingY;
    private final double radiusXZ, radiusY;
    private final double rotatedSquareWidthHalfScaled;
    private final Registrar registrar;

    // Initialized for a chunk.
    private int nSortedDatapoints;
    private int nInterpolatedValues;
    private DatapointDataArray sortedDatapointData;
    private DensityFunction.FunctionContext masterFunctionContext; // NoiseChunk, generally.

    // Updated during interpolation.
    private boolean running;
    private int localX, localZ, lastY;
    private double xPlusRadius, xMinusRadius;
    private double zPlusRadius, zMinusRadius;
    private int lowerSortedPointsStartIndex, lowerSortedPointsEndIndex, upperSortedPointsEndIndex;
    private double[] currentValuesAndSlopes;
    private double[] currentNormalizedValues;
    private double[] datapointDeltaContributions;

    // For cellular-wrapped functions ("CacheOnce").
    private int currentCellularIndex;
    private double currentCellularDeltaWeight;
    private double currentCellularDatapointY;
    private int nextKnownCellularIndex;
    private double nextKnownCellularDeltaWeight;
    private double nextKnownCellularDatapointY;
    private double yUpdateCellularIndex;

    record GridPatchCell(Vector2d scaledRotation, Vector3d jitter) { }
    record PatchBoundaryInfo(Vector3d floodFillBoundaryPlaneVector, double floodFillBoundaryPlaneMaxValueAtBoundary, int opposingPatchIndex, double distanceSquaredToOpposingPoint) { }
    record PointToSample(Vector3d pointInChunk, Vector2d scaledRotation) { }

    public IrreguLerper(long seed, int chunkWidth, int chunkHeight, double spacingXZ, double spacingY) {
        this.seed = seed;
        this.chunkWidth = chunkWidth;
        this.chunkHeight = chunkHeight;
        this.spacingXZ = spacingXZ;
        this.spacingY = spacingY;
        this.radiusXZ = spacingXZ * (SQRT2 * FALLOFF_WIDTH_RATIO);
        this.radiusY = spacingY * FALLOFF_HEIGHT_RATIO;
        this.rotatedSquareWidthHalfScaled = spacingXZ * spacingXZ * FALLOFF_WIDTH_RATIO;
        this.registrar = new Registrar();
    }

    public void initForChunk(int chunkBlockX, int chunkBlockY, int chunkBlockZ, DensityFunction.FunctionContext masterFunctionContext, List<DensityFunction> interpolated3DFunctions, List<DensityFunction> cellular3DFunctions) {
        this.masterFunctionContext = masterFunctionContext;
        this.running = true;

        final double frequencyY = 1.0 / spacingY;
        final double patchSpacingXZ = spacingXZ * PATCH_RELATIVE_SPACING_XZ;
        final double patchSpacingY = spacingY * PATCH_RELATIVE_SPACING_Y;
        final double patchFrequencyXZ = 1.0 / patchSpacingXZ;
        final double patchFrequencyY = 1.0 / patchSpacingY;
        final Vector3d patchFrequency = new Vector3d(patchFrequencyXZ, patchFrequencyY, patchFrequencyXZ);
        final Vector3d patchSpacing = new Vector3d(patchSpacingXZ, patchSpacingY, patchSpacingXZ);
        this.nInterpolatedValues = interpolated3DFunctions.size();
        int nCellularValues = cellular3DFunctions.size();
        int nTotalValues = nInterpolatedValues + nCellularValues;
        this.currentValuesAndSlopes = new double[2 * nInterpolatedValues + 2];
        this.currentNormalizedValues = new double[nInterpolatedValues];
        List<PointToSample> pointsToSample = new ArrayList<>();

        // Voronoi patch cell search range.
        // Doubled-radii account for "Bleed one grid step into the neighboring cells." modification further down.
        int patchCellStartX = fastFloor((chunkBlockX - 2*radiusXZ) * patchFrequencyXZ - PATCH_CELL_SEARCH_RADIUS_TRUE);
        int patchCellStartY = fastFloor((chunkBlockY - 2*radiusY)  * patchFrequencyY  - PATCH_CELL_SEARCH_RADIUS_TRUE);
        int patchCellStartZ = fastFloor((chunkBlockZ - 2*radiusXZ) * patchFrequencyXZ - PATCH_CELL_SEARCH_RADIUS_TRUE);
        int patchCellEndX = fastFloor((chunkBlockX + (2*radiusXZ + chunkWidth)) * patchFrequencyXZ + PATCH_CELL_SEARCH_RADIUS_TRUE);
        int patchCellEndY = fastFloor((chunkBlockY + (2*radiusY + chunkHeight)) * patchFrequencyY  + PATCH_CELL_SEARCH_RADIUS_TRUE);
        int patchCellEndZ = fastFloor((chunkBlockZ + (2*radiusXZ + chunkWidth)) * patchFrequencyXZ + PATCH_CELL_SEARCH_RADIUS_TRUE);
        int patchCellCountX = patchCellEndX - patchCellStartX + 1;
        int patchCellCountY = patchCellEndY - patchCellStartY + 1;
        int patchCellCountZ = patchCellEndZ - patchCellStartZ + 1;
        int patchCellCount = patchCellCountX * patchCellCountY * patchCellCountZ;

        GridPatchCell[] gridPatchCells = new GridPatchCell[patchCellCount];
        PatchBoundaryInfo[] gridPatchCellBoundaryInfo = new PatchBoundaryInfo[PATCH_CELL_SEARCH_VOLUME + N_CHUNK_SIDES];

        // Populate all the seeded data for the Patch Voronoi cells.
        for (int i = 0, patchCellY = patchCellStartY, patchCellZ = patchCellStartZ, patchCellX = patchCellStartX; i < patchCellCount; i++) {

            // Jitter and rotation hash.
            long hash = primeHash(patchCellX * PRIME_X, patchCellY * PRIME_Y, patchCellZ * PRIME_Z);

            // Rotation, to ensure we get jaggedness that treats different cliff face angles more fairly over the space of the world.
            int rotationIndex = (int)(hash << 1) & 0xFE;
            double rx = ROTATION_VECTORS_128[rotationIndex | 0] * spacingXZ;
            double rz = ROTATION_VECTORS_128[rotationIndex | 1] * spacingXZ;

            // Jitter, to ensure grid patch transitions aren't aligned to a visible structure.
            long jitterIndexBits = (hash >> 7) | (hash >> 10);
            int jitterRXi = (int)((jitterIndexBits      ) & 0x3FFFF);
            int jitterRYi = (int)((jitterIndexBits >> 18) & 0x3FFFF);
            int jitterRZi = (int)((jitterIndexBits >> 36) & 0x3FFFF);
            double jx = (1.0 / 0x40000) * jitterRXi;
            double jy = (1.0 / 0x40000) * jitterRYi;
            double jz = (1.0 / 0x40000) * jitterRZi;

            gridPatchCells[i] = new GridPatchCell(new Vector2d(rx, rz), new Vector3d(jx, jy, jz));

            patchCellY++;
            if (patchCellY <= patchCellEndY) continue;
            patchCellY = patchCellStartY;
            patchCellZ++;
            if (patchCellZ <= patchCellEndZ) continue;
            patchCellZ = patchCellStartZ;
            patchCellX++;
            if (patchCellX > patchCellEndX) break;
        }

        // Populate the points contributed by each patch cell.
        for (int i = 0, patchCellY = patchCellStartY, patchCellZ = patchCellStartZ, patchCellX = patchCellStartX; i < patchCellCount; i++) {
            GridPatchCell patchCell = gridPatchCells[i];

            // Search patch cells around, to define boundaries.
            int gridPatchCellBoundaryInfoCount = 0;
            int opposingPatchIndex = i - (1 + patchCellCountY + patchCellCountY * patchCellCountZ) * PATCH_CELL_SEARCH_RADIUS;
            for (int j = 0, surroundingPatchCellDY = -PATCH_CELL_SEARCH_RADIUS,
                            surroundingPatchCellDZ = -PATCH_CELL_SEARCH_RADIUS,
                            surroundingPatchCellDX = -PATCH_CELL_SEARCH_RADIUS; j < PATCH_CELL_SEARCH_VOLUME; j++) {

                /*double indexOffset = surroundingPatchCellDY + surroundingPatchCellDZ * patchCellCountY + surroundingPatchCellDX * (patchCellCountY * patchCellCountZ);
                if (opposingPatchIndex != i + indexOffset) throw new RuntimeException();*/

                boolean isOutOfRange = false;
                if (!isOutOfRange) isOutOfRange = (patchCellX + surroundingPatchCellDX < patchCellStartX);
                if (!isOutOfRange) isOutOfRange = (patchCellX + surroundingPatchCellDX > patchCellEndX);
                if (!isOutOfRange) isOutOfRange = (patchCellY + surroundingPatchCellDY < patchCellStartY);
                if (!isOutOfRange) isOutOfRange = (patchCellY + surroundingPatchCellDY > patchCellEndY);
                if (!isOutOfRange) isOutOfRange = (patchCellZ + surroundingPatchCellDZ < patchCellStartZ);
                if (!isOutOfRange) isOutOfRange = (patchCellZ + surroundingPatchCellDZ > patchCellEndZ);
                if (!isOutOfRange && opposingPatchIndex != i) AddBoundary: {

                    GridPatchCell surroundingPatchCell = gridPatchCells[opposingPatchIndex];

                    // in grid space
                    Vector3d surroundingPatchCellBaseOffsetToCurrentPatchCellBase = new Vector3d(surroundingPatchCellDX, surroundingPatchCellDY, surroundingPatchCellDZ);
                    Vector3d surroundingPatchCoordRelativeToCurrentPatchCellBase = Vector3d.add(surroundingPatchCellBaseOffsetToCurrentPatchCellBase, surroundingPatchCell.jitter);

                    // Cull some points that can't possibly be the closest.
                    // TODO this could be improved, but this is better than nothing for now.
                    Vector3d closestPointInCurrentCube = Vector3d.clamp(surroundingPatchCoordRelativeToCurrentPatchCellBase, 0, 1);
                    if (distanceSq(surroundingPatchCoordRelativeToCurrentPatchCellBase, closestPointInCurrentCube) > SQRT3+0.0001)
                        break AddBoundary;

                    // Anyway...
                    Vector3d boundaryDeltaUncorrected = Vector3d.subtract(surroundingPatchCoordRelativeToCurrentPatchCellBase, patchCell.jitter);

                    // Adjust for grid scale (patchSpacing)
                    Vector3d boundaryDelta = Vector3d.multiply(boundaryDeltaUncorrected, patchFrequency);
                    Vector3d boundaryDeltaWorld = Vector3d.multiply(boundaryDeltaUncorrected, patchSpacing);
                    double distanceSquared = Vector3d.dot(boundaryDeltaWorld, boundaryDeltaWorld);
                    double dotValueAtBoundary = Vector3d.dot(boundaryDeltaWorld, boundaryDelta) * 0.5;

                    /*// Allow one block further along each axis, to address certain edge cases.
                    dotValueAtBoundary += Math.abs(boundaryDelta.x()) + Math.abs(boundaryDelta.y()) + Math.abs(boundaryDelta.z());*/

                    // Bleed one grid step into the neighboring cells.
                    double boundaryDeltaExtensionU = Math.abs(patchCell.scaledRotation.x() * boundaryDelta.x() + patchCell.scaledRotation.z() * boundaryDelta.z());
                    double boundaryDeltaExtensionV = Math.abs(patchCell.scaledRotation.z() * boundaryDelta.x() - patchCell.scaledRotation.x() * boundaryDelta.z());
                    double boundaryDeltaExtensionY = Math.abs(spacingY * boundaryDelta.y());
                    dotValueAtBoundary += Math.max(Math.max(boundaryDeltaExtensionU, boundaryDeltaExtensionV), boundaryDeltaExtensionY);

                    gridPatchCellBoundaryInfo[gridPatchCellBoundaryInfoCount++] = new PatchBoundaryInfo(
                        boundaryDelta, dotValueAtBoundary, opposingPatchIndex, distanceSquared
                    );

                }

                surroundingPatchCellDY++;
                opposingPatchIndex++;
                if (surroundingPatchCellDY <= PATCH_CELL_SEARCH_RADIUS) continue;
                surroundingPatchCellDY = -PATCH_CELL_SEARCH_RADIUS;
                opposingPatchIndex += patchCellCountY - PATCH_CELL_SEARCH_DIAMETER;
                surroundingPatchCellDZ++;
                if (surroundingPatchCellDZ <= PATCH_CELL_SEARCH_RADIUS) continue;
                surroundingPatchCellDZ = -PATCH_CELL_SEARCH_RADIUS;
                opposingPatchIndex += patchCellCountY * patchCellCountZ - PATCH_CELL_SEARCH_DIAMETER * patchCellCountY;
                surroundingPatchCellDX++;
                if (surroundingPatchCellDX > PATCH_CELL_SEARCH_RADIUS) break;
            }

            // Everything in the queue traversal below will be relative to this.
            Vector3d patchOriginChunkRelativeCoord = new Vector3d(
                    (patchCellX + patchCell.jitter.x()) * patchSpacingXZ - chunkBlockX,
                    (patchCellY + patchCell.jitter.y()) * patchSpacingY  - chunkBlockY,
                    (patchCellZ + patchCell.jitter.z()) * patchSpacingXZ - chunkBlockZ
            );

            // Chunk contribution boundaries
            {
                double fromBoundary = patchOriginChunkRelativeCoord.x() + radiusXZ;
                double valueAtBoundary = fromBoundary;
                gridPatchCellBoundaryInfo[gridPatchCellBoundaryInfoCount++] = new PatchBoundaryInfo(
                        new Vector3d(-1, 0, 0), valueAtBoundary,
                        -1, fromBoundary * fromBoundary * 4
                );
            }
            {
                double fromBoundary = (chunkWidth + radiusXZ) - patchOriginChunkRelativeCoord.x();
                double valueAtBoundary = fromBoundary;
                gridPatchCellBoundaryInfo[gridPatchCellBoundaryInfoCount++] = new PatchBoundaryInfo(
                        new Vector3d(1, 0, 0), valueAtBoundary,
                        -1, fromBoundary * fromBoundary * 4
                );
            }
            {
                double fromBoundary = patchOriginChunkRelativeCoord.z() + radiusXZ;
                double valueAtBoundary = fromBoundary;
                gridPatchCellBoundaryInfo[gridPatchCellBoundaryInfoCount++] = new PatchBoundaryInfo(
                        new Vector3d(0, 0, -1), valueAtBoundary,
                        -1, fromBoundary * fromBoundary * 4
                );
            }
            {
                double fromBoundary = (chunkWidth + radiusXZ) - patchOriginChunkRelativeCoord.z();
                double valueAtBoundary = fromBoundary;
                gridPatchCellBoundaryInfo[gridPatchCellBoundaryInfoCount++] = new PatchBoundaryInfo(
                        new Vector3d(0, 0, 1), valueAtBoundary,
                        -1, fromBoundary * fromBoundary * 4
                );
            }
            {
                double fromBoundary = patchOriginChunkRelativeCoord.y() + radiusY;
                double valueAtBoundary = fromBoundary;
                gridPatchCellBoundaryInfo[gridPatchCellBoundaryInfoCount++] = new PatchBoundaryInfo(
                        new Vector3d(0, -1, 0), valueAtBoundary,
                        -1, fromBoundary * fromBoundary * 4
                );
            }
            {
                double fromBoundary = (chunkHeight + radiusY) - patchOriginChunkRelativeCoord.y();
                double valueAtBoundary = fromBoundary;
                gridPatchCellBoundaryInfo[gridPatchCellBoundaryInfoCount++] = new PatchBoundaryInfo(
                        new Vector3d(0, 1, 0), valueAtBoundary,
                        -1, fromBoundary * fromBoundary * 4
                );
            }

            // Sort boundaries by distance. This is an optimization effort.
            Arrays.sort(gridPatchCellBoundaryInfo, 0, gridPatchCellBoundaryInfoCount, Comparator.comparingDouble(a -> a.distanceSquaredToOpposingPoint));

            // Initial patch loop range.
            final int patchXZRange = fastCeil(PATCH_RELATIVE_SPACING_XZ * (2 * SQRT2));
            final int patchYRange = fastCeil(PATCH_RELATIVE_SPACING_Y * 2);

            // Hone down the loop range a bit.
            int patchXStart = -Math.min(patchXZRange, calculateXZSearchBound(-patchCell.scaledRotation.x(),  patchCell.scaledRotation.z(), patchOriginChunkRelativeCoord.x(), patchOriginChunkRelativeCoord.z()));
            int patchXEnd   =  Math.min(patchXZRange, calculateXZSearchBound( patchCell.scaledRotation.x(), -patchCell.scaledRotation.z(), patchOriginChunkRelativeCoord.x(), patchOriginChunkRelativeCoord.z()));
            int patchZStart = -Math.min(patchXZRange, calculateXZSearchBound(-patchCell.scaledRotation.z(), -patchCell.scaledRotation.x(), patchOriginChunkRelativeCoord.x(), patchOriginChunkRelativeCoord.z()));
            int patchZEnd   =  Math.min(patchXZRange, calculateXZSearchBound( patchCell.scaledRotation.z(),  patchCell.scaledRotation.x(), patchOriginChunkRelativeCoord.x(), patchOriginChunkRelativeCoord.z()));
            int patchYStart = -Math.min(patchYRange,  fastCeil((            - radiusY - patchOriginChunkRelativeCoord.y()) * -frequencyY));
            int patchYEnd   =  Math.min(patchYRange,  fastCeil((chunkHeight + radiusY - patchOriginChunkRelativeCoord.y()) *  frequencyY));

            for (int igdz = patchZStart; igdz <= patchZEnd; igdz++) {
                for (int igdx = patchXStart; igdx <= patchXEnd; igdx++) {
                    double gdx =  patchCell.scaledRotation.x() * igdx + patchCell.scaledRotation.z() * igdz;
                    double gdz = -patchCell.scaledRotation.z() * igdx + patchCell.scaledRotation.x() * igdz;

                    // Skip entire column if it's out of chunk horizontal contribution range.
                    double gcx = gdx + patchOriginChunkRelativeCoord.x();
                    if (gcx <= -radiusXZ || gcx >= chunkWidth + radiusXZ) continue;
                    double gcz = gdz + patchOriginChunkRelativeCoord.z();
                    if (gcz <= -radiusXZ || gcz >= chunkWidth + radiusXZ) continue;

                    // Refine the range of the column that needs to be added.
                    double tStart = Double.NEGATIVE_INFINITY;
                    double tEnd = Double.POSITIVE_INFINITY;
                    for (int gridPatchCellBoundaryInfoIndex = 0;
                            gridPatchCellBoundaryInfoIndex < gridPatchCellBoundaryInfoCount; gridPatchCellBoundaryInfoIndex++) {
                        PatchBoundaryInfo patchBoundaryInfo = gridPatchCellBoundaryInfo[gridPatchCellBoundaryInfoIndex];

                        double initialDot = gdx * patchBoundaryInfo.floodFillBoundaryPlaneVector.x() + gdz * patchBoundaryInfo.floodFillBoundaryPlaneVector.z();
                        double initialDotMinus = initialDot - patchBoundaryInfo.floodFillBoundaryPlaneMaxValueAtBoundary;
                        double dy = initialDotMinus / -patchBoundaryInfo.floodFillBoundaryPlaneVector.y();

                        if (initialDotMinus < 0) { // In range, dy is where it leaves range.
                            if (dy < 0) tStart = Math.max(tStart, dy);
                            else tEnd = Math.min(tEnd, dy);
                        } else { // Out of range, dy is where it enters range.
                            if (dy < 0) tEnd = Math.min(tEnd, dy);
                            else tStart = Math.max(tStart, dy);
                        }
                    }
                    int thisPatchYStart = Math.max(patchYStart, (int)(tStart * frequencyY));
                    int thisPatchYEnd = Math.min(patchYEnd, (int)(tEnd * frequencyY));

                    // Add the column.
                    for (int igdy = thisPatchYStart; igdy <= thisPatchYEnd; igdy++) {
                        double gdy = igdy * spacingY;
                        Vector3d subGridPointDeltaFromPatchOrigin = new Vector3d(gdx, gdy, gdz);
                        Vector3d subGridPointChunkRelativeCoord = Vector3d.add(patchOriginChunkRelativeCoord, subGridPointDeltaFromPatchOrigin);
                        pointsToSample.add(new PointToSample(subGridPointChunkRelativeCoord, patchCell.scaledRotation));
                    }
                }
            }

            patchCellY++;
            if (patchCellY <= patchCellEndY) continue;
            patchCellY = patchCellStartY;
            patchCellZ++;
            if (patchCellZ <= patchCellEndZ) continue;
            patchCellZ = patchCellStartZ;
            patchCellX++;
            if (patchCellX > patchCellEndX) break;
        }

        // Points need to be sorted in ascending Y order for the column blending to work.
        pointsToSample.sort(Comparator.comparingDouble(a -> a.pointInChunk.y()));

        this.nSortedDatapoints = pointsToSample.size();
        this.datapointDeltaContributions = new double[this.nSortedDatapoints];
        this.sortedDatapointData = DatapointDataArray.create(pointsToSample.size(), nTotalValues);

        // Set the data we'll be interpolating over.
        for (int j = 0; j < this.nSortedDatapoints; j++) {
            PointToSample pointToSample = pointsToSample.get(j);

            // Set most of the parameters.
            this.sortedDatapointData.setX(j, pointToSample.pointInChunk.x());
            this.sortedDatapointData.setY(j, pointToSample.pointInChunk.y());
            this.sortedDatapointData.setZ(j, pointToSample.pointInChunk.z());
            this.sortedDatapointData.setRX(j, pointToSample.scaledRotation.x());
            this.sortedDatapointData.setRZ(j, pointToSample.scaledRotation.z());

            var evaluationPoint = new DensityFunction.SinglePointContext(
                    fastFloor(pointToSample.pointInChunk.x() + chunkBlockX), fastFloor(pointToSample.pointInChunk.y() + chunkBlockY), fastFloor(pointToSample.pointInChunk.z() + chunkBlockZ));

            // Populate the cellular values.
            // Need these first so the interpolated ones can reference them. (TODO lazyload)
            this.currentCellularIndex = j;
            int valueIndex = this.nInterpolatedValues;
            for (DensityFunction function : cellular3DFunctions) {
                this.sortedDatapointData.setValue(j, valueIndex, function.compute(evaluationPoint));
                valueIndex++;
            }

            // Populate the values to be interpolated.
            valueIndex = 0;
            for (DensityFunction function : interpolated3DFunctions) {
                this.sortedDatapointData.setValue(j, valueIndex, function.compute(evaluationPoint));
                valueIndex++;
            }
        }
    }

    private int calculateXZSearchBound(double dirX, double dirZ, double patchX, double patchZ) {
        double cx = dirX < 0 ? -radiusXZ : chunkWidth + radiusXZ;
        double cz = dirZ < 0 ? -radiusXZ : chunkWidth + radiusXZ;
        double t = (dirX * (cx - patchX) + dirZ * (cz - patchZ)) / (dirX * dirX + dirZ * dirZ);
        return fastCeil(t);
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

        // Debug: Show the sampling points in the world.
        /*for (int i = 0; i < this.nSortedDatapoints; i++) {
            if (this.localX != fastFloor(this.sortedDatapointData.getX(i))) continue;
            if (this.localZ != fastFloor(this.sortedDatapointData.getZ(i))) continue;
            if (y           != fastFloor(this.sortedDatapointData.getY(i))) continue;
            Arrays.fill(this.currentNormalizedValues, 1);
            return;
        }
        Arrays.fill(this.currentNormalizedValues, -1);
        if (true) return;*/

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
        return this.sortedDatapointData.getValue(IrreguLerper.this.currentCellularIndex, i + this.nInterpolatedValues);
    }

    private long primeHash(long gxPrime, long gyPrime, long gzPrime) {
        return (seed ^ gxPrime ^ gyPrime ^ gzPrime) * HASH_MULTIPLIER;
    }

    private double calcPointDeltaForXZ(int i) {

        // If the point is in vertical range, but not horizontal range (square containing circle containing rotated square), we're outside of horizontal range.
        double px = sortedDatapointData.getX(i);
        if (xPlusRadius <= px || xMinusRadius >= px) return 0;
        double pz = sortedDatapointData.getZ(i);
        if (zPlusRadius <= pz || zMinusRadius >= pz) return 0;

        // If the point is in vertical range, but not horizontal range (circle), we're outside of horizontal range.
        double dx = localX - px;
        double dz = localZ - pz;
        double distSq = dx*dx + dz*dz;
        if (distSq >= radiusXZ * radiusXZ) return 0;

        // If the point is in vertical range, but not horizontal range (final rotated square in that circle), we're outside range..
        double scaledRotationX = sortedDatapointData.getRX(i);
        double scaledRotationZ = sortedDatapointData.getRZ(i);
        double ardx = Math.abs(dx * scaledRotationX - dz * scaledRotationZ);
        if (ardx >= rotatedSquareWidthHalfScaled) return 0;
        double ardz = Math.abs(dx * scaledRotationZ + dz * scaledRotationX);
        if (ardz >= rotatedSquareWidthHalfScaled) return 0;

        // Trilerp-emulating horizontal falloff value.
        return (rotatedSquareWidthHalfScaled - ardx) * (rotatedSquareWidthHalfScaled - ardz);
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

    }

    private static boolean circleIntersectsSquare(double dx, double dz, double halfSquareWidth, double radius) {

        // Completely outside as if both were squares.
        double adxDelta = Math.abs(dx) - halfSquareWidth;
        if (adxDelta > radius) return false;
        double adzDelta = Math.abs(dz) - halfSquareWidth;
        if (adzDelta > radius) return false;

        // Within square on one axis = other axis decides completely.
        if (adxDelta <= 0) return adzDelta < radius;
        if (adzDelta <= 0) return adxDelta < radius;

        // Otherwise the circle determines intersection on a square corner.
        return adxDelta * adxDelta + adzDelta * adzDelta < radius * radius;
    }

    private static int fastFloor(double x) {
        int xi = (int)x;
        return x < xi ? xi - 1 : xi;
    }

    private static int fastCeil(double x) {
        int xi = (int)x;
        return x > xi ? xi + 1 : xi;
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
            IrreguLerper.this.initForChunk(chunkBlockX, chunkBlockY, chunkBlockZ, masterFunctionContext, this.interpolated3DFunctions, this.cellular3DFunctions);
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
            if (functionContext != IrreguLerper.this.masterFunctionContext)
                return base.compute(functionContext);
            else
                return IrreguLerper.this.getCurrentInterpolatedValue(this.index);
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
        private final DensityFunction base;

        private CellularRegistration(int index, DensityFunction base) {
            this.index = index;
            this.min = base.minValue();
            this.max = base.maxValue();
            this.base = base;
        }

        @Override
        public double compute(FunctionContext functionContext) {
            if (functionContext != IrreguLerper.this.masterFunctionContext)
                return base.compute(functionContext);
            else
                return IrreguLerper.this.getCurrentCellularValue(this.index);
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
}
