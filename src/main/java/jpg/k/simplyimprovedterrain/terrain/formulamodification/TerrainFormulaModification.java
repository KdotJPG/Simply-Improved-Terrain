package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.SimplyImprovedTerrain;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class TerrainFormulaModification {

    public static void bootstrap() {
        SimplyImprovedTerrain.register(Registry.DENSITY_FUNCTION_TYPES, CourseAlteringNode.SERIALIZED_NAME, CourseAlteringNode.CODEC.codec());
    }

    public static NoiseRouter translateFormula(NoiseRouter noiseRouter, Registry<NormalNoise.NoiseParameters> noiseParametersRegistry) {
        return translateFormula(noiseRouter, NoiseRouter::mapAll, noiseParametersRegistry);
    }

    private static <T> T translateFormula(T subject, BiFunction<T, DensityFunction.Visitor, T> mapAllFunction, Registry<NormalNoise.NoiseParameters> noiseParametersRegistry) {
        subject = mapAllFunction.apply(subject, RemoveHoldersVisitor.INSTANCE);
        subject = mapAllFunction.apply(subject, AddCourseAlteringNodesVisitor.INSTANCE);
        subject = mapAllFunction.apply(subject, CleanUpMarkersVisitor.DEFAULT);
        subject = mapAllFunction.apply(subject, SimplifyConstantExpressionsVisitor.INSTANCE);
        //subject = mapAllFunction.apply(subject, ConsolidateNestedAssociativeFunctionsVisitor.INSTANCE); // TODO
        subject = mapAllFunction.apply(subject, ReplaceNoiseNodesVisitor.INSTANCE);
        subject = mapAllFunction.apply(subject, new FillNoiseHoldersVisitor(noiseParametersRegistry));
        subject = mapAllFunction.apply(subject, SeparateNoiseInterpolationsVisitor.INSTANCE);
        //subject = mapAllFunction.apply(subject, ConsolidateNestedAssociativeFunctionsVisitor.INSTANCE); // TODO
        //subject = mapAllFunction.apply(subject, ConsolidateApprovedInterpolationsVisitor.INSTANCE); // TODO
        //subject = mapAllFunction.apply(subject, new ReCacheVisitor()); // TODO
        subject = mapAllFunction.apply(subject, RemoveCourseAlteringNodesVisitor.INSTANCE);

        return subject;
    }
}
