package jpg.k.simplyimprovedterrain.terrain.tieredgen;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;

public class PoissonDiskPlugin3D implements PointDistributionPlugin3D {

    private static final double CELL_OVERFLOW_SAFETY_SPACING_MULTIPLIER = 129.0 / 128.0;

    private static final int MAX_POINTS_PER_CELL = 14;
    private static final double POINT_SPACING_SQUARED_FOR_UNIT_CELL = 0.5 * Mth.square(CELL_OVERFLOW_SAFETY_SPACING_MULTIPLIER * 2.0 / 3.0);

    private static final double POINT_SPACING_FOR_UNIT_CELL = Math.sqrt(POINT_SPACING_SQUARED_FOR_UNIT_CELL);

    private static final float WORKING_GRID_POINT_BUFFER = 2.0f;
    private static final float WORKING_GRID_BUFFER_UNIT = (float) (WORKING_GRID_POINT_BUFFER * POINT_SPACING_FOR_UNIT_CELL);

    public static RegistrarBuilder registrarBuilder(TieredChunkGrid3D.PluginRegistrar pluginRegistrar, float pointSpacing, long seed) {
        return new RegistrarBuilder(pluginRegistrar, pointSpacing, seed);
    }

    public static class RegistrarBuilder {

        private final PluginInternal pluginInternal;
        private final int pluginIndex;
        private final int chunkDataIndex;
        private final int xOffset, xSize, yOffset, ySize, zOffset, zSize;
        private final float gridFrequency;

        private RegistrarBuilder(TieredChunkGrid3D.PluginRegistrar pluginRegistrar, float pointSpacing, long seed) {
            if (!(pluginRegistrar.chunkSpacingWithinTier() >= pointSpacing * 2)) {
                throw new IllegalArgumentException("Chunk spacing must be at least twice the point spacing.");
            }

            ConvexPolytope3D.Vec3f min = pluginRegistrar.chunkShape().axialMin();
            ConvexPolytope3D.Vec3f max = pluginRegistrar.chunkShape().axialMax();

            gridFrequency = (float) (POINT_SPACING_FOR_UNIT_CELL / pointSpacing);
            float gridScale = (float) (pointSpacing / POINT_SPACING_FOR_UNIT_CELL);

            xOffset = -Mth.floor(min.x() * gridFrequency);
            xSize = Mth.floor(max.x() * gridFrequency) + xOffset;
            yOffset = -Mth.floor(min.y() * gridFrequency);
            ySize = Mth.floor(max.y() * gridFrequency) + yOffset;
            zOffset = -Mth.floor(min.z() * gridFrequency);
            zSize = Mth.floor(max.z() * gridFrequency) + zOffset;

            int xOffsetWorkingGrid = -Mth.floor(min.x() * gridFrequency - WORKING_GRID_BUFFER_UNIT);
            int xSizeWorkingGrid = Mth.floor(max.x() * gridFrequency + WORKING_GRID_BUFFER_UNIT) + xOffsetWorkingGrid;
            int yOffsetWorkingGrid = -Mth.floor(min.y() * gridFrequency - WORKING_GRID_BUFFER_UNIT);
            int ySizeWorkingGrid = Mth.floor(max.y() * gridFrequency + WORKING_GRID_BUFFER_UNIT) + yOffsetWorkingGrid;
            int zOffsetWorkingGrid = -Mth.floor(min.z() * gridFrequency - WORKING_GRID_BUFFER_UNIT);
            int zSizeWorkingGrid = Mth.floor(max.z() * gridFrequency + WORKING_GRID_BUFFER_UNIT) + zOffsetWorkingGrid;

            chunkDataIndex = pluginRegistrar.registerChunkDataIndices(1);
            pluginInternal = new PluginInternal(
                    seed, chunkDataIndex, pointSpacing, gridFrequency, gridScale, gridScale * WORKING_GRID_BUFFER_UNIT,
                    xOffset, xSize, yOffset, ySize, zOffset, zSize,
                    xOffsetWorkingGrid, xSizeWorkingGrid, yOffsetWorkingGrid, ySizeWorkingGrid, zOffsetWorkingGrid, zSizeWorkingGrid
            );
            pluginIndex = pluginRegistrar.registerPlugin(pluginInternal);
        }

