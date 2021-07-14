package jpg.k.simplyimprovedterrain.terrain;

import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.StructureWeightSampler;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;

/**
 * Created by K.jpg on 6/10/2021.
 */
public class SimplyImprovedNoiseColumnSampler {

    private final BiomeSource biomeSource;
    private final GenerationShapeConfig config;
    private final SimplyImprovedTerrainNoiseSampler noise;
    private final double effectiveThresholdMultiplier;
    private final double effectiveThresholdOffset;
    private final double[] thresholdSlideModifiers;
    private final int minimumY;

    public SimplyImprovedNoiseColumnSampler(BiomeSource biomeSource, double inverseVerticalNoiseResolution, int noiseSizeY, GenerationShapeConfig config, SimplyImprovedTerrainNoiseSampler noise/*, OctavePerlinNoiseSampler densityNoise, class_6357 arg */) {
        this.biomeSource = biomeSource;
        this.config = config;
        this.noise = noise;
        //this.densityNoise = densityNoise;

        // Pre-calculate initial part of the thresholding formula.
        double twiceInverseHeight = (2.0 / 32.0) * inverseVerticalNoiseResolution;
        double densityFactor = config.getDensityFactor();
        double densityOffset = config.getDensityOffset();
        this.effectiveThresholdMultiplier = -twiceInverseHeight * densityFactor;
        this.effectiveThresholdOffset = densityFactor + densityOffset;

        // Pre-generate the slides to be applied to the terrain threshold.
        double topSlideTarget = (double)config.getTopSlide().getTarget();
        double topSlideSize = (double)config.getTopSlide().getSize();
        double topSlideOffset = (double)config.getTopSlide().getOffset();
        double bottomSlideTarget = (double)config.getBottomSlide().getTarget();
        double bottomSlideSize = (double)config.getBottomSlide().getSize();
        double bottomSlideOffset = (double)config.getBottomSlide().getOffset();
        this.minimumY = config.getMinimumY();
        int generationHeight = config.getHeight();
        thresholdSlideModifiers = new double[generationHeight];
        for (int y = 0; y < generationHeight; y++) {
            double thresholdSlideModifier = 0;
            double yb = (y + this.minimumY) * inverseVerticalNoiseResolution;

            if (topSlideSize > 0) {
                double tBase = ((noiseSizeY - yb) - topSlideOffset);
                if (tBase < topSlideSize) {
                    if (tBase < 0)
                        tBase = 0;
                    double inverseT = topSlideSize / tBase;
                    thresholdSlideModifier += topSlideTarget * (inverseT - 1);
                }
            }

            if (bottomSlideSize > 0) {
                double tBase = (yb - bottomSlideOffset);
                if (tBase < bottomSlideSize) {
                    if (tBase < 0)
                        tBase = 0;
                    double inverseT = bottomSlideSize / tBase;
                    thresholdSlideModifier += bottomSlideTarget * (inverseT - 1);
                }
            }

            thresholdSlideModifiers[y] = thresholdSlideModifier;
        }
    }

    public ColumnSamplingContext columnSamplingContext() {
        return new ColumnSamplingContext();
    }

    private ThreadLocal<ColumnSamplingContext> columnSamplingContextThreadLocal = new ThreadLocal<>() {
        protected ColumnSamplingContext initialValue() {
            return columnSamplingContext();
        }
    };

    public ColumnSamplingContext columnSamplingContextThreadLocal() {
        return columnSamplingContextThreadLocal.get();
    }

    public class ColumnSamplingContext {
        private int chunkWorldX, chunkWorldZ;
        private int worldX, worldZ;
        private double depth, inverseScale;
        private ChunkLocalTerrainContext chunkLocalTerrainContext;
        private StructureWeightSampler structureWeightSampler;

        private ColumnSamplingContext() {
            
        }

        public void setChunkFields(ChunkLocalTerrainContext chunkLocalTerrainContext, StructureWeightSampler structureWeightSampler) {
            this.chunkWorldX = chunkLocalTerrainContext.getChunkWorldX();
            this.chunkWorldZ = chunkLocalTerrainContext.getChunkWorldZ();
            this.chunkLocalTerrainContext = chunkLocalTerrainContext;
            this.structureWeightSampler = structureWeightSampler;
        }

        public void setXZ(int x, int z) {
            this.worldX = this.chunkWorldX | x;
            this.worldZ = this.chunkWorldZ | z;
            this.depth = chunkLocalTerrainContext.getDepth(x, z);
            this.inverseScale = chunkLocalTerrainContext.getInverseScale(x, z);
        }

        public double sampleNoiseSign(int y) {
            double thresholdingValue = calcThresholdingValue(y, depth, inverseScale);
            thresholdingValue = applyThresholdSlides(y, thresholdingValue);

            // TODO Doing this here before the clamp in NoiseChunkGenerator.GetBlockState could cause problems with underground structures. Revisit this if any problems are found.
            thresholdingValue += this.structureWeightSampler.getWeight(worldX, y, worldZ) * 400.0;

            double noiseSignValue = noise.sampleNoiseSign(thresholdingValue, worldX, y, worldZ);

            // TODO noise caves & corresponding conditional noise layer skipping.
            return noiseSignValue;
        }

    }

    private double calcThresholdingValue(int y, double depth, double inverseScale/*, double densityNoise2D*/) {
        double thresholdingValue = y;
        thresholdingValue = y * effectiveThresholdMultiplier + effectiveThresholdOffset;
        thresholdingValue = (thresholdingValue + depth) * inverseScale;
        if (thresholdingValue > 0) thresholdingValue *= 4;
        return thresholdingValue;
    }

    private double applyThresholdSlides(int y, double thresholdingValue) {
        return thresholdingValue + this.thresholdSlideModifiers[y - this.minimumY];
    }



}
