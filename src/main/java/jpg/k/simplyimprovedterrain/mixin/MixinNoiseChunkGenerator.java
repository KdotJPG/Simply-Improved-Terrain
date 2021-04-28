package jpg.k.simplyimprovedterrain.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.feature.jigsaw.JigsawJunction;
import net.minecraft.world.gen.feature.structure.StructurePiece;
import net.minecraft.util.math.*;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.world.gen.feature.structure.StructureManager;
import net.minecraft.world.gen.DimensionSettings;
import net.minecraft.world.gen.settings.NoiseSettings;
import net.minecraft.world.gen.NoiseChunkGenerator;
import net.minecraft.world.gen.SimplexNoiseGenerator;
import net.minecraft.world.gen.feature.jigsaw.JigsawPattern;
import net.minecraft.world.gen.feature.structure.AbstractVillagePiece;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.Heightmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;
import java.util.function.Supplier;

import jpg.k.simplyimprovedterrain.util.noise.MetaballEndIslandNoise;
import jpg.k.simplyimprovedterrain.util.noise.NeoNotchNoise;
import jpg.k.simplyimprovedterrain.biome.blending.ScatteredBiomeBlender;
import jpg.k.simplyimprovedterrain.mixinapi.ISimplexNoiseGenerator;
import jpg.k.simplyimprovedterrain.biome.CachedScatteredBiomeMagnifier;
import jpg.k.simplyimprovedterrain.biome.blending.LinkedBiomeWeightMap;

@Mixin(NoiseChunkGenerator.class)
public class MixinNoiseChunkGenerator {

    private static final double NOISE_MAIN_FREQUENCY = 684.412 / 32768.0;
    private static final double BLEND_NOISE_RELATIVE_FREQUENCY = 256.0;
    private static final double PRIMARY_NOISE_MAIN_AMPLITUDE = 64.0;
    private static final double BLEND_NOISE_MAIN_AMPLITUDE = 6.4;
    private static final int N_OCTAVES_PRIMARY = 6;
    private static final int N_OCTAVES_BLEND = 3;

    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    @Shadow
    protected Supplier<DimensionSettings> field_236080_h_;

    @Shadow
    private int verticalNoiseGranularity;

    @Shadow
    private int horizontalNoiseGranularity;

    @Shadow
    private int noiseSizeY;

    @Shadow
    public long field_236084_w_;

    @Shadow
    protected SharedSeedRandom randomSeed;

    @Shadow
    private int field_236085_x_;

    @Shadow
    private SimplexNoiseGenerator field_236083_v_;

    private NeoNotchNoise[] newNoiseOctaves1;
    private NeoNotchNoise[] newNoiseOctaves2;
    private NeoNotchNoise[] newNoiseOctavesBlend;
    private double[] primaryUncertaintyBounds;
    private double[] blendUncertaintyBounds;

    private BiomeProvider biomeSource;
    private int seaLevel;
    private double inverseVerticalNoiseResolution;
    private double inverseHorizontalNoiseResolution;
    private double noiseXZScale;
    private double noiseYScale;
    private double blendNoiseXZScale;
    private double blendNoiseYScale;
    private double ratioFrequencyToSmooth;
    private double effectiveThresholdMultiplier;
    private double effectiveThresholdOffset;
    private double[] thresholdSlideModifiers;
    private int effectiveBiomeBlurKernelRadius;
    private float[] biomeBlurKernel;
    private float[] biomeBlurKernel2;

    private ScatteredBiomeBlender scatteredBiomeBlender;
    private NoiseSettings generationShapeConfig;

    boolean useEndIslandNoise;