        public PoissonDiskPlugin3D build(TieredChunkGrid3D tieredChunkGrid) {
            tieredChunkGrid.validatePlugin(pluginInternal, pluginIndex);
            return new PoissonDiskPlugin3D(tieredChunkGrid, chunkDataIndex, xOffset, xSize, yOffset, ySize, zOffset, zSize, gridFrequency);
        }
    }

    private final TieredChunkGrid3D tieredChunkGrid;
    private final int chunkDataIndex;
    private final int xOffset, xSize, yOffset, ySize, zOffset, zSize;
    private final float gridFrequency;

    private PoissonDiskPlugin3D(
            TieredChunkGrid3D tieredChunkGrid, int chunkDataIndex,
            int xOffset, int xSize, int yOffset, int ySize, int zOffset, int zSize,
            float gridFrequency
    ) {
        this.tieredChunkGrid = tieredChunkGrid;
        this.chunkDataIndex = chunkDataIndex;
        this.xOffset = xOffset;
        this.xSize = xSize;
        this.yOffset = yOffset;
        this.ySize = ySize;
        this.zOffset = zOffset;
        this.zSize = zSize;
        this.gridFrequency = gridFrequency;
    }

    @Override
    public void forPointsInChunk(
            double x, double y, double z,
            ConvexPolytope3D queryShape, float queryPadding, float rejectionPadding,
            TieredChunkGrid3D.TieredChunk3D chunk, float dxChunk, float dyChunk, float dzChunk,
            PointIterationHandler handler
    ) {
        int xGridStart = Math.max(0,         Mth.floor((queryShape.axialMin().x() - dxChunk - queryPadding) * gridFrequency) + xOffset);
        int xGridEnd   = Math.min(xSize - 1, Mth.floor((queryShape.axialMax().x() - dxChunk + queryPadding) * gridFrequency) + xOffset);
        int yGridStart = Math.max(0,         Mth.floor((queryShape.axialMin().y() - dyChunk - queryPadding) * gridFrequency) + yOffset);
        int yGridEnd   = Math.min(ySize - 1, Mth.floor((queryShape.axialMax().y() - dyChunk + queryPadding) * gridFrequency) + yOffset);
        int zGridStart = Math.max(0,         Mth.floor((queryShape.axialMin().z() - dzChunk - queryPadding) * gridFrequency) + zOffset);
        int zGridEnd   = Math.min(zSize - 1, Mth.floor((queryShape.axialMax().z() - dzChunk + queryPadding) * gridFrequency) + zOffset);

        Point3D[] points = getPointArray(chunk);
        for (int zGrid = zGridStart; zGrid <= zGridEnd; zGrid++) {
            for (int xGrid = xGridStart; xGrid <= xGridEnd; xGrid++) {
                for (int yGrid = yGridStart; yGrid <= yGridEnd; yGrid++) {
                    for (int indexPointRelative = 0; indexPointRelative < MAX_POINTS_PER_CELL; indexPointRelative++) {
                        int index = indexPointRelative + MAX_POINTS_PER_CELL * (yGrid + ySize * (xGrid + xSize * zGrid));
                        Point3D point = points[index];
                        if (point == null) break;

                        if (queryShape.isPointInRange(point.x() + dxChunk, point.y() + dyChunk, point.z() + dzChunk, queryPadding)) {
                            if (!queryShape.isPointInRange(point.x() + dxChunk, point.y() + dyChunk, point.z() + dzChunk, rejectionPadding)) {
                                boolean continueIteration = handler.handle(point);
                                if (!continueIteration) return;
                            }
                        }
                    }
                }
            }
        }
    }

    public Point3D[] getPointArray(TieredChunkGrid3D.TieredChunk3D chunk) {
        return getPointArray(chunk.dataEntries());
    }

    public Point3D[] getPointArray(Object[] chunkDataEntries) {
        return (Point3D[]) chunkDataEntries[chunkDataIndex];
    }

    private static class PluginInternal implements TieredChunkGrid3D.PluginInternal {

        private final long seed;
        private final int chunkDataIndex;
        private final float pointSpacing, gridFrequency, gridScale, workingGridBuffer;
        private final int xOffset, xSize, yOffset, ySize, zOffset, zSize;
        private final int xOffsetWorkingGrid, xSizeWorkingGrid, yOffsetWorkingGrid, ySizeWorkingGrid, zOffsetWorkingGrid, zSizeWorkingGrid;

        private record BridsonPivotPoint(
                float xUnit, float yUnit, float zUnit, int index,
                Quaternionf rotation, Permutation permutation
        ) { }

