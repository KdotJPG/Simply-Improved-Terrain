package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.terrain.CustomMathDensityFunctions;
import jpg.k.simplyimprovedterrain.terrain.SplitBlendedNoise;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.apache.commons.lang3.NotImplementedException;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Function;
import java.util.stream.Stream;

@Mixin(NoiseRouterData.class)
public class MixinNoiseRouterData {
    @Shadow @Final private static DensityFunction BLENDING_JAGGEDNESS;
    @Shadow @Final private static ResourceKey<DensityFunction> Y;
    @Shadow @Final private static ResourceKey<DensityFunction> SHIFT_X;
    @Shadow @Final private static ResourceKey<DensityFunction> SHIFT_Z;
    @Shadow @Final private static ResourceKey<DensityFunction> BASE_3D_NOISE_OVERWORLD;
    @Shadow @Final private static ResourceKey<DensityFunction> BASE_3D_NOISE_NETHER;
    @Shadow @Final private static ResourceKey<DensityFunction> BASE_3D_NOISE_END;
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
    @Shadow @Final private static ResourceKey<DensityFunction> FACTOR_AMPLIFIED;
    @Shadow @Final private static ResourceKey<DensityFunction> DEPTH_AMPLIFIED;
    @Shadow @Final private static ResourceKey<DensityFunction> JAGGEDNESS;
    @Shadow @Final private static ResourceKey<DensityFunction> JAGGEDNESS_LARGE;
    @Shadow @Final private static ResourceKey<DensityFunction> JAGGEDNESS_AMPLIFIED;
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
    public static NoiseRouter overworld(Registry<DensityFunction> registry, boolean isLarge, boolean isAmplified) {
        DensityFunction densityFunction = DensityFunctions.noise(getNoise(Noises.AQUIFER_BARRIER), 0.5D);
        DensityFunction densityFunction2 = DensityFunctions.noise(getNoise(Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS), 0.67D);
        DensityFunction densityFunction3 = DensityFunctions.noise(getNoise(Noises.AQUIFER_FLUID_LEVEL_SPREAD), 0.7142857142857143D);
        DensityFunction densityFunction4 = DensityFunctions.noise(getNoise(Noises.AQUIFER_LAVA));
        DensityFunction densityFunction5 = getFunction(registry, SHIFT_X);
        DensityFunction densityFunction6 = getFunction(registry, SHIFT_Z);
        DensityFunction densityFunction7 = DensityFunctions.shiftedNoise2d(densityFunction5, densityFunction6, 0.25D, getNoise(isLarge ? Noises.TEMPERATURE_LARGE : Noises.TEMPERATURE));
        DensityFunction densityFunction8 = DensityFunctions.shiftedNoise2d(densityFunction5, densityFunction6, 0.25D, getNoise(isLarge ? Noises.VEGETATION_LARGE : Noises.VEGETATION));
        DensityFunction factor = getFunction(registry, isLarge ? FACTOR_LARGE : (isAmplified ? FACTOR_AMPLIFIED : FACTOR));
        DensityFunction depth = getFunction(registry, isLarge ? DEPTH_LARGE : (isAmplified ? DEPTH_AMPLIFIED : DEPTH));
        DensityFunction jaggednessSpline = getFunction(registry, isLarge ? JAGGEDNESS_LARGE : (isAmplified ? JAGGEDNESS_AMPLIFIED : JAGGEDNESS));
        DensityFunction densityFunction11 = noiseGradientDensity(DensityFunctions.cache2d(factor), depth);

        // Three parts that used be blended inside BlendedNoise.java
        DensityFunction blendedNoise = getFunction(registry, BASE_3D_NOISE_OVERWORLD);
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
        DensityFunction jaggednessNoise = DensityFunctions.cache2d(DensityFunctions.noise(getNoise(Noises.JAGGED), 1500.0D, 0.0D));
        DensityFunction slopeForTerrainNoise = slopeForTerrainNoise(registry, jaggednessNoise, jaggednessSpline, factor, depth);

        // Blended noise only used in sparse sampling space for cave gen, -> cave gen
        DensityFunction blendedNoiseForUnderground = SplitBlendedNoise.BlendedNoiseCombine.create(blendNoiseMinLimit, blendNoiseMaxLimit, blendNoiseMain);
        DensityFunction slopedNoiseForUnderground = DensityFunctions.add(slopeForTerrainNoise, blendedNoiseForUnderground);
        DensityFunction interpolatedUnderground = DensityFunctions.interpolated(underground(registry, slopedNoiseForUnderground)); // TODO see what parts of underground(...) benefit from splitting out interpolations

        // Individually-interpolated blended noise, directly visible in terrain.
        DensityFunction blendedNoiseForTerrain = SplitBlendedNoise.BlendedNoiseCombine.create(
                DensityFunctions.interpolated(blendNoiseMinLimit),
                DensityFunctions.interpolated(blendNoiseMaxLimit),
                DensityFunctions.interpolated(blendNoiseMain)
        );
        DensityFunction slopedNoiseForTerrain = DensityFunctions.add(slopeForTerrainNoise, blendedNoiseForTerrain);

        // Home stretch
        DensityFunction entrancesMultipliedInterpolated = DensityFunctions.interpolated(DensityFunctions.mul(DensityFunctions.constant(5.0D), getFunction(registry, ENTRANCES)));
        DensityFunction terrainWithEntrances = CustomMathDensityFunctions.SmoothMin.create(slopedNoiseForTerrain, entrancesMultipliedInterpolated, 1.25);
        DensityFunction terrainWithEntrancesAndCaves = CustomMathDensityFunctions.SmoothRangeChoice.create(slopedNoiseForTerrain, -1000000.0, 1.5625D, 0.04, terrainWithEntrances, interpolatedUnderground);
        DensityFunction interpolatedTerrainWithNoodles = CustomMathDensityFunctions.SmoothMin.create(postProcessNoInterpolate(slideOverworld(isAmplified, terrainWithEntrancesAndCaves)), getFunction(registry, NOODLE), 0.03);

        DensityFunction densityFunction16 = getFunction(registry, Y);
        int j = Stream.of(OreVeinifier.VeinType.values()).mapToInt((veinType) -> {
            return veinType.minY;
        }).min().orElse(-DimensionType.MIN_Y * 2);
        int k = Stream.of(OreVeinifier.VeinType.values()).mapToInt((veinType) -> {
            return veinType.maxY;
        }).max().orElse(-DimensionType.MIN_Y * 2);
        DensityFunction densityFunction17 = yLimitedInterpolatable(densityFunction16, DensityFunctions.noise(getNoise(Noises.ORE_VEININESS), 1.5D, 1.5D), j, k, 0);
        float f = 4.0F;
        DensityFunction densityFunction18 = yLimitedInterpolatable(densityFunction16, DensityFunctions.noise(getNoise(Noises.ORE_VEIN_A), 4.0D, 4.0D), j, k, 0).abs();
        DensityFunction densityFunction19 = yLimitedInterpolatable(densityFunction16, DensityFunctions.noise(getNoise(Noises.ORE_VEIN_B), 4.0D, 4.0D), j, k, 0).abs();
        DensityFunction densityFunction20 = DensityFunctions.add(DensityFunctions.constant(-0.07999999821186066D), DensityFunctions.max(densityFunction18, densityFunction19));
        DensityFunction densityFunction21 = DensityFunctions.noise(getNoise(Noises.ORE_GAP));
        return new NoiseRouter(densityFunction, densityFunction2, densityFunction3, densityFunction4, densityFunction7, densityFunction8,
                getFunction(registry, isLarge ? CONTINENTS_LARGE : CONTINENTS), getFunction(registry, isLarge ? EROSION_LARGE : EROSION), getFunction(registry, isLarge ? DEPTH_LARGE : DEPTH),
                getFunction(registry, RIDGES), densityFunction11, interpolatedTerrainWithNoodles, densityFunction17, densityFunction20, densityFunction21);
    }

