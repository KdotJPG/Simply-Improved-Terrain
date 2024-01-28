package jpg.k.simplyimprovedterrain.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jpg.k.simplyimprovedterrain.terrain.formulamodification.TerrainFormulaModification;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RandomState.class)
public class MixinRandomState {

    @Shadow @Final
    private HolderGetter<NormalNoise.NoiseParameters> noises;

    @WrapOperation(method = "<init>", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;noiseRouter()Lnet/minecraft/world/level/levelgen/NoiseRouter;"
    ))
    private NoiseRouter getModifiedNoiseRouter(NoiseGeneratorSettings instance, Operation<NoiseRouter> original) {
        return TerrainFormulaModification.translateFormula(original.call(instance), noises);
    }

}