        private PluginInternal(
                long seed, int chunkDataIndex, float pointSpacing, float gridFrequency, float gridScale, float workingGridBuffer,
                int xOffset, int xSize, int yOffset, int ySize, int zOffset, int zSize,
                int xOffsetWorkingGrid, int xSizeWorkingGrid, int yOffsetWorkingGrid, int ySizeWorkingGrid, int zOffsetWorkingGrid, int zSizeWorkingGrid
        ) {
            this.seed = seed;
            this.chunkDataIndex = chunkDataIndex;
            this.pointSpacing = pointSpacing;
            this.gridFrequency = gridFrequency;
            this.gridScale = gridScale;
            this.workingGridBuffer = workingGridBuffer;
            this.xOffset = xOffset;
            this.xSize = xSize;
            this.yOffset = yOffset;
            this.ySize = ySize;
            this.zOffset = zOffset;
            this.zSize = zSize;
            this.xOffsetWorkingGrid = xOffsetWorkingGrid;
            this.xSizeWorkingGrid = xSizeWorkingGrid;
            this.yOffsetWorkingGrid = yOffsetWorkingGrid;
            this.ySizeWorkingGrid = ySizeWorkingGrid;
            this.zOffsetWorkingGrid = zOffsetWorkingGrid;
            this.zSizeWorkingGrid = zSizeWorkingGrid;
        }

        @Override
        public void populateChunkData(
                Object[] chunkDataEntries, TieredChunkGrid3D.NeighborTieredChunk3D[] neighborhood,
                long unseededHash, int tier, TieredChunkGrid3D.ChunkUtils chunkUtils
        ) {

            RandomSource random = new XoroshiroRandomSource(seed ^ unseededHash);
            ArrayList<BridsonPivotPoint> bridsonPivotPoints = new ArrayList<>();

            WorkingPoint3D[] workingPointGrid = new WorkingPoint3D[xSizeWorkingGrid * ySizeWorkingGrid * zSizeWorkingGrid * MAX_POINTS_PER_CELL];
            byte[] workingGridCellPointCounts = new byte[xSizeWorkingGrid * ySizeWorkingGrid * zSizeWorkingGrid];
            for (TieredChunkGrid3D.NeighborTieredChunk3D neighborChunk : neighborhood) {
                copyChunkToWorkingGrid(neighborChunk, workingPointGrid, workingGridCellPointCounts, (Point3D[]) neighborChunk.dataEntries()[chunkDataIndex], bridsonPivotPoints);
            }

            if (tier == 0) {
                Vector3f firstPointPosition = chunkUtils.randomPointInChunk(random, new Vector3f());
                int index = tryPlacePointWithTierCheck(
                        workingPointGrid, workingGridCellPointCounts,
                        firstPointPosition.x() * gridFrequency, firstPointPosition.y() * gridFrequency, firstPointPosition.z() * gridFrequency,
                        sampleRotation(random, new Quaternionf()), tier, chunkUtils
                );
                bridsonPivotPoints.add(new BridsonPivotPoint(
                        firstPointPosition.x() * gridFrequency, firstPointPosition.y() * gridFrequency, firstPointPosition.z() * gridFrequency,
                        index, sampleRotation(random, new Quaternionf()), new Permutation(SPHERICAL_SHELL_SAMPLE_COUNT)
                ));
            }

            Vector3f sampleDelta = new Vector3f();
            while (!bridsonPivotPoints.isEmpty()) {
                int bridsonFrontEntryIndex = random.nextInt(bridsonPivotPoints.size());
                BridsonPivotPoint bridsonPivotPoint = bridsonPivotPoints.get(bridsonFrontEntryIndex);

                Quaternionf nextRotation = sampleRotation(random, new Quaternionf());
                while (!bridsonPivotPoint.permutation().isEmpty()) {
                    int sampleIndex = bridsonPivotPoint.permutation().sample(random) * 3;

                    sampleDelta.set(
                            UNROTATED_SPHERICAL_SHELL_SAMPLES_INLINE[sampleIndex    ],
                            UNROTATED_SPHERICAL_SHELL_SAMPLES_INLINE[sampleIndex + 1],
                            UNROTATED_SPHERICAL_SHELL_SAMPLES_INLINE[sampleIndex + 2]
                    );
                    bridsonPivotPoint.rotation().transform(sampleDelta, sampleDelta);

                    int placedPointIndex = tryPlaceSpacedPoint(
                            workingPointGrid, workingGridCellPointCounts,
                            bridsonPivotPoint.xUnit() + sampleDelta.x(),
                            bridsonPivotPoint.yUnit() + sampleDelta.y(),
                            bridsonPivotPoint.zUnit() + sampleDelta.z(),
                            bridsonPivotPoint.index(),
                            nextRotation,
                            tier,
                            chunkUtils
                    );

                    if (placedPointIndex >= 0) {
                        WorkingPoint3D placedPoint = workingPointGrid[placedPointIndex];
                        bridsonPivotPoints.add(new BridsonPivotPoint(
                                placedPoint.xUnit(), placedPoint.yUnit(), placedPoint.zUnit(),
                                placedPointIndex, nextRotation, new Permutation(SPHERICAL_SHELL_SAMPLE_COUNT)
                        ));
                        break;
                    }
                }

                // Swap/remove point from Bridson pivot list
                if (bridsonPivotPoint.permutation().isEmpty()) {
                    if (bridsonPivotPoints.size() > 1) {
                        int bridsonFrontEntryIndexAtEnd = bridsonPivotPoints.size() - 1;
                        BridsonPivotPoint bridsonPivotPointAtEnd = bridsonPivotPoints.remove(bridsonFrontEntryIndexAtEnd);
                        if (bridsonFrontEntryIndex != bridsonFrontEntryIndexAtEnd) {
                            bridsonPivotPoints.set(bridsonFrontEntryIndex, bridsonPivotPointAtEnd);
                        }
                    } else {
                        bridsonPivotPoints.clear();
                    }
                }
            }

            chunkDataEntries[chunkDataIndex] = createFinalGrid(workingPointGrid, workingGridCellPointCounts, tier);
        }

