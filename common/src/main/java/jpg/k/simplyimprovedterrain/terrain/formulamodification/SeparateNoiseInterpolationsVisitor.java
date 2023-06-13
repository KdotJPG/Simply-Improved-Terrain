package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.*;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

enum SeparateNoiseInterpolationsVisitor implements CourseAlteringVisitor {
    INSTANCE;

    public DensityFunction mapAllFor(DensityFunction function) {

        // Only consider `Interpolated` markers.
        if (!(function instanceof DensityFunctions.Marker marker) ||
                DensityFunctions.Marker.Type.Interpolated != marker.type()) return function.mapAll(this);

        CheckForNoiseInUnapprovedInterpolatedFunctionsVisitor checkVisitor = new CheckForNoiseInUnapprovedInterpolatedFunctionsVisitor();
        function.mapAll(checkVisitor);
        CheckForNoiseInUnapprovedInterpolatedFunctionsVisitor.State checkResult = checkVisitor.state();

        return checkResult == CheckForNoiseInUnapprovedInterpolatedFunctionsVisitor.State.FOUND_NOISE_IN_UNAPPROVED_FUNCTION ?

                // If this tree path contains noise inside unapproved functions, push it down then keep traversing.
                interpolateArguments(CourseAlteringNode.unwrap(marker.wrapped())).mapAll(this) :

                // Otherwise no need to traverse the path (note lack of `.mapAll()`)
                function;
    }

    public DensityFunction apply(DensityFunction densityFunction) {
        return densityFunction;
    }

    // Should be called on a true function, not a CourseAlteringNode.
    private DensityFunction interpolateArguments(DensityFunction function) {
        Function<DensityFunction, DensityFunction> transformer = TRANSFORMED_ARGUMENT_INTERPOLATORS.get(function.getClass());

        return transformer != null ?

                // Apply specialized interpolation push-down transformer
                transformer.apply(function) :

                // Apply generic transformer
                defaultInterpolateArguments(function);
    }

    private DensityFunction defaultInterpolateArguments(DensityFunction function) {
        return function.mapAll(GenericInterpolateArgumentsVisitor.INSTANCE);
    }

    private DensityFunction wrappedInterpolated(DensityFunction function) {
        return new CourseAlteringNode(DensityFunctions.interpolated(function));
    }

    private Map<Class<? extends DensityFunction>, Function<DensityFunction, DensityFunction>> TRANSFORMED_ARGUMENT_INTERPOLATORS = Map.of(
            DensityFunctions.Ap2.class, (DensityFunction function) -> {
                if (!(function instanceof DensityFunctions.Ap2 twoArgumentSimpleFunction)) return defaultInterpolateArguments(function);
                return switch (twoArgumentSimpleFunction.type()) {
                    case MIN, MAX -> {

                        // Faster rates of change mean larger smoothing factors needed to stand in for interpolation.
                        double factor1 = SamplingUtils.calculateInterpolationSmoothingFactor(twoArgumentSimpleFunction.argument1());
                        double factor2 = SamplingUtils.calculateInterpolationSmoothingFactor(twoArgumentSimpleFunction.argument2());

                        List<MultiSmoothMinOrMax.Entry> entries = List.of(
                                new MultiSmoothMinOrMax.Entry(wrappedInterpolated(twoArgumentSimpleFunction.argument1()), factor1),
                                new MultiSmoothMinOrMax.Entry(wrappedInterpolated(twoArgumentSimpleFunction.argument2()), factor2)
                        );

                        yield MultiSmoothMinOrMax.create(entries, twoArgumentSimpleFunction.type() == DensityFunctions.TwoArgumentSimpleFunction.Type.MIN);
                    }
                    default -> defaultInterpolateArguments(function);
                };
            },
            DensityFunctions.RangeChoice.class, (DensityFunction function) -> {
                if (!(function instanceof DensityFunctions.RangeChoice rangeChoice)) return defaultInterpolateArguments(function);
                double factor = SamplingUtils.calculateInterpolationSmoothingFactor(rangeChoice.input());
                return SmoothRangeChoice.create(
                        wrappedInterpolated(rangeChoice.input()),
                        rangeChoice.minInclusive(), rangeChoice.maxExclusive(), factor,
                        wrappedInterpolated(rangeChoice.whenInRange()),
                        wrappedInterpolated(rangeChoice.whenOutOfRange())
                );
            },
            DensityFunctions.Clamp.class, (DensityFunction function) -> {
                if (!(function instanceof DensityFunctions.Clamp clamp)) return defaultInterpolateArguments(function);
                double factor = SamplingUtils.calculateInterpolationSmoothingFactor(clamp.input());
                return SmoothClamp.create(wrappedInterpolated(clamp.input()), clamp.minValue(), clamp.maxValue(), factor);
            },
            DensityFunctions.Mapped.class, (DensityFunction function) -> {
                if (!(function instanceof DensityFunctions.Mapped mapped)) return defaultInterpolateArguments(function);
                return switch (mapped.type()) {
                    case ABS, HALF_NEGATIVE, QUARTER_NEGATIVE, SQUEEZE -> {
                        double factor = SamplingUtils.calculateInterpolationSmoothingFactor(mapped.input());
                        yield SmoothMapped.create(mapped.type(), wrappedInterpolated(mapped.input()), factor);
                    }
                    default -> defaultInterpolateArguments(function);
                };
            }
    );

