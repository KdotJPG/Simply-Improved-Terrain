package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;

enum AddCourseAlteringNodesVisitor implements DensityFunction.Visitor {
    INSTANCE;
    public DensityFunction apply(DensityFunction function) {
        return new CourseAlteringNode(function);
    }
}