        private int tryPlaceSpacedPoint(
                WorkingPoint3D[] workingPointGrid, byte[] workingGridCellPointCounts,
                float xUnit, float yUnit, float zUnit, int knownGoodIndex,
                Quaternionf rotation, int tier, TieredChunkGrid3D.ChunkUtils chunkUtils) {

            if (!chunkUtils.chunkShape().isPointInRange(xUnit * gridScale, yUnit * gridScale, zUnit * gridScale, this.workingGridBuffer)) return -1;

            int xSearchStart = Mth.clamp(Mth.floor(xUnit - POINT_SPACING_FOR_UNIT_CELL) + xOffsetWorkingGrid, 0, xSizeWorkingGrid - 1);
            int ySearchStart = Mth.clamp(Mth.floor(yUnit - POINT_SPACING_FOR_UNIT_CELL) + yOffsetWorkingGrid, 0, ySizeWorkingGrid - 1);
            int zSearchStart = Mth.clamp(Mth.floor(zUnit - POINT_SPACING_FOR_UNIT_CELL) + zOffsetWorkingGrid, 0, zSizeWorkingGrid - 1);

            int xSearchEnd = Mth.clamp(Mth.floor(xUnit + POINT_SPACING_FOR_UNIT_CELL) + xOffsetWorkingGrid, 0, xSizeWorkingGrid - 1);
            int ySearchEnd = Mth.clamp(Mth.floor(yUnit + POINT_SPACING_FOR_UNIT_CELL) + yOffsetWorkingGrid, 0, ySizeWorkingGrid - 1);
            int zSearchEnd = Mth.clamp(Mth.floor(zUnit + POINT_SPACING_FOR_UNIT_CELL) + zOffsetWorkingGrid, 0, zSizeWorkingGrid - 1);

            for (int zCell = zSearchStart; zCell <= zSearchEnd; zCell++) {
                for (int xCell = xSearchStart; xCell <= xSearchEnd; xCell++) {
                    for (int yCell = ySearchStart; yCell <= ySearchEnd; yCell++) {
                        int indexBase = yCell + ySizeWorkingGrid * (xCell + xSizeWorkingGrid * zCell);
                        int pointCount = workingGridCellPointCounts[indexBase];
                        int indexStart = indexBase * MAX_POINTS_PER_CELL;

                        for (int pointIndexRelative = 0; pointIndexRelative < pointCount; pointIndexRelative++) {
                            int index = indexStart + pointIndexRelative;
                            if (index == knownGoodIndex) continue;

                            WorkingPoint3D point = workingPointGrid[index];
                            if (point.distanceSquared(xUnit, yUnit, zUnit) < POINT_SPACING_FOR_UNIT_CELL * POINT_SPACING_FOR_UNIT_CELL) {
                                return -1;
                            }
                        }
                    }
                }
            }

            return tryPlacePointWithTierCheck(workingPointGrid, workingGridCellPointCounts, xUnit, yUnit, zUnit, rotation, tier, chunkUtils);
        }