    @Inject(method = "<init>(Lnet/minecraft/world/biome/provider/BiomeProvider;Lnet/minecraft/world/biome/provider/BiomeProvider;JLjava/util/function/Supplier;)V", at = @At("TAIL"))
    private void injectConstructor(BiomeProvider biomeSource, BiomeProvider biomeSource2, long worldSeed,
            Supplier<DimensionSettings> supplier, CallbackInfo cir) {

        this.biomeSource = biomeSource;

        DimensionSettings chunkGeneratorSettings = (DimensionSettings) field_236080_h_.get();
        NoiseSettings generationShapeConfig = chunkGeneratorSettings.func_236113_b_();

        this.inverseVerticalNoiseResolution = 1.0 / verticalNoiseGranularity;
        this.inverseHorizontalNoiseResolution = 1.0 / horizontalNoiseGranularity;

        // Generation configuration properties
        double thresholdTopSlideTarget = (double) generationShapeConfig.func_236172_c_().func_236186_a_();
        double thresholdTopSlideSize = (double) generationShapeConfig.func_236172_c_().func_236188_b_();
        double thresholdTopSlideOffset = (double) generationShapeConfig.func_236172_c_().func_236189_c_();
        double thresholdBottomSlideTarget = (double) generationShapeConfig.func_236173_d_().func_236186_a_();
        double thresholdBottomSlideSize = (double) generationShapeConfig.func_236173_d_().func_236188_b_();
        double thresholdBottomSlideOffset = (double) generationShapeConfig.func_236173_d_().func_236189_c_();
        this.noiseXZScale = NOISE_MAIN_FREQUENCY * generationShapeConfig.func_236171_b_().func_236151_a_()
                * inverseHorizontalNoiseResolution;
        this.noiseYScale = NOISE_MAIN_FREQUENCY * generationShapeConfig.func_236171_b_().func_236153_b_()
                * inverseVerticalNoiseResolution *  1.1;
        this.blendNoiseXZScale = BLEND_NOISE_RELATIVE_FREQUENCY * noiseXZScale
                / generationShapeConfig.func_236171_b_().func_236154_c_();
        this.blendNoiseYScale = BLEND_NOISE_RELATIVE_FREQUENCY * noiseYScale
                / generationShapeConfig.func_236171_b_().func_236155_d_();
        this.ratioFrequencyToSmooth = Math.sqrt(3) * 0.5 * verticalNoiseGranularity;
        this.seaLevel = chunkGeneratorSettings.func_236119_g_();

        // Seed all the noise octaves.
        newNoiseOctaves1 = new NeoNotchNoise[N_OCTAVES_PRIMARY];
        newNoiseOctaves2 = new NeoNotchNoise[N_OCTAVES_PRIMARY];
        newNoiseOctavesBlend = new NeoNotchNoise[N_OCTAVES_BLEND];
        for (int i = 0; i < Math.max(N_OCTAVES_PRIMARY, N_OCTAVES_BLEND); i++) {
            if (i < N_OCTAVES_PRIMARY) {
                newNoiseOctaves1[i] = new NeoNotchNoise(randomSeed);
                newNoiseOctaves2[i] = new NeoNotchNoise(randomSeed);
            }
            if (i < N_OCTAVES_BLEND)
                newNoiseOctavesBlend[i] = new NeoNotchNoise(randomSeed);
        }


        // for N_OCTAVES_PRIMARY = 4, this would generate
        // { PRIMARY_NOISE_MAIN_AMPLITUDE * (1.0 + 0.5 + 0.25 + 0.125), PRIMARY_NOISE_MAIN_AMPLITUDE * (0.5 + 0.25 + 0.125), PRIMARY_NOISE_MAIN_AMPLITUDE * (0.25 + 0.125),  PRIMARY_NOISE_MAIN_AMPLITUDE * (0.125) };
        primaryUncertaintyBounds = new double[N_OCTAVES_PRIMARY];
        {
            double maxValueSum = 0.0;
            for (int i = N_OCTAVES_PRIMARY - 1; i >= 0; i--) {
                maxValueSum += 1.0 / (1 << i);
                primaryUncertaintyBounds[i] = PRIMARY_NOISE_MAIN_AMPLITUDE * maxValueSum;
            }
        }

        // for N_OCTAVES_BLEND = 3, this would generate
        // { BLEND_NOISE_MAIN_AMPLITUDE * (0.5 + 0.25), BLEND_NOISE_MAIN_AMPLITUDE * (0.25) };
        if (N_OCTAVES_BLEND != 0) {
            blendUncertaintyBounds = new double[N_OCTAVES_BLEND];
            {
                double maxValueSum = 0.0;
                for (int i = N_OCTAVES_BLEND - 1; i >= 0; i--) {
                    maxValueSum += 1.0 / (1 << i);
                    blendUncertaintyBounds[i] = BLEND_NOISE_MAIN_AMPLITUDE * maxValueSum + 0.5; // Note the +0.5
                }
            }
        }

        this.scatteredBiomeBlender = new ScatteredBiomeBlender(0.3125 * inverseHorizontalNoiseResolution, horizontalNoiseGranularity, 16);
        this.generationShapeConfig = ((DimensionSettings) this.field_236080_h_.get()).func_236113_b_();

        // Pre-calculate initial part of the thresholding formula.
        double twiceInverseWorldHeight = 2.0 / generationShapeConfig.func_236169_a_();
        double thresholdMultiplier = generationShapeConfig.func_236176_g_();
        double thresholdOffset = generationShapeConfig.func_236177_h_();
        this.effectiveThresholdMultiplier = -twiceInverseWorldHeight * thresholdMultiplier;
        this.effectiveThresholdOffset = thresholdMultiplier + thresholdOffset;

        // Pre-generate the slides to be applied to the terrain threshold.
        int generationHeight = generationShapeConfig.func_236169_a_();
        thresholdSlideModifiers = new double[generationHeight];
        for (int y = 0; y < generationHeight; y++) {
            double thresholdSlideModifier = 0;
            double yb = y * this.inverseVerticalNoiseResolution;

            if (thresholdTopSlideSize > 0) {
                double tBase = ((this.noiseSizeY - yb) - thresholdTopSlideOffset);
                if (tBase < thresholdTopSlideSize) {
                    if (tBase < 0)
                        tBase = 0;
                    double inverseT = thresholdTopSlideSize / tBase;
                    thresholdSlideModifier += thresholdTopSlideTarget * (inverseT - 1);
                }
            }

            if (thresholdBottomSlideSize > 0) {
                double tBase = (yb - thresholdBottomSlideOffset);
                if (tBase < thresholdBottomSlideSize) {
                    if (tBase < 0)
                        tBase = 0;
                    double inverseT = thresholdBottomSlideSize / tBase;
                    thresholdSlideModifier += thresholdBottomSlideTarget * (inverseT - 1);
                }
            }

            thresholdSlideModifiers[y] = thresholdSlideModifier;
        }

        this.useEndIslandNoise = generationShapeConfig.func_236180_k_();
    }

