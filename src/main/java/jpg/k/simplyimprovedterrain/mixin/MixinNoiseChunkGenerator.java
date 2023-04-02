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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;
import java.util.function.Supplier;

import jpg.k.simplyimprovedterrain.util.noise.MetaballEndIslandNoise;
import jpg.k.simplyimprovedterrain.util.noise.DomainRotatedShelfNoise;
import jpg.k.simplyimprovedterrain.mixinapi.ISimplexNoiseGenerator;
import jpg.k.simplyimprovedterrain.biome.CachedScatteredBiomeMagnifier;
import jpg.k.simplyimprovedterrain.biome.blending.LinkedBiomeWeightMap;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunkGenerator.class)
public class MixinNoiseChunkGenerator {

    private static final double PRIMARY_NOISE_MAIN_AMPLITUDE = 128.0;
    private static final double BLEND_NOISE_MAIN_AMPLITUDE = 6.4;
    private static final double NOISE_RELATIVE_VERTICAL_FREQUENCY = 1.1;
    private static final double NOISE_SHELF_SMOOTHING_FACTOR = 0.95;

    private static final int MAX_OCTAVE_COUNT_PRIMARY = 7;
    private static final int MAX_OCTAVE_COUNT_BLEND = 4;
    private static final double OCTAVE_COUNT_FREQUENCY_THRESHOLD_MODIFIER = 0.95;

    private static final double NOISE_MAIN_FREQUENCY = 684.412 / 32768.0;
    private static final double BLEND_NOISE_RELATIVE_FREQUENCY = 256.0;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    @Shadow
    protected Supplier<DimensionSettings> settings;

    @Shadow
    private int chunkHeight;

    @Shadow
    private int chunkWidth;

    @Shadow
    private int chunkCountY;

    @Shadow
    public long seed;

    @Shadow
    protected SharedSeedRandom random;

    @Shadow
    private int height;

    @Shadow
    private SimplexNoiseGenerator islandNoise;

    private DomainRotatedShelfNoise[] newNoiseOctaves1;
    private DomainRotatedShelfNoise[] newNoiseOctaves2;
    private DomainRotatedShelfNoise[] newNoiseOctavesBlend;
    private double[] primaryAmplitudes;
    private double[] blendAmplitudes;
    private double[] primaryUncertaintyBounds;
    private double[] blendUncertaintyBounds;

    private BiomeProvider biomeSource;
    private double noiseXZScale;
    private double noiseYScale;
    private double blendNoiseXZScale;
    private double blendNoiseYScale;
    private double ratioFrequencyToSmooth;
    private double effectiveThresholdMultiplier;
    private double effectiveThresholdOffset;
    private double[] thresholdSlideModifiers;

    private NoiseSettings noiseSettings;

    boolean useEndIslandNoise;