        private int tryPlacePointWithTierCheck(
                WorkingPoint3D[] workingPointGrid, byte[] workingGridCellPointCounts,
                float xUnit, float yUnit, float zUnit,
                Object rotation, int currentChunkTier,
                TieredChunkGrid3D.ChunkUtils chunkUtils
        ) {

            // TODO address the unlikely boundary case where a point lands on the corner of another chunk of the same tier.
            int containingChunkTier = chunkUtils.getChunkTierForPoint(xUnit * gridScale, yUnit * gridScale, zUnit * gridScale, currentChunkTier);

            // Reject points placed into areas covered by chunks already generated in previous tiers.
            // Such points, which cannot make it into the final distribution (+ are omitted during the `createFinalGrid()` step),
            // could create conflict gaps in the current generation tier which then cannot be filled by the current or later generation tiers.
            if (containingChunkTier < currentChunkTier) return -1;

            return tryPlacePointDirect(workingPointGrid, workingGridCellPointCounts, xUnit, yUnit, zUnit, rotation, containingChunkTier);
        }

        private int tryPlacePointDirect(
                WorkingPoint3D[] workingPointGrid, byte[] workingGridCellPointCounts,
                float xUnit, float yUnit, float zUnit,
                Object rotation, int originatingChunkTier
        ) {

            int xTargetCell = Mth.floor(xUnit) + xOffsetWorkingGrid;
            int yTargetCell = Mth.floor(yUnit) + yOffsetWorkingGrid;
            int zTargetCell = Mth.floor(zUnit) + zOffsetWorkingGrid;

            if (xTargetCell < 0) return -1;
            else if (xTargetCell >= xSizeWorkingGrid) return -1;
            if (yTargetCell < 0) return -1;
            else if (yTargetCell >= ySizeWorkingGrid) return -1;
            if (zTargetCell < 0) return -1;
            else if (zTargetCell >= zSizeWorkingGrid) return -1;

            int indexTargetCellBase = yTargetCell + ySizeWorkingGrid * (xTargetCell + xSizeWorkingGrid * zTargetCell);
            int pointIndexRelative = workingGridCellPointCounts[indexTargetCellBase];
            if (pointIndexRelative >= MAX_POINTS_PER_CELL) return -2;
            int indexTarget = indexTargetCellBase * MAX_POINTS_PER_CELL + pointIndexRelative;

            workingPointGrid[indexTarget] = new WorkingPoint3D(xUnit, yUnit, zUnit, rotation, originatingChunkTier);
            workingGridCellPointCounts[indexTargetCellBase]++;
            return indexTarget;
        }

        private void copyChunkToWorkingGrid(
                TieredChunkGrid3D.NeighborTieredChunk3D neighborChunk, WorkingPoint3D[] workingPointGrid, byte[] workingGridCellPointCounts,
                Point3D[] chunkPointGrid, ArrayList<BridsonPivotPoint> bridsonPivotPoints
        ) {

            float dxChunkUnit = neighborChunk.dx() * gridFrequency;
            float dyChunkUnit = neighborChunk.dy() * gridFrequency;
            float dzChunkUnit = neighborChunk.dz() * gridFrequency;

            int xStartUnbounded = xOffsetWorkingGrid - xOffset - Mth.floor(dxChunkUnit);
            int yStartUnbounded = yOffsetWorkingGrid - yOffset - Mth.floor(dyChunkUnit);
            int zStartUnbounded = zOffsetWorkingGrid - zOffset - Mth.floor(dzChunkUnit);

            int xStart = Math.max(0, xStartUnbounded);
            int yStart = Math.max(0, yStartUnbounded);
            int zStart = Math.max(0, zStartUnbounded);

            int xStop = Math.min(xSize, xStartUnbounded + xSizeWorkingGrid);
            int yStop = Math.min(ySize, xStartUnbounded + ySizeWorkingGrid);
            int zStop = Math.min(zSize, xStartUnbounded + zSizeWorkingGrid);

            for (int zSource = zStart; zSource < zStop; zSource++) {
                for (int xSource = xStart; xSource < xStop; xSource++) {
                    for (int ySource = yStart; ySource < yStop; ySource++) {
                        int indexSourceStart = (ySource + ySize * (xSource + xSize * zSource)) * MAX_POINTS_PER_CELL;

                        for (int pointIndexRelativeSource = 0; pointIndexRelativeSource < MAX_POINTS_PER_CELL; pointIndexRelativeSource++) {
                            Point3D point = chunkPointGrid[indexSourceStart + pointIndexRelativeSource];
                            if (point == null) break;

                            float xUnit = point.x() * gridFrequency + dxChunkUnit;
                            float yUnit = point.y() * gridFrequency + dyChunkUnit;
                            float zUnit = point.z() * gridFrequency + dzChunkUnit;

                            int placedPointIndex = tryPlacePointDirect(workingPointGrid, workingGridCellPointCounts, xUnit, yUnit, zUnit, point.dataEntries()[0], neighborChunk.tier());
                            if (placedPointIndex >= 0) bridsonPivotPoints.add(new BridsonPivotPoint(
                                    xUnit, yUnit, zUnit,
                                    placedPointIndex, (Quaternionf) point.dataEntries()[0], new Permutation(SPHERICAL_SHELL_SAMPLE_COUNT)
                            ));
                        }
                    }
                }
            }
        }

