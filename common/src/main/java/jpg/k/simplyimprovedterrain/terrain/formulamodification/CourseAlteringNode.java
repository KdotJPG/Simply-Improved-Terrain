package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record CourseAlteringNode(DensityFunction wrapped) implements DensityFunction.SimpleFunction {
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {

        DensityFunction transformedFunction = (visitor instanceof CourseAlteringVisitor courseAlteringVisitor) ?
                courseAlteringVisitor.mapAllFor(wrapped) :
                wrapped.mapAll(visitor);

        return visitor.apply(new CourseAlteringNode(transformedFunction));
    }

    public double compute(DensityFunction.FunctionContext functionContext) {
        return wrapped.compute(functionContext);
    }

    public double minValue() {
        return wrapped.minValue();
    }

    public double maxValue() {
        return wrapped.maxValue();
    }

    public static DensityFunction unwrap(DensityFunction function) {
        return (function instanceof CourseAlteringNode node) ?
                node.wrapped() :
                function;
    }

    public static final String SERIALIZED_NAME = "course_altering_node";
    public static final KeyDispatchDataCodec<CourseAlteringNode> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("wrapped").forGetter(CourseAlteringNode::wrapped)
        ).apply(instance, CourseAlteringNode::new);
    }));

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