    private double computeNecessaryNoiseOctaves(int worldX, int worldY, int worldZ, double startingValue) {

        // Final noise value begins with the threshold.
        double signValue = startingValue;

        // If the noise couldn't possibly turn the threshold from air to solid (or vice-versa), we can skip modulating it.
        if (signValue > primaryUncertaintyBounds[0] || signValue < -primaryUncertaintyBounds[0])
            return signValue;

        // Compute blending noise fractal. We don't always need to calculate all of the octaves.
        double blendingValue = 0.0;
        if (newNoiseOctavesBlend.length != 0) {
            int octave = 0;
            double freqXZ = blendNoiseXZScale;
            double freqY = blendNoiseYScale;
            double amp = BLEND_NOISE_MAIN_AMPLITUDE;
            do {
                blendingValue += newNoiseOctavesBlend[octave].noise3(worldX * freqXZ, worldY * freqY, worldZ * freqXZ,
                        freqY * ratioFrequencyToSmooth) * amp;
                freqXZ *= 2.0;
                freqY *= 2.0;
                amp /= 2.0;
                octave++;
            } while (octave < newNoiseOctavesBlend.length && blendingValue > -blendUncertaintyBounds[octave]
                    && blendingValue < blendUncertaintyBounds[octave]);

            if (blendingValue <= -0.5)
                blendingValue = 0;
            else if (blendingValue >= 0.5)
                blendingValue = 1;
            else {
                blendingValue += 0.5;
                blendingValue = blendingValue * blendingValue * (3 - blendingValue * 2); // Smooth
            }
        }

        // Compute regular noise fractal(s). We don't always need to calculate all of the octaves. And most of the time, we only need to calculate one of the two fractals.
        {
            int octave = 0;
            double freqXZ = noiseXZScale;
            double freqY = noiseYScale;
            double amp = PRIMARY_NOISE_MAIN_AMPLITUDE;
            do {
                if (blendingValue < 1)
                    signValue += (1 - blendingValue) * amp * newNoiseOctaves1[octave].noise3(worldX * freqXZ,
                            worldY * freqY, worldZ * freqXZ, freqY * ratioFrequencyToSmooth);
                if (blendingValue > 0)
                    signValue += blendingValue * amp * newNoiseOctaves2[octave].noise3(worldX * freqXZ, worldY * freqY,
                            worldZ * freqXZ, freqY * ratioFrequencyToSmooth);
                freqXZ *= 2.0;
                freqY *= 2.0;
                amp /= 2.0;
                octave++;
            } while (octave < newNoiseOctaves1.length && signValue > -primaryUncertaintyBounds[octave]
                    && signValue < primaryUncertaintyBounds[octave]);

        }

        return signValue;
    }

