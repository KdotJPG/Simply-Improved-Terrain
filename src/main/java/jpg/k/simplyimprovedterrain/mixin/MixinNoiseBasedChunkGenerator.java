package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.biome.FiddledBiomeResolver;
import jpg.k.simplyimprovedterrain.biome.WrappedFiddledBiomeResolver;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinNoiseChunk;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;
import java.util.function.Predicate;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGenerator {

    @Shadow @Final private static BlockState AIR;
    @Shadow @Final protected Holder<NoiseGeneratorSettings> settings;
    @Shadow @Final private Aquifer.FluidPicker globalFluidPicker;
    @Shadow @Final protected BlockState defaultBlock;
    @Shadow @Final private NoiseRouter router;

    private BiomeResolver possiblyFiddledBiomeResolver;

    @Shadow
    private BlockState debugPreliminarySurfaceLevel(NoiseChunk noiseChunk, int i, int j, int k, BlockState blockState) {
        throw new NotImplementedException();
    }

    @Inject(method = "<init>(Lnet/minecraft/core/Registry;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/biome/BiomeSource;JLnet/minecraft/core/Holder;)V", at = @At("TAIL"))
    private void injectConstructor(Registry<StructureSet> registry, Registry<NormalNoise.NoiseParameters> registry2, BiomeSource biomeSource, BiomeSource runtimeBiomeSource, long seed, Holder<NoiseGeneratorSettings> holder, CallbackInfo callbackInfo) {
        this.possiblyFiddledBiomeResolver = (runtimeBiomeSource instanceof FiddledBiomeResolver fiddledBiomeSource) ?
                new WrappedFiddledBiomeResolver(fiddledBiomeSource, BiomeManager.obfuscateSeed(seed)) : runtimeBiomeSource;
    }

    @Inject(method = "iterateNoiseColumn(II[Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Predicate;II)Ljava/util/OptionalInt;", at = @At("HEAD"), cancellable = true)
    private void injectIterateNoiseColumn(int worldX, int worldZ, @Nullable BlockState[] blockStates, @Nullable Predicate<BlockState> predicate, int cellNoiseMinY, int cellCountY, CallbackInfoReturnable<OptionalInt> callbackInfo) {

        NoiseSettings noiseSettings = this.settings.value().noiseSettings();
        int cellWidth = noiseSettings.getCellWidth();
        int cellHeight = noiseSettings.getCellHeight();
        int cellIndexX = Math.floorDiv(worldX, cellWidth);
        int cellIndexZ = Math.floorDiv(worldZ, cellWidth);
        int inCellX = Math.floorMod(worldX, cellWidth);
        int inCellZ = Math.floorMod(worldZ, cellWidth);
        int cellBaseX = cellIndexX * cellWidth;
        int cellBaseZ = cellIndexZ * cellWidth;

        NoiseChunk noiseChunk = NoiseChunk.forColumn(cellBaseX, cellBaseZ, cellNoiseMinY, cellCountY, this.router, this.settings.value(), this.globalFluidPicker);
        IMixinNoiseChunk mixinNoiseChunk = (IMixinNoiseChunk)(Object)noiseChunk;
        mixinNoiseChunk.setLocalXZ(inCellX, inCellZ);

        int ySize = cellCountY * cellHeight;
        int yBottomOffset = cellNoiseMinY * cellHeight;

        // Have to split into two loops for now, because IrreguLerper only runs upward.
        // TODO fix this in the future, either by supporting both directions or refactoring to run downward.
        if (blockStates == null) blockStates = new BlockState[ySize];
        for (int y = yBottomOffset; y < ySize + yBottomOffset; y++) {
            mixinNoiseChunk.setGlobalY(y);
            BlockState blockState = noiseChunk.getInterpolatedState();
            blockStates[y - yBottomOffset] = blockState == null ? this.defaultBlock : blockState;
        }
        for (int y = ySize + yBottomOffset - 1; y >= yBottomOffset; y--) {
            if (predicate != null && predicate.test(blockStates[y - yBottomOffset])) {
                noiseChunk.stopInterpolation();
                callbackInfo.setReturnValue(OptionalInt.of(y + 1));
                return;
            }
        }

        noiseChunk.stopInterpolation();
        callbackInfo.setReturnValue(OptionalInt.empty());
    }

    @Inject(method = "doFill(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/StructureFeatureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;II)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void doFill(Blender blender, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess,
            int vanillaNoiseGridMinY, int vanillaNoiseGridSizeY, CallbackInfoReturnable<ChunkAccess> callbackInfo) {

        // Read & translate settings
        NoiseGeneratorSettings noiseGeneratorSettings = (NoiseGeneratorSettings)this.settings.value();
        NoiseSettings noiseSettings = noiseGeneratorSettings.noiseSettings();
        ChunkPos chunkPos = chunkAccess.getPos();
        int chunkWorldX = chunkPos.getMinBlockX();
        int chunkWorldZ = chunkPos.getMinBlockZ();
        int cellHeight = noiseSettings.getCellHeight();
        int ySize = vanillaNoiseGridSizeY * cellHeight;
        int yBottomOffset = vanillaNoiseGridMinY * cellHeight;

        // Initialize for generation
        NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(this.router, () -> {
            return new Beardifier(structureFeatureManager, chunkAccess);
        }, noiseGeneratorSettings, this.globalFluidPicker, blender);
        IMixinNoiseChunk mixinNoiseChunk = (IMixinNoiseChunk)(Object)noiseChunk;
        Aquifer aquifer = noiseChunk.aquifer();
        Heightmap heightmap = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap2 = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        // Loop over columns.
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            int worldZ = chunkWorldZ | z;
            for (int x = 0; x < 16; x++) {
                int worldX = chunkWorldX | x;

                // New column for irregulerping and whatnot.
                mixinNoiseChunk.setLocalXZ(x, z);

                // Iterate up column.
                LevelChunkSection chunkSection = null;
                int chunkSectionYTop = Integer.MIN_VALUE;
                for (int y = yBottomOffset; y < ySize + yBottomOffset; y++) {
                    if (y >= chunkSectionYTop) {
                        int chunkSectionIndex = chunkAccess.getSectionIndex(y);
                        chunkSection = chunkAccess.getSection(chunkSectionIndex);
                        chunkSectionYTop = LevelChunkSection.getBottomBlockY(chunkSectionIndex) + yBottomOffset + LevelChunkSection.SECTION_HEIGHT;
                    }

                    // New column for irregulerping and whatnot.
                    mixinNoiseChunk.setGlobalY(y);

                    // Vanilla code adapted: get final block state and do what we need to with it.
                    BlockState blockState = noiseChunk.getInterpolatedState();
                    if (blockState == null) {
                        blockState = this.defaultBlock;
                    }
                    blockState = this.debugPreliminarySurfaceLevel(noiseChunk, y, worldX, worldZ, blockState);
                    if (blockState != AIR && !SharedConstants.debugVoidTerrain(chunkAccess.getPos())) {
                        if (blockState.getLightEmission() != 0 && chunkAccess instanceof ProtoChunk) {
                            mutableBlockPos.set(worldX, y, worldZ);
                            ((ProtoChunk)chunkAccess).addLight(mutableBlockPos);
                        }

                        chunkSection.setBlockState(x, y & (LevelChunkSection.SECTION_HEIGHT - 1), z, blockState, false);
                        heightmap.update(x, y, z, blockState);
                        heightmap2.update(x, y, z, blockState);
                        if (aquifer.shouldScheduleFluidUpdate() && !blockState.getFluidState().isEmpty()) {
                            mutableBlockPos.set(worldX, y, worldZ);
                            chunkAccess.markPosForPostprocessing(mutableBlockPos);
                        }
                    }
                }
            }
        }

        noiseChunk.stopInterpolation();
        callbackInfo.setReturnValue(chunkAccess);
    }

    /**
     * @author K.jpg
     * @reason Sample 'fiddled' biome positions where possible, following BiomeManager's displacement process.
     */
    @Overwrite
    private void doCreateBiomes(Blender blender, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess) {
        NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(this.router, () -> {
            return new Beardifier(structureFeatureManager, chunkAccess);
        }, this.settings.value(), this.globalFluidPicker, blender);
        BiomeResolver biomeResolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(this.possiblyFiddledBiomeResolver), chunkAccess);
        chunkAccess.fillBiomesFromNoise(biomeResolver, noiseChunk.cachedClimateSampler(this.router));
    }

}
