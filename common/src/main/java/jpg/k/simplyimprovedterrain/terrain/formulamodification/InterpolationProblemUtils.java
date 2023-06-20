package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.SplitBlendedNoise;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

final class InterpolationProblemUtils {

    public static InterpolationProblem checkInterpolationProblem(DensityFunction function) {
        Function<DensityFunction, InterpolationProblem> problemChecker = PROBLEM_CHECKERS.get(function.getClass());

        return problemChecker != null ?
                problemChecker.apply(function) :
                InterpolationProblem.NO_PROBLEM;
    }

    public enum InterpolationProblem {
        NO_PROBLEM, NOT_IN_UNAPPROVED_FUNCTIONS, NEVER_INTERPOLATE
    }

    private static final Map<Class<? extends DensityFunction>, Function<DensityFunction, InterpolationProblem>> PROBLEM_CHECKERS;
    static {
        Map<Class<? extends DensityFunction>, Function<DensityFunction, InterpolationProblem>> problemCheckers = new HashMap<>();
        problemCheckers.put(BlendedNoise.class, (DensityFunction function) -> InterpolationProblem.NOT_IN_UNAPPROVED_FUNCTIONS);
        problemCheckers.put(DensityFunctions.Marker.class, (DensityFunction function) -> {
            if (!(function instanceof DensityFunctions.Marker marker)) return InterpolationProblem.NO_PROBLEM;
            return marker.type() == DensityFunctions.Marker.Type.Cache2D ?
                    InterpolationProblem.NEVER_INTERPOLATE :
                    InterpolationProblem.NO_PROBLEM;
        });
        problemCheckers.put(DensityFunctions.Noise.class, (DensityFunction function) -> InterpolationProblem.NOT_IN_UNAPPROVED_FUNCTIONS);
        problemCheckers.put(DensityFunctions.EndIslandDensityFunction.class, (DensityFunction function) -> InterpolationProblem.NEVER_INTERPOLATE);
        problemCheckers.put(DensityFunctions.ShiftA.class, (DensityFunction function) -> InterpolationProblem.NOT_IN_UNAPPROVED_FUNCTIONS);
        problemCheckers.put(DensityFunctions.ShiftB.class, (DensityFunction function) -> InterpolationProblem.NOT_IN_UNAPPROVED_FUNCTIONS);
        problemCheckers.put(DensityFunctions.Shift.class, (DensityFunction function) -> InterpolationProblem.NOT_IN_UNAPPROVED_FUNCTIONS);
        problemCheckers.put(DensityFunctions.ShiftedNoise.class, (DensityFunction function) -> InterpolationProblem.NOT_IN_UNAPPROVED_FUNCTIONS);
        problemCheckers.put(DensityFunctions.YClampedGradient.class, (DensityFunction function) -> InterpolationProblem.NEVER_INTERPOLATE);
        problemCheckers.put(DensityFunctions.WeirdScaledSampler.class, (DensityFunction function) -> InterpolationProblem.NOT_IN_UNAPPROVED_FUNCTIONS);
        problemCheckers.put(SplitBlendedNoise.BlendedNoisePart.class, (DensityFunction function) -> InterpolationProblem.NOT_IN_UNAPPROVED_FUNCTIONS);
        PROBLEM_CHECKERS = problemCheckers;
    }

}
