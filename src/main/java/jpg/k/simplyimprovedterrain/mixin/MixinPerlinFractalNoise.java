package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.mixinapi.IMixinPerlinFractalNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PerlinNoise.class)
public class MixinPerlinFractalNoise implements IMixinPerlinFractalNoise {

    @Shadow @Final private ImprovedNoise[] noiseLevels;

    @Override
    public int octaveCount() {
        return this.noiseLevels == null ? 0 : this.noiseLevels.length;
    }

    /**
     * @author: K.jpg
     * @reason: Undoing wrap to make domain-rotation easier to inject. An alternate solution is included in MixinShelfEnabledNoise.java
     */
    @Overwrite
    public static double wrap(double d) {
        return d;
    }

}
