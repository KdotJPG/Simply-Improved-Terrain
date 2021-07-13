package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.mixinapi.ISimplexNoiseSampler;
import jpg.k.simplyimprovedterrain.terrain.ChunkLocalTerrainContext;
import jpg.k.simplyimprovedterrain.terrain.SimplyImprovedNoiseColumnSampler;
import jpg.k.simplyimprovedterrain.terrain.SimplyImprovedTerrainNoiseSampler;
import jpg.k.simplyimprovedterrain.util.noise.NeoNotchNoise;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.BlockSource;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.StructureWeightSampler;
import net.minecraft.world.gen.chunk.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
        chunkRandom.skip(18340); // 16+16+8+4+16 octaves, * 262 RNG skips per octave, to bring us to generate the same noise we had in 1.16
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

    @Inject(method = "sampleHeightmap", at = @At("HEAD"), cancellable = true)
    private void sampleHeightmap(int x, int z, @Nullable BlockState[] states, @Nullable Predicate<BlockState> predicate, int vanillaNoiseGridMinY, int vanillaNoiseGridSizeY,
                                        CallbackInfoReturnable<OptionalInt> cir) {
        int chunkX = ChunkSectionPos.getSectionCoord(x);
        int chunkZ = ChunkSectionPos.getSectionCoord(z);
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        int chunkWorldX = chunkPos.getStartX();
        int chunkWorldZ = chunkPos.getStartZ();
        AquiferSampler aquiferSampler = this.createBlockSampler(vanillaNoiseGridMinY, vanillaNoiseGridSizeY, chunkPos);

        // Setup to generate this column.
        ChunkLocalTerrainContext chunkContext = ChunkLocalTerrainContext.Create(chunkWorldX, chunkWorldZ, this.seed, this.biomeSource, this.generationShapeConfig, this.islandNoisePermutationTable);
        SimplyImprovedNoiseColumnSampler.ColumnSamplingContext columnContext = this.newNoiseColumnSampler.columnSamplingContextThreadLocal();
        columnContext.setChunkFields(chunkContext, StructureWeightSampler.INSTANCE);
        columnContext.setXZ(x & 15, z & 15);

        int yTop = vanillaNoiseGridSizeY * this.verticalNoiseResolution - 1;
        int yBottomOffset = vanillaNoiseGridMinY * this.verticalNoiseResolution;

        for (int y = yTop; y >= 0; y--) {
            int yy = y + yBottomOffset;

            // Computes only the necessary noise layers to resolve what this block should be.
            double noiseSignValue = columnContext.sampleNoiseSign(yy);

            BlockState blockState = this.getBlockState(StructureWeightSampler.INSTANCE, aquiferSampler, (BlockSource) this.deepslateSource, WeightSampler.DEFAULT, x, yy, z, noiseSignValue);
            if (states != null) {
                states[y] = blockState;
            }

            if (predicate != null && predicate.test(blockState)) {
                cir.setReturnValue(OptionalInt.of(yy + 1));
                return;
            }
        }
        cir.cancel();
    }

    @Inject(method = "populateNoise(Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;II)Lnet/minecraft/world/chunk/Chunk;", at = @At("HEAD"), cancellable = true)
    public void populateNewNoise(StructureAccessor accessor, Chunk chunk, int vanillaNoiseGridMinY, int vanillaNoiseGridSizeY, CallbackInfoReturnable<Chunk> cir) {
        Heightmap heightmap = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap heightmap2 = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        ChunkPos chunkPos = chunk.getPos();
        int chunkWorldX = chunkPos.getStartX();
        int chunkWorldZ = chunkPos.getStartZ();
        StructureWeightSampler structureWeightSampler = new StructureWeightSampler(accessor, chunk);
        AquiferSampler aquiferSampler = this.createBlockSampler(vanillaNoiseGridMinY, vanillaNoiseGridSizeY, chunkPos);
        //DoubleFunction<BlockSource> oreVeinSampler = this.method_36387(i, chunkPos, consumer);
        //DoubleFunction<class_6357> thisMustBeNoodleCaves = this.method_36462(i, chunkPos, consumer);
        ChunkLocalTerrainContext chunkContext = ChunkLocalTerrainContext.Create(chunkWorldX, chunkWorldZ, this.seed, this.biomeSource, this.generationShapeConfig, this.islandNoisePermutationTable);
        SimplyImprovedNoiseColumnSampler.ColumnSamplingContext columnContext = this.newNoiseColumnSampler.columnSamplingContextThreadLocal();
        columnContext.setChunkFields(chunkContext, structureWeightSampler);

        int yTop = vanillaNoiseGridSizeY * this.verticalNoiseResolution - 1;
        int yBottomOffset = vanillaNoiseGridMinY * this.verticalNoiseResolution;

        BlockPos.Mutable currentBlockPos = new BlockPos.Mutable();

        // This loop works with the nested loop for the chunk section Y range.
        // The "ys = y" update statement updates "ys" with the beginning of the next chunk section.
        for (int y = yTop + yBottomOffset, ys = y; ys >= yBottomOffset; ys = y) {
            int chunkSectionIndex = chunk.getSectionIndex(ys);
            ChunkSection chunkSection = chunk.getSection(chunkSectionIndex);
            int chunkSectionYBottom = chunkSection.getYOffset();

            for (int z = 0; z < 16; z++) {
                int worldZ = chunkWorldZ | z;
                for (int x = 0; x < 16; x++) {
                    int worldX = chunkWorldX | x;

                    // Sample for this column now.
                    columnContext.setXZ(x, z);

                    // Vertical range, just in this chunk section
                    for (y = ys; y >= chunkSectionYBottom; y--) {

                        // Computes only the necessary noise layers to resolve what this block should be.
                        double noiseSignValue = columnContext.sampleNoiseSign(y);

                        // StructureWeightSampler.INSTANCE used in place of actual sampler, because it's handled in columnContext.sampleNoiseSign(y);
                        // TODO replace (BlockSource)this.deepslateSource, (class_6357)class_6357.field_33652 with completed samplers for this mod
                        BlockState blockState = this.getBlockState(StructureWeightSampler.INSTANCE, aquiferSampler, (BlockSource) this.deepslateSource, WeightSampler.DEFAULT, worldX, y, worldZ, noiseSignValue);

                        if (blockState != AIR) {
                            if (blockState.getLuminance() != 0 && chunk instanceof ProtoChunk) {
                                currentBlockPos.set(worldX, y, worldZ);
                                ((ProtoChunk) chunk).addLightSource(currentBlockPos);
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
        }

        cir.setReturnValue(chunk);
    }

    @Shadow
    protected BlockState getBlockState(StructureWeightSampler structures, AquiferSampler aquiferSampler, BlockSource blockInterpolator, WeightSampler weightSampler, int i, int j, int k, double d) {
        throw new AssertionError();
    }

    @Shadow
    private AquiferSampler createBlockSampler(int startY, int deltaY, ChunkPos chunkPos) {
        throw new AssertionError();
    }

}
