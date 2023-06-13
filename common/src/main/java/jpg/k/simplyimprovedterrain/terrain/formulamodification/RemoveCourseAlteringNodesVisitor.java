package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;

enum RemoveCourseAlteringNodesVisitor implements DensityFunction.Visitor {
    INSTANCE;

    public DensityFunction apply(DensityFunction function) {
        return (function instanceof CourseAlteringNode courseAlteringNode) ?
                courseAlteringNode.wrapped() :
                function;
    }
}
