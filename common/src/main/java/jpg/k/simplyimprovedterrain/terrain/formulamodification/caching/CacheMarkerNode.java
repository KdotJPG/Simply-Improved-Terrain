package jpg.k.simplyimprovedterrain.terrain.formulamodification.caching;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.apache.commons.lang3.NotImplementedException;

public record CacheMarkerNode(int index) implements DensityFunction.SimpleFunction {

    @Override
    public double compute(FunctionContext functionContext) {
        return 0;
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return null;
    }
}
