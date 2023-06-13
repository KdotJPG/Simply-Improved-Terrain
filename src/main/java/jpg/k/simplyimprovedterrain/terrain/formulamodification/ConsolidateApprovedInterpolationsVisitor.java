package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;

enum ConsolidateApprovedInterpolationsVisitor implements DensityFunction.Visitor {
    INSTANCE;

    public DensityFunction apply(DensityFunction function) {
        NoiseWrappingFunctionsApprovedUnderInterpolationUtils.Handler handler =
                NoiseWrappingFunctionsApprovedUnderInterpolationUtils.handlerFor(function);

        // If function is approved to be interpolated on the outside and have noise on the inside,
        // perform any such transformation if possible. Otherwise pass through.
        return handler.dragUpInterpolationsOrPassThrough(function);
    }
}