    private static NoiseRouter noNewCaves(Registry<DensityFunction> registry, DensityFunction blendedNoise, Function<DensityFunction, DensityFunction> mapSlide) {
        DensityFunction domainWarpX = getFunction(registry, SHIFT_X);
        DensityFunction domainWarpZ = getFunction(registry, SHIFT_Z);
        DensityFunction warpedTemperature = DensityFunctions.shiftedNoise2d(domainWarpX, domainWarpZ, 0.25, getNoise(Noises.TEMPERATURE));
        DensityFunction warpedVegetation = DensityFunctions.shiftedNoise2d(domainWarpX, domainWarpZ, 0.25, getNoise(Noises.VEGETATION));
        DensityFunction densityFunction5 = noiseGradientDensity(DensityFunctions.cache2d(getFunction(registry, FACTOR)), getFunction(registry, DEPTH));

        // Three parts that used be blended inside BlendedNoise.java
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
        DensityFunction postProcessedTerrain = postProcessNoInterpolate(mapSlide.apply(blendedNoiseForTerrain));
        
        return new NoiseRouter(DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
                warpedTemperature, warpedVegetation, getFunction(registry, CONTINENTS), getFunction(registry, EROSION), getFunction(registry, DEPTH), getFunction(registry, RIDGES),
                densityFunction5, postProcessedTerrain, DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero());
    }

