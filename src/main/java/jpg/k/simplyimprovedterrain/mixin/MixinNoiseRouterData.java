package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.terrain.CustomMathDensityFunctions;
import jpg.k.simplyimprovedterrain.terrain.SplitBlendedNoise;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.apache.commons.lang3.NotImplementedException;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.stream.Stream;

@Mixin(NoiseRouterData.class)
public class MixinNoiseRouterData {
    @Shadow @Final private static DensityFunction BLENDING_JAGGEDNESS;
    @Shadow @Final private static ResourceKey<DensityFunction> Y;
    @Shadow @Final private static ResourceKey<DensityFunction> SHIFT_X;
    @Shadow @Final private static ResourceKey<DensityFunction> SHIFT_Z;
    @Shadow @Final private static ResourceKey<DensityFunction> BASE_3D_NOISE;
    @Shadow @Final private static ResourceKey<DensityFunction> CONTINENTS;
    @Shadow @Final private static ResourceKey<DensityFunction> EROSION;
    @Shadow @Final private static ResourceKey<DensityFunction> RIDGES;
    @Shadow @Final private static ResourceKey<DensityFunction> FACTOR;
    @Shadow @Final private static ResourceKey<DensityFunction> DEPTH;
    @Shadow @Final private static ResourceKey<DensityFunction> SLOPED_CHEESE;
    @Shadow @Final private static ResourceKey<DensityFunction> CONTINENTS_LARGE;
    @Shadow @Final private static ResourceKey<DensityFunction> EROSION_LARGE;
    @Shadow @Final private static ResourceKey<DensityFunction> FACTOR_LARGE;
    @Shadow @Final private static ResourceKey<DensityFunction> DEPTH_LARGE;
    @Shadow @Final private static ResourceKey<DensityFunction> SLOPED_CHEESE_LARGE;
    @Shadow @Final private static ResourceKey<DensityFunction> SLOPED_CHEESE_END;
    @Shadow @Final private static ResourceKey<DensityFunction> SPAGHETTI_ROUGHNESS_FUNCTION;
    @Shadow @Final private static ResourceKey<DensityFunction> ENTRANCES;
    @Shadow @Final private static ResourceKey<DensityFunction> NOODLE;
    @Shadow @Final private static ResourceKey<DensityFunction> PILLARS;
    @Shadow @Final private static ResourceKey<DensityFunction> SPAGHETTI_2D_THICKNESS_MODULATOR;
    @Shadow @Final private static ResourceKey<DensityFunction> SPAGHETTI_2D;

