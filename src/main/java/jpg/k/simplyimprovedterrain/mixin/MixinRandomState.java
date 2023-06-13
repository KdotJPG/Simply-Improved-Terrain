package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.terrain.formulamodification.TerrainFormulaModification;
import net.minecraft.core.Registry;
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
    private Registry<NormalNoise.NoiseParameters> noises;

    @Redirect(method = "<init>", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;noiseRouter()Lnet/minecraft/world/level/levelgen/NoiseRouter;"
    ))
    private NoiseRouter getModifiedNoiseRouter(NoiseGeneratorSettings instance) {
        return TerrainFormulaModification.translateFormula(instance.noiseRouter(), noises);
    }

}