        private Point3D[] createFinalGrid(WorkingPoint3D[] workingPointGrid, byte[] workingGridCellPointCounts, int tier) {

            Point3D[] finalGrid = new Point3D[xSize * ySize * zSize * MAX_POINTS_PER_CELL];

            for (int zDest = 0; zDest < zSize; zDest++) {
                for (int xDest = 0; xDest < xSize; xDest++) {
                    for (int yDest = 0; yDest < ySize; yDest++) {

                        int xSource = xDest + (xOffsetWorkingGrid - xOffset);
                        int ySource = yDest + (yOffsetWorkingGrid - yOffset);
                        int zSource = zDest + (zOffsetWorkingGrid - zOffset);
                        int indexSourceBase = ySource + ySizeWorkingGrid * (xSource + xSizeWorkingGrid * zSource);
                        int pointCountSource = workingGridCellPointCounts[indexSourceBase];
                        int indexSourceStart = indexSourceBase * MAX_POINTS_PER_CELL;

                        int indexDestStart = (yDest + ySize * (xDest + xSize * zDest)) * MAX_POINTS_PER_CELL;

                        int pointIndexRelativeDest = 0;
                        for (int pointIndexRelativeSource = 0; pointIndexRelativeSource < pointCountSource; pointIndexRelativeSource++) {
                            WorkingPoint3D workingPoint = workingPointGrid[indexSourceStart + pointIndexRelativeSource];
                            if (workingPoint.originatingChunkTier() != tier) continue;

                            float x = workingPoint.xUnit() * gridScale;
                            float y = workingPoint.yUnit() * gridScale;
                            float z = workingPoint.zUnit() * gridScale;

                            finalGrid[indexDestStart + pointIndexRelativeDest] = new Point3D(
                                    x, y, z,
                                    new Object[] { workingPoint.workingData } // TODO populate extra data from sub-plugins
                            );
                            pointIndexRelativeDest++;
                        }

                    }
                }
            }

            return finalGrid;
        }
    }

    private static Vector3f sampleUnitVector(RandomSource random, Vector3f destination) {
        float sphereY = random.nextFloat() * 2.0f - 1.0f;
        float sphereTheta = random.nextFloat() * Mth.TWO_PI;
        float sphereXZScale = Mth.sqrt(1.0f - sphereY * sphereY);

        return destination.set(
                sphereXZScale * Mth.cos(sphereTheta),
                sphereY,
                sphereXZScale * Mth.sin(sphereTheta)
        );
    }

    private static Quaternionf sampleRotation(RandomSource random, Quaternionf destination) {
        Vector3f axis = sampleUnitVector(random, new Vector3f());
        return destination.setAngleAxis(random.nextFloat() * Mth.TWO_PI, axis.x(), axis.y(), axis.z());
    }

    private static class Permutation {
        private final int[] indicesRelative;
        private int remainingCount;

        public Permutation(int count) {
            indicesRelative = new int[count];
            remainingCount = count;
        }

