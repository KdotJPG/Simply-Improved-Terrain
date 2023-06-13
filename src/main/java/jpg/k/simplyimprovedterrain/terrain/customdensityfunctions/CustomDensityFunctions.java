package jpg.k.simplyimprovedterrain.terrain.customdensityfunctions;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.SimplyImprovedTerrain;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.function.BiConsumer;

public final class CustomDensityFunctions {

    public static void bootstrap() {
        SimplyImprovedTerrain.register(Registry.DENSITY_FUNCTION_TYPES, SmoothRangeChoice.SERIALIZED_NAME, SmoothRangeChoice.CODEC.codec());
        SimplyImprovedTerrain.register(Registry.DENSITY_FUNCTION_TYPES, MultiArgumentSimpleFunction.SERIALIZED_NAME, MultiArgumentSimpleFunction.CODEC.codec());
        SimplyImprovedTerrain.register(Registry.DENSITY_FUNCTION_TYPES, MultiSmoothMinOrMax.SERIALIZED_NAME, MultiSmoothMinOrMax.CODEC.codec());
        SimplyImprovedTerrain.register(Registry.DENSITY_FUNCTION_TYPES, SmoothClamp.SERIALIZED_NAME, SmoothClamp.CODEC.codec());
        SimplyImprovedTerrain.register(Registry.DENSITY_FUNCTION_TYPES, SmoothMapped.SERIALIZED_NAME, SmoothMapped.CODEC.codec());
        SimplyImprovedTerrain.register(Registry.DENSITY_FUNCTION_TYPES, SplitBlendedNoise.BlendedNoiseCombine.SERIALIZED_NAME, SplitBlendedNoise.BlendedNoiseCombine.CODEC.codec());
        for (var blendedNoisePartType : SplitBlendedNoise.BlendedNoisePart.Type.values()) {
            SimplyImprovedTerrain.register(Registry.DENSITY_FUNCTION_TYPES, blendedNoisePartType.getSerializedName(), blendedNoisePartType.codec.codec());
        }
    }
}