    // Should be called on a true function, not a CourseAlteringNode.
    private enum GenericInterpolateArgumentsVisitor implements CourseAlteringVisitor {
        INSTANCE;

        public DensityFunction mapAllFor(DensityFunction function) {

            // no mapAll, so no recursion
            return DensityFunctions.interpolated(new CourseAlteringNode(function));

        }

        public DensityFunction apply(DensityFunction function) {
            return function;
        }
    }

    private static class CheckForNoiseInUnapprovedInterpolatedFunctionsVisitor implements CourseAlteringVisitor {

        private State state;

        public CheckForNoiseInUnapprovedInterpolatedFunctionsVisitor() {
            this.state = State.NONE;
        }

        public State state() {
            return state;
        }

        public DensityFunction mapAllFor(DensityFunction function) {

            // If we've found one, nothing further to check.
            if (state == State.FOUND_NOISE_IN_UNAPPROVED_FUNCTION) {
                return function;
            }

            // If we've found noise, mark it.
            if (NoiseFunctionUtils.isNoise(function)) {
                state = State.FOUND_NOISE;
                return function; // We expect noise to be leaf nodes only, so don't traverse further.
            }

            // Use new visitor instance just for this subtree.
            CheckForNoiseInUnapprovedInterpolatedFunctionsVisitor subVisitor = new CheckForNoiseInUnapprovedInterpolatedFunctionsVisitor();
            DensityFunction mapped = function.mapAll(subVisitor);

            switch (subVisitor.state) {

                // If nothing of note found, we're fine for the moment.
                case NONE:
                    break;

                // If we found noise, determine whether this function is approved to contain it under interpolation.
                case FOUND_NOISE:
                    state = NoiseWrappingFunctionsApprovedUnderInterpolationUtils.isApproved(function) ?
                            State.FOUND_NOISE :
                            State.FOUND_NOISE_IN_UNAPPROVED_FUNCTION;
                    break;

                // If we already found noise in an umapproved function, pass the message along.
                case FOUND_NOISE_IN_UNAPPROVED_FUNCTION:
                    state = State.FOUND_NOISE_IN_UNAPPROVED_FUNCTION;
                    break;
            }

            return mapped;
        }

        public DensityFunction apply(DensityFunction densityFunction) {
            return densityFunction;
        }

        public enum State {
            NONE, FOUND_NOISE, FOUND_NOISE_IN_UNAPPROVED_FUNCTION
        }
    }

}
