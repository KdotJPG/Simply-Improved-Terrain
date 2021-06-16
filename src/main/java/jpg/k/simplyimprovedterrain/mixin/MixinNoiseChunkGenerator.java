package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.terrain.ChunkLocalTerrainContext;
import jpg.k.simplyimprovedterrain.terrain.SimplyImprovedTerrainNoiseSampler;
import jpg.k.simplyimprovedterrain.terrain.SimplyImprovedNoiseColumnSampler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.class_6350;
import net.minecraft.class_6357;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.BlockSource;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.StructureWeightSampler;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.ChunkSection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

import jpg.k.simplyimprovedterrain.mixinapi.ISimplexNoiseSampler;
import jpg.k.simplyimprovedterrain.util.noise.NeoNotchNoise;

@Mixin(NoiseChunkGenerator.class)
public class MixinNoiseChunkGenerator {

    private static final double NOISE_MAIN_FREQUENCY = 684.412 / 32768.0;
    private static final double BLEND_NOISE_RELATIVE_FREQUENCY = 256.0;
    private static final double PRIMARY_NOISE_MAIN_AMPLITUDE = 64.0;
    private static final double BLEND_NOISE_MAIN_AMPLITUDE = 6.4;
    private static final int N_OCTAVES_PRIMARY = 6;
    private static final int N_OCTAVES_BLEND = 3;

    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    @Shadow protected Supplier<ChunkGeneratorSettings> settings;
    @Shadow private int verticalNoiseResolution;
    @Shadow private int horizontalNoiseResolution;
    @Shadow private int noiseSizeY;
    @Shadow public long seed;
    @Shadow private int worldHeight;
    @Shadow @Final private BlockSource deepslateSource;

    private NeoNotchNoise[] newNoiseOctaves1;
    private NeoNotchNoise[] newNoiseOctaves2;
    private NeoNotchNoise[] newNoiseOctavesBlend;

    private SimplyImprovedTerrainNoiseSampler newTerrainNoiseSampler;
    private SimplyImprovedNoiseColumnSampler newNoiseColumnSampler;

    private int[] islandNoisePermutationTable;

    private BiomeSource biomeSource;
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

    private GenerationShapeConfig generationShapeConfig;

    @Inject(method = "<init>(Lnet/minecraft/world/biome/source/BiomeSource;Lnet/minecraft/world/biome/source/BiomeSource;JLjava/util/function/Supplier;)V", at = @At("TAIL"))
    private void injectConstructor(BiomeSource biomeSource, BiomeSource biomeSource2, long seed, Supplier<ChunkGeneratorSettings> settings, CallbackInfo ci) {

        this.biomeSource = biomeSource;

        ChunkGeneratorSettings chunkGeneratorSettings = (ChunkGeneratorSettings)settings.get();
        this.generationShapeConfig = chunkGeneratorSettings.getGenerationShapeConfig();

        this.inverseVerticalNoiseResolution = 1.0 / verticalNoiseResolution;
        this.inverseHorizontalNoiseResolution = 1.0 / horizontalNoiseResolution;

        // Generation configuration properties
        double thresholdTopSlideTarget = (double) generationShapeConfig.getTopSlide().getTarget();
        double thresholdTopSlideSize = (double) generationShapeConfig.getTopSlide().getSize();
        double thresholdTopSlideOffset = (double) generationShapeConfig.getTopSlide().getOffset();
        double thresholdBottomSlideTarget = (double) generationShapeConfig.getBottomSlide().getTarget();
        double thresholdBottomSlideSize = (double) generationShapeConfig.getBottomSlide().getSize();
        double thresholdBottomSlideOffset = (double) generationShapeConfig.getBottomSlide().getOffset();
        double noiseXZScale = NOISE_MAIN_FREQUENCY * generationShapeConfig.getSampling().getXZScale()
                * inverseHorizontalNoiseResolution;
        double noiseYScale = NOISE_MAIN_FREQUENCY * generationShapeConfig.getSampling().getYScale()
                * inverseVerticalNoiseResolution *  1.1;
        double blendNoiseXZScale = BLEND_NOISE_RELATIVE_FREQUENCY * noiseXZScale
                / generationShapeConfig.getSampling().getXZFactor();
        double blendNoiseYScale = BLEND_NOISE_RELATIVE_FREQUENCY * noiseYScale
                / generationShapeConfig.getSampling().getYFactor();
        double ratioFrequencyToSmooth = Math.sqrt(3) * 0.5 * verticalNoiseResolution;
        this.seaLevel = chunkGeneratorSettings.getSeaLevel();

        // Seed all the noise octaves.
        ChunkRandom chunkRandom = new ChunkRandom(seed);
        newNoiseOctaves1 = new NeoNotchNoise[N_OCTAVES_PRIMARY];
        newNoiseOctaves2 = new NeoNotchNoise[N_OCTAVES_PRIMARY];
        newNoiseOctavesBlend = new NeoNotchNoise[N_OCTAVES_BLEND];
        for (int i = 0; i < Math.max(N_OCTAVES_PRIMARY, N_OCTAVES_BLEND); i++) {
            if (i < N_OCTAVES_PRIMARY) {
                newNoiseOctaves1[i] = new NeoNotchNoise(chunkRandom);
                newNoiseOctaves2[i] = new NeoNotchNoise(chunkRandom);
            }
            if (i < N_OCTAVES_BLEND)
                newNoiseOctavesBlend[i] = new NeoNotchNoise(chunkRandom);
        }

        this.newTerrainNoiseSampler = new SimplyImprovedTerrainNoiseSampler(newNoiseOctaves1, newNoiseOctaves2, newNoiseOctavesBlend, blendNoiseXZScale, blendNoiseYScale, noiseXZScale, noiseYScale, ratioFrequencyToSmooth);
        this.newNoiseColumnSampler = new SimplyImprovedNoiseColumnSampler(biomeSource, inverseVerticalNoiseResolution, noiseSizeY, generationShapeConfig, newTerrainNoiseSampler);

        if (generationShapeConfig.hasIslandNoiseOverride()) {
            ChunkRandom noiseForIslandPermutationTable = new ChunkRandom(this.seed);
            noiseForIslandPermutationTable.skip(17292);
            this.islandNoisePermutationTable = ((ISimplexNoiseSampler)(Object)new SimplexNoiseSampler(noiseForIslandPermutationTable)).getPermTable();
        }
    }

