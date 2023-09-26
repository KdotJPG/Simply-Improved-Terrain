package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.MultiArgumentSimpleFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

final class NoiseWrappingFunctionsApprovedUnderInterpolationUtils {
    public record Handler(
            Predicate<DensityFunction> approvalPredicate,
            Function<DensityFunction, DensityFunction> interpolationUpDragger
    ) {
        private static final Handler DEFAULT = new Handler((function) -> false, (function) -> function);

        public boolean isApproved(DensityFunction function) {
            return approvalPredicate.test(function);
        }

        public DensityFunction dragUpInterpolations(DensityFunction function) {
            return interpolationUpDragger.apply(function);
        }

        public DensityFunction dragUpInterpolationsOrPassThrough(DensityFunction function) {
            return isApproved(function) ?

                    // If function is approved to be interpolated on the outside and have noise on the inside,
                    // perform any such transformation if possible.
                    dragUpInterpolations(function) :

                    // Otherwise, no.
                    function;
        }
    }

    public static boolean isApproved(DensityFunction function) {
        return handlerFor(function).isApproved(function);
    }

    public static Handler handlerFor(DensityFunction function) {
        return HANDLERS.getOrDefault(function.getClass(), Handler.DEFAULT);
    }

    private static DensityFunction notInterpolated(DensityFunction function) {
        DensityFunction actualFunction = CourseAlteringNode.unwrap(function);

        return (actualFunction instanceof DensityFunctions.Marker marker
                && marker.type() == DensityFunctions.Marker.Type.Interpolated) ?

                // If this function is an interpolation marker, return what's inside it.
                marker.wrapped() :

                // Otherwise return what we passed in.
                function;
    }

    private static boolean isInterpolatedOrConstant(DensityFunction function) {
        DensityFunction actualFunction = CourseAlteringNode.unwrap(function);
        return actualFunction instanceof DensityFunctions.Marker marker && marker.type() == DensityFunctions.Marker.Type.Interpolated ||
                actualFunction instanceof DensityFunctions.Constant;
    }

    private static final Map<Class<? extends DensityFunction>, Handler> HANDLERS = Map.of(
            DensityFunctions.Ap2.class, new Handler(
                    (DensityFunction function) ->
                            function instanceof DensityFunctions.Ap2 twoArgumentSimpleFunction &&
                            DensityFunctions.TwoArgumentSimpleFunction.Type.ADD == twoArgumentSimpleFunction.type(),
                    (DensityFunction function) -> {
                        return function instanceof DensityFunctions.TwoArgumentSimpleFunction twoArgumentSimpleFunction &&
                                isInterpolatedOrConstant(twoArgumentSimpleFunction.argument1()) && isInterpolatedOrConstant(twoArgumentSimpleFunction.argument2()) ?

                                // If both arguments are interpolated or constant, we can wrap the whole addition with `interpolated` instead.
                                DensityFunctions.interpolated(DensityFunctions.add(
                                        notInterpolated(twoArgumentSimpleFunction.argument1()), notInterpolated(twoArgumentSimpleFunction.argument2())
                                )) :

                                // Otherwise, make no modification.
                                function;
                    }
            ),
            MultiArgumentSimpleFunction.class, new Handler(
                    (DensityFunction function) ->
                            function instanceof MultiArgumentSimpleFunction multiArgumentSimpleFunction &&
                                    DensityFunctions.TwoArgumentSimpleFunction.Type.ADD == multiArgumentSimpleFunction.type(),
                    (DensityFunction function) -> {

                        if (!(function instanceof MultiArgumentSimpleFunction multiArgumentSimpleFunction)) return function;

                        // Multi-adds may have some arguments that are interpolated/constant, and some not.
                        // Here, we can create group additions of the interpolated/constant ones while leaving the non-interpolatable ones out.
                        List<DensityFunction> addendsToInterpolate = new ArrayList<DensityFunction>();
                        List<DensityFunction> addendsNotToInterpolate = new ArrayList<DensityFunction>();

                        // Assign each argument to one of the above two lists.
                        for (DensityFunction argument : multiArgumentSimpleFunction.arguments()) {
                            if (isInterpolatedOrConstant(argument)) {
                                addendsToInterpolate.add(notInterpolated(argument));
                            } else {
                                addendsNotToInterpolate.add(argument);
                            }
                        }

                        // If there are two or more interpolated/constant addends, combine them.
                        if (addendsToInterpolate.size() >= 2) {
                            DensityFunction functionToInterpolate = MultiArgumentSimpleFunction.createSimplfied(
                                    DensityFunctions.TwoArgumentSimpleFunction.Type.ADD, addendsToInterpolate
                            );
                            addendsNotToInterpolate.add(DensityFunctions.interpolated(new CourseAlteringNode(functionToInterpolate)));

                            return MultiArgumentSimpleFunction.createSimplfied(
                                    DensityFunctions.TwoArgumentSimpleFunction.Type.ADD, addendsNotToInterpolate
                            );
                        }

                        // Otherwise, return the function as it was.
                        return function;
                    }
            ),
            DensityFunctions.MulOrAdd.class, new Handler(
                    (DensityFunction function) ->
                            function instanceof DensityFunctions.MulOrAdd,
                    (DensityFunction function) -> {
                        return function instanceof DensityFunctions.MulOrAdd mulOrAdd && isInterpolatedOrConstant(mulOrAdd.input()) ?

                                switch (mulOrAdd.specificType()) {
                                    case MUL -> DensityFunctions.interpolated(DensityFunctions.mul(
                                            notInterpolated(mulOrAdd.input()), DensityFunctions.constant(mulOrAdd.argument())
                                    ));
                                    case ADD -> DensityFunctions.interpolated(DensityFunctions.add(
                                            notInterpolated(mulOrAdd.input()), DensityFunctions.constant(mulOrAdd.argument())
                                    ));
                                } :

                                // Otherwise, make no modification.
                                function;
                    }
            ),
            DensityFunctions.ShiftedNoise.class, new Handler(
                    (DensityFunction function) -> true, // Allow ShiftedNoise to be interpolated, even if its arguments contain noise (which they generally will)
                    (DensityFunction function) -> function.mapAll(InterpolationRelegationVisitor.INSTANCE) // But don't make that become the case when it's not.
            )
    );
}