    @Inject(method = "<init>(Lnet/minecraft/world/biome/provider/BiomeProvider;Lnet/minecraft/world/biome/provider/BiomeProvider;JLjava/util/function/Supplier;)V", at = @At("TAIL"))
    private void injectConstructor(BiomeProvider biomeSource, BiomeProvider biomeSource2, long worldSeed,
            Supplier<DimensionSettings> supplier, CallbackInfo cir) {

        this.biomeSource = biomeSource;

        DimensionSettings dimensionSettings = settings.get();
        NoiseSettings noiseSettings = dimensionSettings.noiseSettings();
        this.noiseSettings = noiseSettings;

        // Direct noise frequencies (+ shelf smoothing)
        double inverseVanillaLerpSizeVertical = 1.0 / chunkHeight;
        double inverseVanillaLerpSizeHorizontal = 1.0 / chunkWidth;
        this.noiseXZScale = NOISE_MAIN_FREQUENCY * noiseSettings.noiseSamplingSettings().xzScale()
                * inverseVanillaLerpSizeHorizontal;
        this.noiseYScale = NOISE_MAIN_FREQUENCY * NOISE_RELATIVE_VERTICAL_FREQUENCY * noiseSettings.noiseSamplingSettings().yScale()
                * inverseVanillaLerpSizeVertical;
        this.blendNoiseXZScale = BLEND_NOISE_RELATIVE_FREQUENCY * noiseXZScale
                / noiseSettings.noiseSamplingSettings().xzFactor();
        this.blendNoiseYScale = BLEND_NOISE_RELATIVE_FREQUENCY * noiseYScale
                / noiseSettings.noiseSamplingSettings().yFactor();
        this.ratioFrequencyToSmooth = NOISE_SHELF_SMOOTHING_FACTOR * chunkHeight;

        // Decide how many octaves each + a fade-in for the highest frequency octaves.
        // This works to (smoothly) filter out frequencies that vanilla trilerp would have masked away anyway.
        int octaveCountPrimary;
        int octaveCountBlend;
        double highestOctaveAmplitudeRescalePrimary;
        double highestOctaveAmplitudeRescaleBlend;
        {
            double effectiveInverseLerpSizeSquaredTripled = inverseVanillaLerpSizeHorizontal * inverseVanillaLerpSizeHorizontal * 2 + inverseVanillaLerpSizeVertical * inverseVanillaLerpSizeVertical;
            double effectiveFrequencyPrimarySquaredTripled = this.noiseXZScale * this.noiseXZScale * 2 + this.noiseYScale * this.noiseYScale;
            double effectiveFrequencyBlendSquaredTripled = this.blendNoiseXZScale * this.blendNoiseXZScale * 2 + this.blendNoiseYScale * this.blendNoiseYScale;
            double smoothOctaveCountPrimary = Math.log(effectiveInverseLerpSizeSquaredTripled / effectiveFrequencyPrimarySquaredTripled) / Math.log(2 * 2) + 1.0;
            double smoothOctaveCountBlend = Math.log(effectiveInverseLerpSizeSquaredTripled / effectiveFrequencyBlendSquaredTripled) / Math.log(2 * 2) + 1.0;
            smoothOctaveCountPrimary = Math.min(MAX_OCTAVE_COUNT_PRIMARY, smoothOctaveCountPrimary);
            smoothOctaveCountBlend = Math.min(MAX_OCTAVE_COUNT_BLEND, smoothOctaveCountBlend);
            octaveCountPrimary = (int)smoothOctaveCountPrimary;
            octaveCountBlend = (int)smoothOctaveCountBlend;
            highestOctaveAmplitudeRescalePrimary = smoothOctaveCountPrimary - octaveCountPrimary;
            highestOctaveAmplitudeRescaleBlend = smoothOctaveCountBlend - octaveCountBlend;
        }

        // Seed all the noise octaves.
        newNoiseOctaves1 = new DomainRotatedShelfNoise[octaveCountPrimary];
        newNoiseOctaves2 = new DomainRotatedShelfNoise[octaveCountPrimary];
        newNoiseOctavesBlend = new DomainRotatedShelfNoise[octaveCountBlend];
        for (int i = 0; i < Math.max(octaveCountPrimary, octaveCountBlend); i++) {
            DomainRotatedShelfNoise octave1 = new DomainRotatedShelfNoise(random);
            DomainRotatedShelfNoise octave2 = new DomainRotatedShelfNoise(random);
            DomainRotatedShelfNoise octaveBlend = new DomainRotatedShelfNoise(random);
            if (i < octaveCountPrimary) {
                newNoiseOctaves1[i] = octave1;
                newNoiseOctaves2[i] = octave2;
            }
            if (i < octaveCountBlend)
                newNoiseOctavesBlend[i] = octaveBlend;
        }

        // Pre-generate noise amplitudes into arrays to consolidate highest octave amplitude rescaling into one place
        primaryAmplitudes = new double[octaveCountPrimary];
        {
            double amplitude = PRIMARY_NOISE_MAIN_AMPLITUDE;
            for (int i = 0; i < octaveCountPrimary; i++) {
                double rescale = (i == octaveCountPrimary - 1) ? highestOctaveAmplitudeRescalePrimary : 1.0;
                primaryAmplitudes[i] = amplitude * rescale;
                amplitude /= 2;
            }
        }
        blendAmplitudes = new double[octaveCountBlend];
        {
            double amplitude = BLEND_NOISE_MAIN_AMPLITUDE;
            for (int i = 0; i < octaveCountBlend; i++) {
                double rescale = (i == octaveCountBlend - 1) ? highestOctaveAmplitudeRescaleBlend : 1.0;
                blendAmplitudes[i] = amplitude * rescale;
                amplitude /= 2;
            }
        }

        // These array values tell us how much noise variation is left at each step of the octave summation.
        // We will check against these to skip early.
        primaryUncertaintyBounds = new double[octaveCountPrimary];
        {
            double maxValueSum = 0.0;
            for (int i = octaveCountPrimary - 1; i >= 0; i--) {
                maxValueSum += primaryAmplitudes[i];
                primaryUncertaintyBounds[i] = maxValueSum;
            }
        }
        blendUncertaintyBounds = new double[octaveCountBlend];
        {
            double maxValueSum = 0.0;
            for (int i = octaveCountBlend - 1; i >= 0; i--) {
                maxValueSum += blendAmplitudes[i];
                // The + 0.5 defines a -0.5 to +0.5 range where we need the fully qualified noise value.
                blendUncertaintyBounds[i] = maxValueSum + 0.5;
            }
        }

        // Pre-calculate initial part of the thresholding formula.
        double twiceInverseWorldHeight = 2.0 / noiseSettings.height();
        double thresholdMultiplier = noiseSettings.densityFactor();
        double thresholdOffset = noiseSettings.densityOffset();
        this.effectiveThresholdMultiplier = -twiceInverseWorldHeight * thresholdMultiplier;
        this.effectiveThresholdOffset = thresholdMultiplier + thresholdOffset;

        // Pre-generate the slides to be applied to the terrain threshold.
        double thresholdTopSlideTarget = noiseSettings.topSlideSettings().target();
        double thresholdTopSlideSize = noiseSettings.topSlideSettings().size();
        double thresholdTopSlideOffset = noiseSettings.topSlideSettings().offset();
        double thresholdBottomSlideTarget = noiseSettings.bottomSlideSettings().target();
        double thresholdBottomSlideSize = noiseSettings.bottomSlideSettings().size();
        double thresholdBottomSlideOffset = noiseSettings.bottomSlideSettings().offset();
        int generationHeight = noiseSettings.height();
        thresholdSlideModifiers = new double[generationHeight];
        for (int y = 0; y < generationHeight; y++) {
            double thresholdSlideModifier = 0;
            double yb = y * inverseVanillaLerpSizeVertical;

            if (thresholdTopSlideSize > 0) {
                double tBase = ((this.chunkCountY - yb) - thresholdTopSlideOffset);
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

        // We'll use this to quickly check where we need to get our terrain shape parameters.
        this.useEndIslandNoise = noiseSettings.islandNoiseOverride();
    }

    // Dynamic noise layer skipping!
    @Unique
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
            do {
                blendingValue += newNoiseOctavesBlend[octave].noise3(worldX * freqXZ, worldY * freqY, worldZ * freqXZ,
                        freqY * ratioFrequencyToSmooth) * blendAmplitudes[octave];
                freqXZ *= 2.0;
                freqY *= 2.0;
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

        // Compute regular noise fractal(s). We don't always need to evaluate every octave.
        // And most of the time, we only need to calculate one of the two fractals.
        {
            int octave = 0;
            double freqXZ = noiseXZScale;
            double freqY = noiseYScale;
            do {
                if (blendingValue < 1)
                    signValue += (1 - blendingValue) * primaryAmplitudes[octave] * newNoiseOctaves1[octave].noise3(worldX * freqXZ,
                            worldY * freqY, worldZ * freqXZ, freqY * ratioFrequencyToSmooth);
                if (blendingValue > 0)
                    signValue += blendingValue * primaryAmplitudes[octave] * newNoiseOctaves2[octave].noise3(worldX * freqXZ, worldY * freqY,
                            worldZ * freqXZ, freqY * ratioFrequencyToSmooth);
                freqXZ *= 2.0;
                freqY *= 2.0;
                octave++;
            } while (octave < newNoiseOctaves1.length && signValue > -primaryUncertaintyBounds[octave]
                    && signValue < primaryUncertaintyBounds[octave]);

        }

        return signValue;
    }

    @Unique
    private double[] getBlendedBiomeMap(int worldChunkX, int worldChunkZ) {
        double[] biomeBlendValuesCombined = new double[16 * 16 * 2];

        if (useEndIslandNoise) {

            for (int z = 0, index = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    double depth = MetaballEndIslandNoise.INSTANCE.getNoise(
                            ((ISimplexNoiseGenerator)islandNoise).getPermTable(),
                            worldChunkX + x, worldChunkZ + z);
                    biomeBlendValuesCombined[index++] = depth;
                    biomeBlendValuesCombined[index++] = depth > 0 ? 0.25 : 1;
                }
            }

            // Note: this could be rewritten to efficiently generate for the entire chunk, a la ChunkPointGatherer.

        } else {

            LinkedBiomeWeightMap weightMap = CachedScatteredBiomeMagnifier.generateBiomeBlendingAndCacheMap(
                    this.biomeSource, BiomeManager.obfuscateSeed(this.seed), worldChunkX, worldChunkZ);

            if (weightMap.getWeights() == null) {

                Biome biome = weightMap.getBiome();
                float biomeDepth = biome.getDepth();
                float biomeScale = biome.getScale();
                if (noiseSettings.isAmplified() && biomeDepth > 0.0f) {
                    biomeDepth = 1.0f + biomeDepth * 2.0f;
                    biomeScale = 1.0f + biomeScale * 4.0f;
                }
                double effectiveDepth = (biomeDepth * 0.5f - 0.125f) * 0.265625;
                double effectiveScale = 96.0 / (biomeScale * 0.9f + 0.1f); // Inverse for threshold formula

                for (int i = 0; i < 16 * 16 * 2; i += 2) {
                    biomeBlendValuesCombined[i + 0] = effectiveDepth;
                    biomeBlendValuesCombined[i + 1] = effectiveScale;
                }

            } else {

                do {

                    Biome biome = weightMap.getBiome();
                    float biomeDepth = biome.getDepth();
                    float biomeScale = biome.getScale();
                    if (noiseSettings.isAmplified() && biomeDepth > 0.0f) {
                        biomeDepth = 1.0f + biomeDepth * 2.0f;
                        biomeScale = 1.0f + biomeScale * 4.0f;
                    }
                    double effectiveDepth = (biomeDepth * 0.5f - 0.125f) * 0.265625;
                    double effectiveScale = 96.0 / (biomeScale * 0.9f + 0.1f); // Inverse for threshold formula
                    effectiveDepth *= effectiveScale; // Bias blend towards biomes with less height variation

                    for (int i = 0; i < 16 * 16; i++) {
                        double weight = weightMap.getWeights()[i];
                        biomeBlendValuesCombined[i * 2 + 0] += weight * effectiveDepth;
                        biomeBlendValuesCombined[i * 2 + 1] += weight * effectiveScale;
                    }

                    weightMap = weightMap.getNext();
                } while (weightMap != null);

                // Undo the division by scale
                for (int i = 0; i < 16 * 16 * 2; i += 2) {
                    biomeBlendValuesCombined[i + 0] /= biomeBlendValuesCombined[i + 1];
                }
            }
        }

        return biomeBlendValuesCombined;
    }

    @Inject(method = "iterateNoiseColumn", at = @At("HEAD"), cancellable = true)
    private void injectIterateNoiseColumn(int x, int z, BlockState[] states, Predicate<BlockState> predicate, CallbackInfoReturnable<Integer> cir) {

        // Get full biome map for this chunk.
        // TODO we can optimize this by only generating the specific column.
        // This method is called infrequently though. Mainly for structure placement.
        double[] biomeBlendValuesCombined = getBlendedBiomeMap(x, z);
        int biomeBlendValuesBaseIndex = (((z & 15) << 4) | (x & 15)) << 1;
        double effectiveDepth = biomeBlendValuesCombined[biomeBlendValuesBaseIndex | 0];
        double effectiveScale = biomeBlendValuesCombined[biomeBlendValuesBaseIndex | 1];

        for (int y = height - 1; y >= 0; y--) {

            // Vanilla threshold formula, higher resolution

            // Initial scale and offset from configurations
            double thresholdingValue = y * effectiveThresholdMultiplier + effectiveThresholdOffset;

            // Removed compared to Vanilla: Extra 2D noise layers
            // thresholdingValue += this.getWorldCoordRandomDensityAt(x, z) * generationShapeConfig.getDensityFactor();

            // Biome scale and depth offset from this particular location.
            thresholdingValue = (thresholdingValue + effectiveDepth) * effectiveScale;

            // Make valleys less deep than mountains are tall
            if (thresholdingValue > 0) thresholdingValue *= 4;

            // Apply pregenerated slide modifiers from the NoiseSettings/GenerationShapeConfig. Important for The End in particular.
            thresholdingValue += this.thresholdSlideModifiers[y];

            // Apply terrain noise in full-resolution.
            // Important optimization: We don't always need to calculate all of the octaves. Outside of biome height range, we even skip them all.
            // After all, we really only need the sign of the value.
            double noiseValue = computeNecessaryNoiseOctaves(x, y, z, thresholdingValue);

            // Set block based on thresholded noise sign and vertical position.
            BlockState blockState = this.generateBaseState(noiseValue, y);
            if (states != null && states.length > y) {
                states[y] = blockState;
            }

            if (predicate != null && predicate.test(blockState)) {
                cir.setReturnValue(y + 1);
                return;
            }
        }

        cir.setReturnValue(0);
    }

    @Shadow
    protected BlockState generateBaseState(double density, int y) {
        throw new AssertionError();
    }

    @Shadow
    private static double getContribution(int x, int y, int z) {
        throw new AssertionError();
    }

    @Inject(method = "fillFromNoise", at = @At("HEAD"), cancellable = true)
    public void injectFillFromNoise(IWorld world, StructureManager accessor, IChunk chunk, CallbackInfo ci) {
        ObjectList<StructurePiece> structurePieces = new ObjectArrayList<>(10);
        ObjectList<JigsawJunction> jigsawJunctions = new ObjectArrayList<>(32);
        ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;
        int chunkWorldX = chunkX << 4;
        int chunkWorldZ = chunkZ << 4;

        for (Structure<?> structure : Structure.NOISE_AFFECTING_FEATURES) {
            accessor.startsForFeature(SectionPos.of(chunkPos, 0), structure).forEach((p_236089_5_) -> {
                for (StructurePiece structurepiece1 : p_236089_5_.getPieces()) {
                    if (structurepiece1.isCloseToChunk(chunkPos, 12)) {
                        if (structurepiece1 instanceof AbstractVillagePiece) {
                            AbstractVillagePiece abstractvillagepiece = (AbstractVillagePiece)structurepiece1;
                            JigsawPattern.PlacementBehaviour jigsawpattern$placementbehaviour = abstractvillagepiece.getElement().getProjection();
                            if (jigsawpattern$placementbehaviour == JigsawPattern.PlacementBehaviour.RIGID) {
                                structurePieces.add(abstractvillagepiece);
                            }

                            for (JigsawJunction jigsawjunction1 : abstractvillagepiece.getJunctions()) {
                                int l5 = jigsawjunction1.getSourceX();
                                int i6 = jigsawjunction1.getSourceZ();
                                if (l5 > chunkWorldX - 12 && i6 > chunkWorldZ - 12 && l5 < chunkWorldX + 15 + 12 && i6 < chunkWorldZ + 15 + 12) {
                                    jigsawJunctions.add(jigsawjunction1);
                                }
                            }
                        } else {
                            structurePieces.add(structurepiece1);
                        }
                    }
                }

            });
        }

        // Get full biome map for this chunk.
        double[] biomeBlendValuesCombined = getBlendedBiomeMap(chunkWorldX, chunkWorldZ);

        // We'll be using these just like Vanilla does.
        ChunkPrimer protoChunk = (ChunkPrimer) chunk;
        Heightmap oceanFloorHeightmap = protoChunk.getOrCreateHeightmapUnprimed(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap worldSurfaceHeightmap = protoChunk.getOrCreateHeightmapUnprimed(Heightmap.Type.WORLD_SURFACE_WG);
        BlockPos.Mutable blockPosMutable = new BlockPos.Mutable();
        ObjectListIterator<StructurePiece> villagePieceIterator = structurePieces.iterator();
        ObjectListIterator<JigsawJunction> jigsawJunctionIterator = jigsawJunctions.iterator();

        // 16xHx16 chunks are divided into 16x16x16 sections. Start at the top one.
        ChunkSection chunksection = protoChunk.getOrCreateSection((height - 1) >> 4);
        chunksection.acquire();

        // Chunk Y loop
        for (int y = height - 1; y >= 0; y--) {
            int chunkSectionIndex = y >> 4;
            int chunkSectionY = y & 15;

            // Chunk section transition
            if (chunksection.bottomBlockY() >> 4 != chunkSectionIndex) {
                chunksection.release();
                chunksection = protoChunk.getOrCreateSection(chunkSectionIndex);
                chunksection.acquire();
            }

            // Chunk XZ loop
            for (int z = 0; z < 16; z++) {
                int worldZ = chunkWorldZ | z;
                for (int x = 0; x < 16; x++) {
                    int worldX = chunkWorldX | x;

                    // Vanilla threshold formula, higher resolution

                    // Initial scale and offset from configurations
                    double thresholdingValue = y * effectiveThresholdMultiplier + effectiveThresholdOffset;

                    // Removed compared to Vanilla: Extra 2D noise layers
                    // thresholdingValue += this.getWorldCoordRandomDensityAt(x, z) * generationShapeConfig.getDensityFactor();

                    // Biome scale and depth offset
                    int biomeBlendValuesBaseIndex = ((z << 4) | x) << 1;
                    double effectiveDepth = biomeBlendValuesCombined[biomeBlendValuesBaseIndex | 0];
                    double effectiveScale = biomeBlendValuesCombined[biomeBlendValuesBaseIndex | 1];
                    thresholdingValue = (thresholdingValue + effectiveDepth) * effectiveScale;

                    // Make valleys less deep than mountains are tall
                    if (thresholdingValue > 0) thresholdingValue *= 4;

                    // Apply pregenerated slide modifiers from the NoiseSettings/GenerationShapeConfig.
                    // Important for The End in particular.
                    thresholdingValue += this.thresholdSlideModifiers[y];

                    // Apply terrain noise in full-resolution.
                    // Important optimization: We don't always need to calculate all of the octaves.
                    //                         Outside of biome height range, we even skip them all.
                    // After all, we really only need the sign of the value.
                    double noiseSignValue = computeNecessaryNoiseOctaves(worldX, y, worldZ, thresholdingValue);

                    // Optimization: If the threshold was too big for the noise to kick in, we should be nowhere near placing a structure.
                    // Note: It would be technically ideal to apply this before noise, so the skipping can adapt to it.
                    //       But the clamp makes things tricker and this doesn't really create any issues in practice.
                    if (noiseSignValue != thresholdingValue) {
                        noiseSignValue = MathHelper.clamp(noiseSignValue / 200.0, -1.0, 1.0);

                        // Existing structure terraforming stuff
                        int vXBound, vYBound, vZBound;
                        for (noiseSignValue = noiseSignValue / 2.0
                                - noiseSignValue * noiseSignValue * noiseSignValue / 24.0; villagePieceIterator
                                .hasNext(); noiseSignValue += getContribution(vXBound, vYBound, vZBound)
                                * 0.8) {
                            StructurePiece structurePiece = villagePieceIterator.next();
                            MutableBoundingBox blockBox = structurePiece.getBoundingBox();
                            vXBound = Math.max(0, Math.max(blockBox.x0 - worldX, worldX - blockBox.x1));
                            vYBound = y - (blockBox.y0 + (structurePiece instanceof AbstractVillagePiece
                                    ? ((AbstractVillagePiece) structurePiece).getGroundLevelDelta() : 0));
                            vZBound = Math.max(0, Math.max(blockBox.z0 - worldZ, worldZ - blockBox.z1));
                        }
                        villagePieceIterator.back(structurePieces.size());

                        // Existing structure terraforming stuff
                        while (jigsawJunctionIterator.hasNext()) {
                            JigsawJunction jigsawJunction = (JigsawJunction) jigsawJunctionIterator.next();
                            int as = worldX - jigsawJunction.getSourceX();
                            vXBound = y - jigsawJunction.getSourceGroundY();
                            vYBound = worldZ - jigsawJunction.getSourceZ();
                            noiseSignValue += getContribution(as, vXBound, vYBound) * 0.4;
                        }
                        jigsawJunctionIterator.back(jigsawJunctions.size());
                    }

                    // Set block based on the above, and the Y coordinate.
                    BlockState blockState = this.generateBaseState(noiseSignValue, y);

                    // Update heightmap data.
                    if (blockState != AIR) {
                        if (blockState.getLightValue(protoChunk, blockPosMutable) != 0) {
                            blockPosMutable.set(worldX, y, worldZ);
                            protoChunk.addLight(blockPosMutable);
                        }

                        chunksection.setBlockState(x, chunkSectionY, z, blockState, false);
                        oceanFloorHeightmap.update(x, y, z, blockState);
                        worldSurfaceHeightmap.update(x, y, z, blockState);
                    }
                }
            }
        }

        chunksection.release();

        ci.cancel();
    }

}
