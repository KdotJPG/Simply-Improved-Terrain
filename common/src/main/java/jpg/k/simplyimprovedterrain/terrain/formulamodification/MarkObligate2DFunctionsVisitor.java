package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.Map;
import java.util.function.Predicate;

public enum MarkObligate2DFunctionsVisitor implements DensityFunction.Visitor {
    INSTANCE;

    @Override
    public DensityFunction apply(DensityFunction function) {
        Predicate<DensityFunction> obligationChecker = OBLIGATION_CHECKERS.get(function.getClass());

        return obligationChecker != null ?

                // Apply Cache2D if found an obligate 2D function.
                // If this is superfluous, it will be removed later.
                DensityFunctions.cache2d(new CourseAlteringNode(function)) :

                // Otherwise, pass through.
                function;
    }

    private static final Map<Class<? extends DensityFunction>, Predicate<DensityFunction>> OBLIGATION_CHECKERS = Map.of(
            DensityFunctions.EndIslandDensityFunction.class, (function) -> true
    );

}
