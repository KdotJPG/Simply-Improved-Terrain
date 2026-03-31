package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.mixinapi.IMixinRandomState;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomState.class)
public class MixinRandomState implements IMixinRandomState {

    @Unique
    private long worldSeed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureWorldSeed(NoiseGeneratorSettings noiseGeneratorSettings, HolderGetter<NormalNoise.NoiseParameters> noiseParametersHolderGetter, final long seed, CallbackInfo callbackInfo) {
        this.worldSeed = seed;
    }

    @Override @Unique
    public long worldSeed() {
        return worldSeed;
    }
}