        public int sample(RandomSource random) {
            int indexIndex = remainingCount == 0 ? 0 : random.nextInt(remainingCount);
            int indexToReturn = indicesRelative[indexIndex] + indexIndex;
            remainingCount--;
            int indexAtEnd = indicesRelative[remainingCount] + remainingCount;
            indicesRelative[indexIndex] = indexAtEnd - indexIndex;
            return indexToReturn;
        }

        public boolean isEmpty() {
            return remainingCount == 0;
        }
    }

    private record WorkingPoint3D(
            float xUnit, float yUnit, float zUnit,
            Object workingData, int originatingChunkTier
    ) {

        public float distanceSquared(float xUnit, float yUnit, float zUnit) {
            return  Mth.square(xUnit - this.xUnit) +
                    Mth.square(yUnit - this.yUnit) +
                    Mth.square(zUnit - this.zUnit);
        }
    }

    /*
     * In non-negative skewed (A3*) 3D coordinates, define a point on the inner sphere of the spherical shell to be used for neighbor placement.
     * The purpose of using this over a plain radius is to ensure the possibility of sample points precisely at the inner (and consequentially also outer) radii.
     * The inner radius computed from this coordinate will be rescaled to 1R, and the outer radius will be 2R, where R is the Poisson disc radius.
     * The A3* lattice (≅BCC) is chosen because it is the sparsest covering in 3D, requiring relatively few samples compared to how small the widest voids are.
     */
    private static final int SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_X = 2;
    private static final int SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Y = 1;
    private static final int SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Z = 1;

    // To cover the inner and outer spheres, this is the bound we need to loop over in skewed coordinates.
    private static final int SHELL_AXIAL_SKEW_COVERAGE_RADIUS = 2 * (
            SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_X +
            SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Y +
            SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Z
    );

    private static final int SHELL_INNER_RADIUS_TIMES_SIX_SQUARED =
            Mth.square(SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_X * 5 - SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Y - SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Z) +
            Mth.square(SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Y * 5 - SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_X - SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Z) +
            Mth.square(SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Z * 5 - SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_X - SHELL_INNER_RADIUS_AXIAL_SKEW_COMPONENT_Y);

    private static final double SHELL_INNER_RADIUS_RESCALE = Math.sqrt(POINT_SPACING_SQUARED_FOR_UNIT_CELL / SHELL_INNER_RADIUS_TIMES_SIX_SQUARED);

    private static final float[] UNROTATED_SPHERICAL_SHELL_SAMPLES_INLINE;
    private static final int SPHERICAL_SHELL_SAMPLE_COUNT;
    static {
        FloatArrayList samples = new FloatArrayList();

        for (int zSkew = -SHELL_AXIAL_SKEW_COVERAGE_RADIUS; zSkew <= SHELL_AXIAL_SKEW_COVERAGE_RADIUS; zSkew++) {
            for (int ySkew = -SHELL_AXIAL_SKEW_COVERAGE_RADIUS; ySkew <= SHELL_AXIAL_SKEW_COVERAGE_RADIUS; ySkew++) {
                for (int xSkew = -SHELL_AXIAL_SKEW_COVERAGE_RADIUS; xSkew <= SHELL_AXIAL_SKEW_COVERAGE_RADIUS; xSkew++) {
                    int unskewDeltaTimesSix = zSkew + ySkew + xSkew;
                    int xTimesSix = xSkew * 6 - unskewDeltaTimesSix;
                    int yTimesSix = ySkew * 6 - unskewDeltaTimesSix;
                    int zTimesSix = zSkew * 6 - unskewDeltaTimesSix;
                    int distanceTimesSixSquared = xTimesSix * xTimesSix + yTimesSix * yTimesSix + zTimesSix * zTimesSix;

                    if (distanceTimesSixSquared >= SHELL_INNER_RADIUS_TIMES_SIX_SQUARED && distanceTimesSixSquared <= SHELL_INNER_RADIUS_TIMES_SIX_SQUARED * 4) {
                        samples.add((float)(xTimesSix * SHELL_INNER_RADIUS_RESCALE));
                        samples.add((float)(yTimesSix * SHELL_INNER_RADIUS_RESCALE));
                        samples.add((float)(zTimesSix * SHELL_INNER_RADIUS_RESCALE));
                    }
                }
            }
        }

        UNROTATED_SPHERICAL_SHELL_SAMPLES_INLINE = samples.toFloatArray();
        SPHERICAL_SHELL_SAMPLE_COUNT = UNROTATED_SPHERICAL_SHELL_SAMPLES_INLINE.length / 3;
    }
}
