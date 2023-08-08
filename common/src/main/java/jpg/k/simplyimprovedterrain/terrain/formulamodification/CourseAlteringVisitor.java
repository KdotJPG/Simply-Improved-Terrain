package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface CourseAlteringVisitor extends DensityFunction.Visitor {
    default DensityFunction mapAllFor(DensityFunction function) {
        return function.mapAll(this);
    }
}
