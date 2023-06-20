package jpg.k.simplyimprovedterrain.terrain.customdensityfunctions;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.function.BiConsumer;

public final class CustomDensityFunctions {

    public static void bootstrap(BiConsumer<String, Codec<? extends DensityFunction>> callback) {
        callback.accept(SmoothRangeChoice.SERIALIZED_NAME, SmoothRangeChoice.CODEC.codec());
        callback.accept(MultiArgumentSimpleFunction.SERIALIZED_NAME, MultiArgumentSimpleFunction.CODEC.codec());
        callback.accept(MultiSmoothMinOrMax.SERIALIZED_NAME, MultiSmoothMinOrMax.CODEC.codec());
        callback.accept(SmoothClamp.SERIALIZED_NAME, SmoothClamp.CODEC.codec());
        callback.accept(SmoothMapped.SERIALIZED_NAME, SmoothMapped.CODEC.codec());
        callback.accept(SplitBlendedNoise.BlendedNoiseCombine.SERIALIZED_NAME, SplitBlendedNoise.BlendedNoiseCombine.CODEC.codec());
        for (var blendedNoisePartType : SplitBlendedNoise.BlendedNoisePart.Type.values()) {
            callback.accept(blendedNoisePartType.getSerializedName(), blendedNoisePartType.codec.codec());
        }
    }
}
