package jpg.k.simplyimprovedterrain.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinRandomState;
import jpg.k.simplyimprovedterrain.terrain.formulamodification.TerrainFormulaModification;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomState.class)
public class MixinRandomState implements IMixinRandomState {

    @Shadow @Final
    private HolderGetter<NormalNoise.NoiseParameters> noises;

    @Unique
    private long worldSeed;

    @WrapOperation(method = "<init>", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;noiseRouter()Lnet/minecraft/world/level/levelgen/NoiseRouter;"
    ))
    private NoiseRouter getModifiedNoiseRouter(NoiseGeneratorSettings instance, Operation<NoiseRouter> original) {
        return TerrainFormulaModification.translateFormula(original.call(instance), noises);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureWorldSeed(NoiseGeneratorSettings noiseGeneratorSettings, HolderGetter<NormalNoise.NoiseParameters> noiseParametersHolderGetter, final long seed, CallbackInfo callbackInfo) {
        this.worldSeed = seed;
    }

    @Override @Unique
    public long worldSeed() {
        return worldSeed;
    }
}
