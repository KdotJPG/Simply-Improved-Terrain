package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public enum RemoveHoldersVisitor implements DensityFunction.Visitor {
    INSTANCE;

    public DensityFunction apply(DensityFunction function) {
        return function instanceof DensityFunctions.HolderHolder holderHolder ?

            // If the function is a HolderHolder, return its containing function.
            holderHolder.function().value() :

            // Otherwise, don't change.
            function;
    }
}
