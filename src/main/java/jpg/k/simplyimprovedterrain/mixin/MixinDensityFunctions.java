package jpg.k.simplyimprovedterrain.mixin;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.terrain.CustomMathDensityFunctions;
import jpg.k.simplyimprovedterrain.terrain.SplitBlendedNoise;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DensityFunctions.class)
public class MixinDensityFunctions {

    // TODO This can't possibly be the best solution... or can it?
    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void bootstrap(Registry<Codec<? extends DensityFunction>> registry, CallbackInfoReturnable<Codec<? extends DensityFunction>> callbackInfos) {
        Registry.register(registry, "blended_noise_combine", SplitBlendedNoise.BlendedNoiseCombine.CODEC.codec());
        for (var blendedNoisePartType : SplitBlendedNoise.BlendedNoisePart.Type.values()) {
            Registry.register(registry, blendedNoisePartType.getSerializedName(), blendedNoisePartType.codec.codec());
        }
        Registry.register(registry, "smooth_min", CustomMathDensityFunctions.SmoothMin.CODEC.codec());
        Registry.register(registry, "smooth_range_choice", CustomMathDensityFunctions.SmoothRangeChoice.CODEC.codec());
    }

}
