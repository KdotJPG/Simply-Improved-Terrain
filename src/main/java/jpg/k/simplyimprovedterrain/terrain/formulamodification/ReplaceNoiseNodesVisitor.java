package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.SplitBlendedNoise;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

import java.util.Map;
import java.util.function.Function;

enum ReplaceNoiseNodesVisitor implements DensityFunction.Visitor {
    INSTANCE;

    private static final Map<Class<? extends DensityFunction>, Function<DensityFunction, DensityFunction>> REPLACEMENTS = Map.of(
            BlendedNoise.class, (DensityFunction function) -> {
                if (!(function instanceof BlendedNoise blendedNoise)) return function;

                DensityFunction blendNoiseMain = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MAIN_NOISE, blendedNoise);
                DensityFunction blendNoiseMinLimit = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MIN_LIMIT_NOISE, blendedNoise);
                DensityFunction blendNoiseMaxLimit = SplitBlendedNoise.BlendedNoisePart.create(SplitBlendedNoise.BlendedNoisePart.Type.MAX_LIMIT_NOISE, blendedNoise);

                // TODO rangeChoice?

                // Return uninterpolated. If the BlendedNoise instance lies within `interpolated`,
                // then `SeparateNoiseInterpolationsVisitor` will push `interpolated` down to the three individual arguments.
                return SplitBlendedNoise.BlendedNoiseCombine.create(
                        new CourseAlteringNode(blendNoiseMinLimit),
                        new CourseAlteringNode(blendNoiseMaxLimit),
                        new CourseAlteringNode(blendNoiseMain)
                );
            }
    );

    public DensityFunction apply(DensityFunction function) {
        Function<DensityFunction, DensityFunction> transformer = REPLACEMENTS.get(function.getClass());
        return transformer != null ?
                transformer.apply(function) :
                function;
    }
}