    // TODO any updates required here. This lets villages generate properly.
    /*@Inject(method = "sampleHeightmap", at = @At("HEAD"), cancellable = true)
    private void injectSampleHeightmap(int x, int z, BlockState[] states, Predicate<BlockState> predicate,
                                       CallbackInfoReturnable<Integer> cir) {

        // Get full biome map for this chunk.
        // TODO we can optimize this by only generating the specific column.
        // This method is called infrequently though. Mainly for village placement.
        double[][][] blendedBiomeMap = getBlendedBiomeMap(x, z);

        for (int y = worldHeight - 1; y >= 0; y--) {

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
            BlockState blockState = this.getBlockState(noiseValue, y);
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
    }*/

    @Inject(method = "populateNoise(Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;II)Lnet/minecraft/world/chunk/Chunk;", at = @At("HEAD"), cancellable = true)
    public void populateNewNoise(StructureAccessor accessor, Chunk chunk, int yGridBottomOffset, int yGridHeight, CallbackInfoReturnable<Chunk> cir) {
        Heightmap heightmap = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap heightmap2 = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        ChunkPos chunkPos = chunk.getPos();
        int chunkWorldX = chunkPos.getStartX();
        int chunkWorldZ = chunkPos.getStartZ();
        StructureWeightSampler structureWeightSampler = new StructureWeightSampler(accessor, chunk);
        class_6350 aquiferSampler = this.method_36386(yGridBottomOffset, yGridHeight, chunkPos);
        //DoubleFunction<BlockSource> oreVeinSampler = this.method_36387(i, chunkPos, consumer);
        //DoubleFunction<class_6357> thisMustBeNoodleCaves = this.method_36462(i, chunkPos, consumer);
        ChunkLocalTerrainContext chunkContext = new ChunkLocalTerrainContext(chunkWorldX, chunkWorldZ, this.seed, this.biomeSource, this.generationShapeConfig, this.islandNoisePermutationTable);
        SimplyImprovedNoiseColumnSampler.ColumnSamplingContext columnContext = this.newNoiseColumnSampler.columnSamplingContextThreadLocal();
        columnContext.setChunkLocalTerrainContext(chunkContext);

        int yTop = yGridHeight * this.horizontalNoiseResolution - 1;
        int yBottomOffset = yGridBottomOffset * this.horizontalNoiseResolution;

        BlockPos.Mutable currentBlockPos = new BlockPos.Mutable();
        for (int z = 0; z < 16; z++) {
            int worldZ = chunkWorldZ | z;
            for (int x = 0; x < 16; x++) {
                int worldX = chunkWorldX | x;

                // Sample for this column now.
                columnContext.setXZ(x, z);

                ChunkSection chunkSection = chunk.getSection(chunk.countVerticalSections() - 1);
                for (int y = yTop; y >= 0; y--) {
                    int chunkSectionIndex = chunk.getSectionIndex(y);
                    if (chunk.getSectionIndex(chunkSection.getYOffset()) != chunkSectionIndex) {
                        chunkSection = chunk.getSection(chunkSectionIndex);
                    }

                    // Computes only the necessary noise layers to resolve what this block should be.
                    double noiseSignValue = columnContext.sampleNoiseSign(y + yBottomOffset);

                    // TODO replace (BlockSource)this.deepslateSource, (class_6357)class_6357.field_33652 with completed samplers for this mod
                    BlockState blockState = this.getBlockState(structureWeightSampler, aquiferSampler, (BlockSource)this.deepslateSource, (class_6357)class_6357.field_33652, x, y, z, noiseSignValue);

                    if (blockState != AIR) {
                        if (blockState.getLuminance() != 0 && chunk instanceof ProtoChunk) {
                            currentBlockPos.set(worldX, y, worldZ);
                            ((ProtoChunk)chunk).addLightSource(currentBlockPos);
                        }

                        chunkSection.setBlockState(x, y & 15, z, blockState, false);
                        heightmap.trackUpdate(x, y, z, blockState);
                        heightmap2.trackUpdate(x, y, z, blockState);
                        if (aquiferSampler.needsFluidTick() && !blockState.getFluidState().isEmpty()) {
                            currentBlockPos.set(worldX, y, worldZ);
                            chunk.getFluidTickScheduler().schedule(currentBlockPos, blockState.getFluidState().getFluid(), 0);
                        }
                    }

                }

            }
        }

        cir.setReturnValue(chunk);
    }

    @Shadow
    protected BlockState getBlockState(StructureWeightSampler structures, class_6350 aquiferSampler, BlockSource blockInterpolator, class_6357 arg, int i, int j, int k, double d) {
        throw new AssertionError();
    }

    @Shadow
    private class_6350 method_36386(int i, int j, ChunkPos chunkPos) {
        throw new AssertionError();
    }

}
