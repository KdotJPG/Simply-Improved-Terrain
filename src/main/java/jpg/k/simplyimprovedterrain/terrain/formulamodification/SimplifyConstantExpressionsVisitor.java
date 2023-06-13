package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.Map;
import java.util.function.Predicate;

enum SimplifyConstantExpressionsVisitor implements DensityFunction.Visitor {
    INSTANCE;

    private static final DensityFunction.FunctionContext ORIGIN = new DensityFunction.SinglePointContext(0, 0, 0);

    private static final Map<Class<? extends DensityFunction>, Predicate<DensityFunction>>
            KNOWN_CONSTANCY_PRESERVING_FUNCTION_CONSTANCY_PREDICATES = Map.of(
                DensityFunctions.Marker.class, (DensityFunction function) ->
                    function instanceof DensityFunctions.Marker marker && isConstant(marker.wrapped()),
                DensityFunctions.TwoArgumentSimpleFunction.class, (DensityFunction function) ->
                    function instanceof DensityFunctions.TwoArgumentSimpleFunction twoArgumentSimpleFunction &&
                    isConstant(twoArgumentSimpleFunction.argument1()) && isConstant(twoArgumentSimpleFunction.argument2()),
                DensityFunctions.RangeChoice.class, (DensityFunction function) ->
                    function instanceof DensityFunctions.RangeChoice rangeChoice &&
                    isConstant(rangeChoice.input()) && isConstant(rangeChoice.whenInRange()) &&
                    isConstant(rangeChoice.whenOutOfRange()),
                DensityFunctions.Clamp.class, (DensityFunction function) ->
                    function instanceof DensityFunctions.Clamp clamp && isConstant(clamp.input()),
                DensityFunctions.Spline.class, (DensityFunction function) -> true,
                DensityFunctions.Constant.class, (DensityFunction function) -> true
            );

    private static boolean isConstant(DensityFunction function) {
        return CourseAlteringNode.unwrap(function) instanceof DensityFunctions.Constant;
    }

    private static boolean isEffectivelyConstant(DensityFunction function) {
        Predicate<DensityFunction> constancyPredicate = KNOWN_CONSTANCY_PRESERVING_FUNCTION_CONSTANCY_PREDICATES.get(function);
        return constancyPredicate != null && constancyPredicate.test(function);
    }

    public DensityFunction apply(DensityFunction function) {
        return isEffectivelyConstant(function) ?

                // If the function is a constant(-preserving function of constants), replace with constant.
                DensityFunctions.constant(function.compute(ORIGIN)) :

                // Otherwise, don't change.
                function;
    }
}