    /**
     * @author K.jpg
	 * @reason Separate BlendedNoise into individual interpolation channels.
     */
    @Overwrite
    private static NoiseRouterWithOnlyNoises overworldWithNewCaves(NoiseSettings noiseSettings, boolean isLarge) {
        DensityFunction densityFunction = DensityFunctions.noise(getNoise(Noises.AQUIFER_BARRIER), 0.5D);
        DensityFunction densityFunction2 = DensityFunctions.noise(getNoise(Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS), 0.67D);
        DensityFunction densityFunction3 = DensityFunctions.noise(getNoise(Noises.AQUIFER_FLUID_LEVEL_SPREAD), 0.7142857142857143D);
        DensityFunction densityFunction4 = DensityFunctions.noise(getNoise(Noises.AQUIFER_LAVA));
        DensityFunction densityFunction5 = getFunction(SHIFT_X);
        DensityFunction densityFunction6 = getFunction(SHIFT_Z);
        DensityFunction densityFunction7 = DensityFunctions.shiftedNoise2d(densityFunction5, densityFunction6, 0.25D, getNoise(isLarge ? Noises.TEMPERATURE_LARGE : Noises.TEMPERATURE));
        DensityFunction densityFunction8 = DensityFunctions.shiftedNoise2d(densityFunction5, densityFunction6, 0.25D, getNoise(isLarge ? Noises.VEGETATION_LARGE : Noises.VEGETATION));
        DensityFunction factor = getFunction(isLarge ? FACTOR_LARGE : FACTOR);
        DensityFunction depth = getFunction(isLarge ? DEPTH_LARGE : DEPTH);
        DensityFunction densityFunction11 = noiseGradientDensity(DensityFunctions.cache2d(factor), depth);

        // Three parts that used be blended inside BlendedNoise.java
        DensityFunction blendedNoise = getFunction(BASE_3D_NOISE);
        DensityFunction blendNoiseMain = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MAIN_NOISE, blendedNoise);
        DensityFunction blendNoiseMinLimit = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MIN_LIMIT_NOISE, blendedNoise);
        DensityFunction blendNoiseMaxLimit = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MAX_LIMIT_NOISE, blendedNoise);

        // SuperCoder79's optimization re-implemented, with a bit of a buffer due to interpolation.
        blendNoiseMinLimit = DensityFunctions.rangeChoice(blendNoiseMain, -1000000.0, SplitBlendedNoise.MAIN_NOISE_MAX_TO_EVALUATE_1ST_HALF, blendNoiseMinLimit, DensityFunctions.zero());
        blendNoiseMaxLimit = DensityFunctions.rangeChoice(blendNoiseMain, SplitBlendedNoise.MAIN_NOISE_MIN_TO_EVALUATE_2ND_HALF,  1000000.0, blendNoiseMaxLimit, DensityFunctions.zero());

        // Now cache -- we'll use them in two places each.
        blendNoiseMain = DensityFunctions.cacheOnce(blendNoiseMain);
        blendNoiseMinLimit = DensityFunctions.cacheOnce(blendNoiseMinLimit);
        blendNoiseMaxLimit = DensityFunctions.cacheOnce(blendNoiseMaxLimit);

        // TODO the biome param noises, etc. have DensityFunctions.flatCache(DensityFunctions.cache2d(...
        //      Once we actually reimplement flatCache (or if we disable custom interpolation/wrapping),
        //      this may not work right.
        DensityFunction jagged = DensityFunctions.cache2d(DensityFunctions.noise(getNoise(Noises.JAGGED), 1500.0D, 0.0D));
        DensityFunction slopeForTerrainNoise = slopeForTerrainNoise(
                getFunction(isLarge ? CONTINENTS_LARGE : CONTINENTS),
                getFunction(isLarge ? EROSION_LARGE : EROSION),
                getFunction(RIDGES), factor, depth, jagged
        );

        // Blended noise only used in sparse sampling space for cave gen, -> cave gen
        DensityFunction blendedNoiseForUnderground = SplitBlendedNoise.BlendedNoiseCombine.create(blendNoiseMinLimit, blendNoiseMaxLimit, blendNoiseMain);
        DensityFunction slopedNoiseForUnderground = DensityFunctions.add(slopeForTerrainNoise, blendedNoiseForUnderground);
        DensityFunction interpolatedUnderground = DensityFunctions.interpolated(underground(slopedNoiseForUnderground)); // TODO see what parts of underground(...) benefit from splitting out interpolations

        // Individually-interpolated blended noise, directly visible in terrain.
        DensityFunction blendedNoiseForTerrain = SplitBlendedNoise.BlendedNoiseCombine.create(
                DensityFunctions.interpolated(blendNoiseMinLimit),
                DensityFunctions.interpolated(blendNoiseMaxLimit),
                DensityFunctions.interpolated(blendNoiseMain)
        );
        DensityFunction slopedNoiseForTerrain = DensityFunctions.add(slopeForTerrainNoise, blendedNoiseForTerrain);

        // Home stretch
        DensityFunction entrancesMultipliedInterpolated = DensityFunctions.interpolated(DensityFunctions.mul(DensityFunctions.constant(5.0D), getFunction(ENTRANCES)));
        DensityFunction terrainWithEntrances = CustomMathDensityFunctions.SmoothMin.create(slopedNoiseForTerrain, entrancesMultipliedInterpolated, 1.25);
        DensityFunction terrainWithEntrancesAndCaves = CustomMathDensityFunctions.SmoothRangeChoice.create(slopedNoiseForTerrain, -1000000.0, 1.5625D, 0.04, terrainWithEntrances, interpolatedUnderground);
        DensityFunction interpolatedTerrainWithNoodles = CustomMathDensityFunctions.SmoothMin.create(postProcessNoInterpolate(noiseSettings, terrainWithEntrancesAndCaves), getFunction(NOODLE), 0.03);

        /*DensityFunction densityFunction12 = getFunction(isLarge ? SLOPED_CHEESE_LARGE : SLOPED_CHEESE);
        DensityFunction densityFunction13 = DensityFunctions.min(densityFunction12, DensityFunctions.mul(DensityFunctions.constant(5.0D), getFunction(ENTRANCES)));
        DensityFunction densityFunction14 = DensityFunctions.rangeChoice(densityFunction12, -1000000.0D, 1.5625D, densityFunction13, underground(densityFunction12));
        DensityFunction interpolatedTerrainWithNoodles = DensityFunctions.min(postProcess(noiseSettings, densityFunction14), getFunction(NOODLE));*/

        DensityFunction densityFunction16 = getFunction(Y);
        int i = noiseSettings.minY();
        int j = Stream.of(OreVeinifier.VeinType.values()).mapToInt((veinType) -> {
            return veinType.minY;
        }).min().orElse(i);
        int k = Stream.of(OreVeinifier.VeinType.values()).mapToInt((veinType) -> {
            return veinType.maxY;
        }).max().orElse(i);
        DensityFunction densityFunction17 = yLimitedInterpolatable(densityFunction16, DensityFunctions.noise(getNoise(Noises.ORE_VEININESS), 1.5D, 1.5D), j, k, 0);
        float f = 4.0F;
        DensityFunction densityFunction18 = yLimitedInterpolatable(densityFunction16, DensityFunctions.noise(getNoise(Noises.ORE_VEIN_A), 4.0D, 4.0D), j, k, 0).abs();
        DensityFunction densityFunction19 = yLimitedInterpolatable(densityFunction16, DensityFunctions.noise(getNoise(Noises.ORE_VEIN_B), 4.0D, 4.0D), j, k, 0).abs();
        DensityFunction densityFunction20 = DensityFunctions.add(DensityFunctions.constant(-0.07999999821186066D), DensityFunctions.max(densityFunction18, densityFunction19));
        DensityFunction densityFunction21 = DensityFunctions.noise(getNoise(Noises.ORE_GAP));
        return new NoiseRouterWithOnlyNoises(densityFunction, densityFunction2, densityFunction3, densityFunction4, densityFunction7, densityFunction8,
                getFunction(isLarge ? CONTINENTS_LARGE : CONTINENTS), getFunction(isLarge ? EROSION_LARGE : EROSION), getFunction(isLarge ? DEPTH_LARGE : DEPTH),
                getFunction(RIDGES), densityFunction11, interpolatedTerrainWithNoodles, densityFunction17, densityFunction20, densityFunction21);
    }

    /**
     * @author K.jpg
     * @reason Separate BlendedNoise into individual interpolation channels.
     */
    @Overwrite
    private static NoiseRouterWithOnlyNoises noNewCaves(NoiseSettings noiseSettings) {
        DensityFunction domainWarpX = getFunction(SHIFT_X);
        DensityFunction domainWarpZ = getFunction(SHIFT_Z);
        DensityFunction warpedTemperature = DensityFunctions.shiftedNoise2d(domainWarpX, domainWarpZ, 0.25, getNoise(Noises.TEMPERATURE));
        DensityFunction warpedVegetation = DensityFunctions.shiftedNoise2d(domainWarpX, domainWarpZ, 0.25, getNoise(Noises.VEGETATION));
        DensityFunction densityFunction5 = noiseGradientDensity(DensityFunctions.cache2d(getFunction(FACTOR)), getFunction(DEPTH));

        // Three parts that used be blended inside BlendedNoise.java
        DensityFunction blendedNoise = getFunction(BASE_3D_NOISE);
        DensityFunction blendNoiseMain = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MAIN_NOISE, blendedNoise);
        DensityFunction blendNoiseMinLimit = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MIN_LIMIT_NOISE, blendedNoise);
        DensityFunction blendNoiseMaxLimit = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MAX_LIMIT_NOISE, blendedNoise);

        // SuperCoder79's optimization re-implemented, with a bit of a buffer due to interpolation.
        blendNoiseMinLimit = DensityFunctions.rangeChoice(blendNoiseMain, -1000000.0, SplitBlendedNoise.MAIN_NOISE_MAX_TO_EVALUATE_1ST_HALF, blendNoiseMinLimit, DensityFunctions.zero());
        blendNoiseMaxLimit = DensityFunctions.rangeChoice(blendNoiseMain, SplitBlendedNoise.MAIN_NOISE_MIN_TO_EVALUATE_2ND_HALF,  1000000.0, blendNoiseMaxLimit, DensityFunctions.zero());

        // TODO get around FlatCache if we add ability to disable our wrapNew+IrreguLerper
        DensityFunction jagged = DensityFunctions.cache2d(DensityFunctions.noise(getNoise(Noises.JAGGED), 1500.0D, 0.0D));
        DensityFunction slopeForTerrainNoise = slopeForTerrainNoise(
                getFunction(CONTINENTS), getFunction(EROSION), getFunction(RIDGES),
                DensityFunctions.cache2d(getFunction(FACTOR)), getFunction(DEPTH), jagged
        );

        // Individually-interpolated blended noise, directly visible in terrain.
        DensityFunction blendedNoiseForTerrain = SplitBlendedNoise.BlendedNoiseCombine.create(
                DensityFunctions.interpolated(blendNoiseMinLimit),
                DensityFunctions.interpolated(blendNoiseMaxLimit),
                DensityFunctions.interpolated(blendNoiseMain)
        );
        DensityFunction slopedNoiseForTerrain = DensityFunctions.add(slopeForTerrainNoise, blendedNoiseForTerrain);
        DensityFunction postProcessedTerrain = postProcessNoInterpolate(noiseSettings, slopedNoiseForTerrain);
        
        return new NoiseRouterWithOnlyNoises(DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
                warpedTemperature, warpedVegetation, getFunction(CONTINENTS), getFunction(EROSION), getFunction(DEPTH), getFunction(RIDGES),
                densityFunction5, postProcessedTerrain, DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero());
    }

    /**
     * @author K.jpg
     * @reason Separate BlendedNoise into individual interpolation channels.
     */
    @Overwrite
    public static NoiseRouterWithOnlyNoises end(NoiseSettings noiseSettings) {

        DensityFunction endIslands = DensityFunctions.endIslands(0);
        DensityFunction endIslandsCached2D = DensityFunctions.cache2d(endIslands);

        // Three parts that used be blended inside BlendedNoise.java
        DensityFunction blendedNoise = getFunction(BASE_3D_NOISE);
        DensityFunction blendNoiseMain = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MAIN_NOISE, blendedNoise);
        DensityFunction blendNoiseMinLimit = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MIN_LIMIT_NOISE, blendedNoise);
        DensityFunction blendNoiseMaxLimit = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MAX_LIMIT_NOISE, blendedNoise);

        // SuperCoder79's optimization re-implemented, with a bit of a buffer due to interpolation.
        blendNoiseMinLimit = DensityFunctions.rangeChoice(blendNoiseMain, -1000000.0, SplitBlendedNoise.MAIN_NOISE_MAX_TO_EVALUATE_1ST_HALF, blendNoiseMinLimit, DensityFunctions.zero());
        blendNoiseMaxLimit = DensityFunctions.rangeChoice(blendNoiseMain, SplitBlendedNoise.MAIN_NOISE_MIN_TO_EVALUATE_2ND_HALF,  1000000.0, blendNoiseMaxLimit, DensityFunctions.zero());

        // Individually-interpolated blended noise, directly visible in terrain.
        DensityFunction blendedNoiseForTerrain = SplitBlendedNoise.BlendedNoiseCombine.create(
                DensityFunctions.interpolated(blendNoiseMinLimit),
                DensityFunctions.interpolated(blendNoiseMaxLimit),
                DensityFunctions.interpolated(blendNoiseMain)
        );
        DensityFunction slopedNoiseForTerrain = DensityFunctions.add(endIslands, blendedNoiseForTerrain);
        DensityFunction postProcessedTerrain = postProcessNoInterpolate(noiseSettings, slopedNoiseForTerrain);

        return new NoiseRouterWithOnlyNoises(
                DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
                DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
                endIslandsCached2D, postProcessedTerrain, DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero());
    }

    /**
     * @author K.jpg
     * @reason Need this to not lock us to cell coordinates.
     */
    @Overwrite
    public static double applySlide(NoiseSettings noiseSettings, double value, double y) {
        double transformedY = y / noiseSettings.getCellHeight() - noiseSettings.getMinCellY();
        value = noiseSettings.topSlideSettings().applySlide(value, noiseSettings.getCellCountY() - transformedY);
        value = noiseSettings.bottomSlideSettings().applySlide(value, transformedY);
        return value;
    }

    private static DensityFunction slopeForTerrainNoise(DensityFunction continentality, DensityFunction erosion, DensityFunction ridges, DensityFunction factor, DensityFunction depth, DensityFunction jagged) {
        DensityFunction jaggedSpline = splineWithBlending(continentality, erosion, ridges, DensityFunctions.TerrainShaperSpline.SplineType.JAGGEDNESS, 0.0D, 1.28D, BLENDING_JAGGEDNESS);
        jaggedSpline = DensityFunctions.mul(jaggedSpline, jagged.halfNegative());
        DensityFunction noiseVerticalGradient = noiseGradientDensity(factor, DensityFunctions.add(depth, jaggedSpline));
        return noiseVerticalGradient;
    }

    private static DensityFunction postProcessNoInterpolate(NoiseSettings noiseSettings, DensityFunction noise) {
        DensityFunction noiseWithSlides = DensityFunctions.slide(noiseSettings, noise);
        DensityFunction noiseWithSlidesAndBlendDensity = DensityFunctions.blendDensity(noiseWithSlides); // TODO I think this is for old world blending?
        return DensityFunctions.mul(noiseWithSlidesAndBlendDensity, DensityFunctions.constant(0.64D)).squeeze();
    }

    @Shadow
    private static DensityFunction splineWithBlending(DensityFunction densityFunction, DensityFunction densityFunction2, DensityFunction densityFunction3, DensityFunctions.TerrainShaperSpline.SplineType splineType, double d, double e, DensityFunction densityFunction4) {
        throw new NotImplementedException();
    }

    @Shadow
    private static DensityFunction postProcess(NoiseSettings noiseSettings, DensityFunction densityFunction14) {
        throw new NotImplementedException();
    }

    @Shadow
    private static DensityFunction yLimitedInterpolatable(DensityFunction densityFunction16, DensityFunction noise, int j, int k, int i) {
        throw new NotImplementedException();
    }

    @Shadow
    private static DensityFunction underground(DensityFunction densityFunction12) {
        throw new NotImplementedException();
    }

    @Shadow
    private static DensityFunction noiseGradientDensity(DensityFunction factor, DensityFunction depth) {
        throw new NotImplementedException();
    }

    @Shadow
    private static Holder<NormalNoise.NoiseParameters> getNoise(ResourceKey<NormalNoise.NoiseParameters> aquiferBarrier) {
        throw new NotImplementedException();
    }

    @Shadow
    private static DensityFunction getFunction(ResourceKey<DensityFunction> resourceKey) {
        throw new NotImplementedException();
    }

}
