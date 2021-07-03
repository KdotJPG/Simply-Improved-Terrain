package jpg.k.simplyimprovedterrain.terrain;

import java.lang.ThreadLocal;

import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import org.jetbrains.annotations.Nullable;

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

    public SimplyImprovedNoiseColumnSampler(BiomeSource biomeSource, double inverseVerticalNoiseResolution, int noiseSizeY, GenerationShapeConfig config, SimplyImprovedTerrainNoiseSampler noise/*, OctavePerlinNoiseSampler densityNoise, class_6357 arg */) {
        this.biomeSource = biomeSource;
        this.config = config;
        this.noise = noise;
        //this.densityNoise = densityNoise;

        // Pre-calculate initial part of the thresholding formula.
        //double twiceInverseWorldHeight = 2.0 / config.getHeight(); // TODO I saw this value hardcoded in the new code. Revisit.
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
        int generationHeight = config.getHeight();
        thresholdSlideModifiers = new double[generationHeight];
        for (int y = 0; y < generationHeight; y++) {
            double thresholdSlideModifier = 0;
            double yb = y * inverseVerticalNoiseResolution;

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

        private ColumnSamplingContext() {
            
        }

        public void setChunkLocalTerrainContext(ChunkLocalTerrainContext chunkLocalTerrainContext) {
            this.chunkWorldX = chunkLocalTerrainContext.getChunkWorldX();
            this.chunkWorldZ = chunkLocalTerrainContext.getChunkWorldZ();
            this.chunkLocalTerrainContext = chunkLocalTerrainContext;
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
        return thresholdingValue + this.thresholdSlideModifiers[y];
    }



}
