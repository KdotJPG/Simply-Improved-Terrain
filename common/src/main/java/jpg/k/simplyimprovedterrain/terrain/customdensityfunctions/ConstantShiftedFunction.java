package jpg.k.simplyimprovedterrain.terrain.customdensityfunctions;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.apache.commons.lang3.NotImplementedException;

public record ConstantShiftedFunction(DensityFunction wrapped, int shiftX, int shiftY, int shiftZ) implements DensityFunction {

    @Override
    public double compute(FunctionContext functionContext) {
        return wrapped.compute(new SinglePointContext(
                functionContext.blockX() + shiftX,
                functionContext.blockY() + shiftY,
                functionContext.blockZ() + shiftZ
        ));
    }

    @Override
    public void fillArray(double[] values, ContextProvider contextProvider) {
        wrapped.fillArray(values, contextProvider);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ConstantShiftedFunction(wrapped.mapAll(visitor), shiftX, shiftY, shiftZ));
    }

    @Override
    public double minValue() {
        return wrapped.minValue();
    }

    @Override
    public double maxValue() {
        return wrapped.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        throw new NotImplementedException(); // Not used in any serialized trees
    }
}