    private double[][][] getBlendedBiomeMap(int worldChunkX, int worldChunkZ) {
        double[][] biomeBlendValues1 = new double[16][16];
        double[][] biomeBlendValues2 = new double[16][16];

        if (useEndIslandNoise) {

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    double depth = MetaballEndIslandNoise.INSTANCE.getNoise(((ISimplexNoiseGenerator)(Object)field_236083_v_).getPermTable(), worldChunkX + x, worldChunkZ + z);
                    biomeBlendValues1[z][x] = depth;
                    biomeBlendValues2[z][x] = depth > 0 ? 0.25 : 1;
                }
            }

            // TODO rewrite this to more efficiently generate the entire chunk at once, a la ChunkPointGatherer.

        } else {

            //LinkedBiomeWeightMap weightMap = scatteredBiomeBlender.getBlendForChunk(0, worldChunkX, worldChunkZ, (double x, double z) -> this.getBiomeDatapointAt(x, z));
            LinkedBiomeWeightMap weightMap = CachedScatteredBiomeMagnifier.generateBiomeBlendingAndCacheMap(this.biomeSource, BiomeManager.func_235200_a_(this.field_236084_w_), worldChunkX, worldChunkZ);

            if (weightMap.getWeights() == null) {

                Biome biome = weightMap.getBiome();
                float biomeDepth = biome.getDepth();
                float biomeScale = biome.getScale();
                if (generationShapeConfig.func_236181_l_() && biomeDepth > 0.0f) {
                    biomeDepth = 1.0f + biomeDepth * 2.0f;
                    biomeScale = 1.0f + biomeScale * 4.0f;
                }
                double effectiveDepth = (biomeDepth * 0.5f - 0.125f) * 0.265625;
                double effectiveScale = 96.0 / (biomeScale * 0.9f + 0.1f); // Inverse for threshold formula

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        biomeBlendValues1[z][x] = effectiveDepth;
                        biomeBlendValues2[z][x] = effectiveScale;
                    }
                }

            } else {

                do {

                    Biome biome = weightMap.getBiome();
                    float biomeDepth = biome.getDepth();
                    float biomeScale = biome.getScale();
                    if (generationShapeConfig.func_236181_l_() && biomeDepth > 0.0f) {
                        biomeDepth = 1.0f + biomeDepth * 2.0f;
                        biomeScale = 1.0f + biomeScale * 4.0f;
                    }
                    double effectiveDepth = (biomeDepth * 0.5f - 0.125f) * 0.265625;
                    double effectiveScale = 96.0 / (biomeScale * 0.9f + 0.1f); // Inverse for threshold formula
                    effectiveDepth *= effectiveScale; // Bias blend towards biomes with less height variation

                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            double weight = weightMap.getWeights()[z * 16 + x];
                            biomeBlendValues1[z][x] += weight * effectiveDepth;
                            biomeBlendValues2[z][x] += weight * effectiveScale;
                        }
                    }

                    weightMap = weightMap.getNext();
                } while (weightMap != null);

                // Undo the division by scale
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        biomeBlendValues1[z][x] /= biomeBlendValues2[z][x];
                    }
                }
            }
        }

        return new double[][][] { biomeBlendValues1, biomeBlendValues2 };
    }

    @Inject(method = "func_236087_a_", at = @At("HEAD"), cancellable = true)
    private void injectSampleHeightmap(int x, int z, BlockState[] states, Predicate<BlockState> predicate,
            CallbackInfoReturnable<Integer> cir) {

        // Get full biome map for this chunk.
        // TODO we can optimize this by only generating the specific column.
        // This method is called infrequently though. Mainly for village placement.
        double[][][] blendedBiomeMap = getBlendedBiomeMap(x, z);

        for (int y = field_236085_x_ - 1; y >= 0; y--) {

            // Vanilla threshold formula, higher resolution

            // Initial scale and offset from configurations
            double thresholdingValue = y * effectiveThresholdMultiplier + effectiveThresholdOffset;

            // Removed compared to Vanilla: Extra 2D noise layers
            // thresholdingValue += this.getWorldCoordRandomDensityAt(x, z) * generationShapeConfig.getDensityFactor();

            // Biome scale and depth offset from this particular location.
            thresholdingValue = (thresholdingValue + blendedBiomeMap[0][z & 15][x & 15]) * blendedBiomeMap[1][z & 15][x & 15];

            // Make valleys less deep than mountains are tall
            if (thresholdingValue > 0) thresholdingValue *= 4;

            // Apply pregenerated slide modifiers from the NoiseSettings/GenerationShapeConfig. Important for The End in particular.
            thresholdingValue += this.thresholdSlideModifiers[y];

            // Apply terrain noise in full-resolution.
            // Important optimization: We don't always need to calculate all of the octaves. Outside of biome height range, we even skip them all.
            // After all, we really only need the sign of the value.
            double noiseValue = computeNecessaryNoiseOctaves(x, y, z, thresholdingValue);

            // Set block based on thresholded noise sign and vertical position.
            BlockState blockState = this.func_236086_a_(noiseValue, y);
            if (states != null) {
                states[y] = blockState;
            }

            if (predicate != null && predicate.test(blockState)) {
                cir.setReturnValue(y + 1);
                cir.cancel();
                return;
            }
        }

        cir.setReturnValue(0);
        cir.cancel();
    }

    @Shadow
    protected BlockState func_236086_a_(double density, int y) {
        throw new AssertionError();
    }

    @Shadow
    private static double func_222556_a(int x, int y, int z) {
        throw new AssertionError();
    }

    @Inject(method = "func_230352_b_", at = @At("HEAD"), cancellable = true)
    public void populateNoise(IWorld world, StructureManager accessor, IChunk chunk, CallbackInfo callbackInfo) {
        ObjectList<StructurePiece> objectlist = new ObjectArrayList<>(10);
        ObjectList<JigsawJunction> objectlist1 = new ObjectArrayList<>(32);
        ChunkPos chunkPos = chunk.getPos();
        int i = chunkPos.x;
        int j = chunkPos.z;
        int k = i << 4;
        int l = j << 4;

        // Existing structure stuff
        for (Structure<?> structure : Structure.field_236384_t_) {
            accessor.func_235011_a_(SectionPos.from(chunkPos, 0), structure).forEach((p_236089_5_) -> {
                for (StructurePiece structurepiece1 : p_236089_5_.getComponents()) {
                    if (structurepiece1.func_214810_a(chunkPos, 12)) {
                        if (structurepiece1 instanceof AbstractVillagePiece) {
                            AbstractVillagePiece abstractvillagepiece = (AbstractVillagePiece) structurepiece1;
                            JigsawPattern.PlacementBehaviour jigsawpattern$placementbehaviour = abstractvillagepiece
                                    .getJigsawPiece().getPlacementBehaviour();
                            if (jigsawpattern$placementbehaviour == JigsawPattern.PlacementBehaviour.RIGID) {
                                objectlist.add(abstractvillagepiece);
                            }

                            for (JigsawJunction jigsawjunction1 : abstractvillagepiece.getJunctions()) {
                                int l5 = jigsawjunction1.getSourceX();
                                int i6 = jigsawjunction1.getSourceZ();
                                if (l5 > k - 12 && i6 > l - 12 && l5 < k + 15 + 12 && i6 < l + 15 + 12) {
                                    objectlist1.add(jigsawjunction1);
                                }
                            }
                        } else {
                            objectlist.add(structurepiece1);
                        }
                    }
                }
            });
        }

        NoiseSettings generationShapeConfig = ((DimensionSettings) this.field_236080_h_.get())
                .func_236113_b_();

        // Get full biome map for this chunk.
        double[][][] blendedBiomeMap = getBlendedBiomeMap(k, l);

        ChunkPrimer protoChunk = (ChunkPrimer) chunk;
        Heightmap oceanFloorHeightmap = protoChunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap worldSurfaceHeightmap = protoChunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        BlockPos.Mutable blockPosMutable = new BlockPos.Mutable();
        ObjectListIterator<StructurePiece> villagePieceIterator = objectlist.iterator();
        ObjectListIterator<JigsawJunction> jigsawJunctionIterator = objectlist1.iterator();

        // 16xHx16 chunks are divided into 16x16x16 sections. Start at the top one.
        ChunkSection chunksection = protoChunk.getSection((field_236085_x_ - 1) >> 4);
        chunksection.lock();

        for (int y = field_236085_x_ - 1; y >= 0; y--) {
            int chunkSectionIndex = y >> 4;
                        int chunkSectionY = y & 15;

                        if (chunksection.getYLocation() >> 4 != chunkSectionIndex) {
                            chunksection.unlock();
                            chunksection = protoChunk.getSection(chunkSectionIndex);
                            chunksection.lock();
                        }

                        for (int z = 0; z < 16; z++) {
                            int worldZ = l | z;
                            for (int x = 0; x < 16; x++) {
                                int worldX = k | x;

                                // Vanilla threshold formula, higher resolution

                                // Initial scale and offset from configurations
                                double thresholdingValue = y * effectiveThresholdMultiplier + effectiveThresholdOffset;

                                // Removed compared to Vanilla: Extra 2D noise layers
                                // thresholdingValue += this.getWorldCoordRandomDensityAt(x, z) * generationShapeConfig.getDensityFactor();

                                // Biome scale and depth offset
                                thresholdingValue = (thresholdingValue + blendedBiomeMap[0][z][x]) * blendedBiomeMap[1][z][x];

                                // Make valleys less deep than mountains are tall
                                if (thresholdingValue > 0) thresholdingValue *= 4;

                                // Apply pregenerated slide modifiers from the NoiseSettings/GenerationShapeConfig. Important for The End in particular.
                                thresholdingValue += this.thresholdSlideModifiers[y];

                                // Apply terrain noise in full-resolution.
                                // Important optimization: We don't always need to calculate all of the octaves. Outside of biome height range, we even skip them all.
                                // After all, we really only need the sign of the value.
                                double noiseSignValue = computeNecessaryNoiseOctaves(worldX, y, worldZ, thresholdingValue);

                                // Optimization: If the threshold was too big for the noise to kick in, we should be nowhere near placing a structure.
                                if (noiseSignValue != thresholdingValue) {
                                    noiseSignValue = MathHelper.clamp(noiseSignValue / 200.0D, -1.0D, 1.0D);

                                    // Existing structure terraforming stuff
                                    int vXBound, vYBound, vZBound;
                                    for (noiseSignValue = noiseSignValue / 2.0D
                                            - noiseSignValue * noiseSignValue * noiseSignValue / 24.0D; villagePieceIterator
                                            .hasNext(); noiseSignValue += func_222556_a(vXBound, vYBound, vZBound)
                                            * 0.8D) {
                                        StructurePiece structurePiece = (StructurePiece) villagePieceIterator.next();
                                        MutableBoundingBox blockBox = structurePiece.getBoundingBox();
                                        vXBound = Math.max(0, Math.max(blockBox.minX - worldX, worldX - blockBox.maxX));
                                        vYBound = y - (blockBox.minY + (structurePiece instanceof AbstractVillagePiece
                                                ? ((AbstractVillagePiece) structurePiece).getGroundLevelDelta() : 0));
                                        vZBound = Math.max(0, Math.max(blockBox.minZ - worldZ, worldZ - blockBox.maxZ));
                                    }
                                    villagePieceIterator.back(objectlist.size());

                                    // Existing structure terraforming stuff
                                    while (jigsawJunctionIterator.hasNext()) {
                                        JigsawJunction jigsawJunction = (JigsawJunction) jigsawJunctionIterator.next();
                                        int as = worldX - jigsawJunction.getSourceX();
                                        vXBound = y - jigsawJunction.getSourceGroundY();
                                        vYBound = worldZ - jigsawJunction.getSourceZ();
                                        noiseSignValue += func_222556_a(as, vXBound, vYBound) * 0.4D;
                                    }
                                    jigsawJunctionIterator.back(objectlist1.size());
                                }

                                // Set block based on the above, and the Y coordinate.
                                BlockState blockState = this.func_236086_a_(noiseSignValue, y);

                                // Update heightmap data.
                                if (blockState != AIR) {
                                    if (blockState.getLightValue(protoChunk, blockPosMutable) != 0) {
                                        blockPosMutable.setPos(worldX, y, worldZ);
                                        protoChunk.addLightPosition(blockPosMutable);
                                    }

                                    chunksection.setBlockState(x, chunkSectionY, z, blockState, false);
                                    oceanFloorHeightmap.update(x, y, z, blockState);
                                    worldSurfaceHeightmap.update(x, y, z, blockState);
                                }
                            }
                        }
        }

        chunksection.unlock();

        callbackInfo.cancel();
    }

}