    private static DensityFunction slideNetherLike(Registry<DensityFunction> registry, int i, int j, DensityFunction blendedNoise) {
        return slide(blendedNoise, i, j, 24, 0, 0.9375D, -8, 24, 2.5D);
    }

    /**
     * @author K.jpg
     * @reason Separate BlendedNoise into individual interpolation channels.
     * /
    @Overwrite
    public static NoiseRouter nether(Registry<DensityFunction> registry) {
        return noNewCaves(registry, getFunction(registry, BASE_3D_NOISE_NETHER), (base) -> slideNetherLike(registry, 0, 128, base));
    }

    /**
     * @author K.jpg
     * @reason Separate BlendedNoise into individual interpolation channels.
     * /
    @Overwrite
    public static NoiseRouter caves(Registry<DensityFunction> registry) {
        return noNewCaves(registry, getFunction(registry, BASE_3D_NOISE_NETHER), (base) -> slideNetherLike(registry, -64, 192, base));
    }

    /**
     * @author K.jpg
     * @reason Separate BlendedNoise into individual interpolation channels.
     * /
    @Overwrite
    public static NoiseRouter floatingIslands(Registry<DensityFunction> registry) {
        return noNewCaves(registry, getFunction(registry, BASE_3D_NOISE_END), (base) -> slideEndLike(base, 0, 256));
    }*/

    /**
     * @author K.jpg
     * @reason Separate BlendedNoise into individual interpolation channels.
     */
    @Overwrite
    public static NoiseRouter end(Registry<DensityFunction> registry) {

        DensityFunction endIslands = DensityFunctions.endIslands(0);
        DensityFunction endIslandsCached2D = DensityFunctions.cache2d(endIslands);

        // Three parts that used be blended inside BlendedNoise.java
        DensityFunction blendedNoise = getFunction(registry, BASE_3D_NOISE_END);
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
        DensityFunction postProcessedTerrain = postProcessNoInterpolate(slideEnd(slopedNoiseForTerrain));

        return new NoiseRouter(
                DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
                DensityFunctions.zero(), DensityFunctions.zero(), endIslands, DensityFunctions.zero(), DensityFunctions.zero(),
                endIslandsCached2D, postProcessedTerrain, DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero());
    }

    private static DensityFunction slopeForTerrainNoise(Registry<DensityFunction> registry, DensityFunction jaggedNoise, DensityFunction jaggedSpline, DensityFunction factorNoise, DensityFunction depthNoise) {
        jaggedSpline = DensityFunctions.mul(jaggedSpline, jaggedNoise.halfNegative());
        DensityFunction noiseVerticalGradient = noiseGradientDensity(factorNoise, DensityFunctions.add(depthNoise, jaggedSpline));
        return noiseVerticalGradient;
    }

    private static DensityFunction postProcessNoInterpolate(DensityFunction noiseWithSlides) {
        // TODO put in placesDensityFunction noiseWithSlides = DensityFunctions.slide(noiseSettings, noise);
        DensityFunction noiseWithSlidesAndBlendDensity = DensityFunctions.blendDensity(noiseWithSlides);
        return DensityFunctions.mul(noiseWithSlidesAndBlendDensity, DensityFunctions.constant(0.64D)).squeeze();
    }

    @Shadow
    private static DensityFunction yLimitedInterpolatable(DensityFunction densityFunction16, DensityFunction noise, int j, int k, int i) {
        throw new NotImplementedException();
    }

    @Shadow
    private static DensityFunction underground(Registry<DensityFunction> registry, DensityFunction densityFunction12) {
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
    private static DensityFunction getFunction(Registry<DensityFunction> registry, ResourceKey<DensityFunction> resourceKey) {
        throw new NotImplementedException();
    }

    @Shadow
    private static DensityFunction slideOverworld(boolean isAmplified, DensityFunction densityFunction) {
        throw new NotImplementedException();
    }

    @Shadow
    private static DensityFunction slideEnd(DensityFunction densityFunction) {
        throw new NotImplementedException();
    }
    
    @Shadow
    private static DensityFunction slide(DensityFunction densityFunction, int i, int j, int k, int l, double d, int m, int n, double e) {
        throw new NotImplementedException();
    }

    @Shadow
    private static DensityFunction slideEndLike(DensityFunction densityFunction, int i, int j) {
        throw new NotImplementedException();
    }

}
