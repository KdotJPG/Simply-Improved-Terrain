package jpg.k.simplyimprovedterrain.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jpg.k.simplyimprovedterrain.biome.BiomeFiddleHelper;
import jpg.k.simplyimprovedterrain.biome.FiddledBiomeResolver;
import jpg.k.simplyimprovedterrain.mixinapi.IMixinClimateSampler;
import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.ConstantShiftedFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(TheEndBiomeSource.class)
public abstract class MixinTheEndBiomeSource implements FiddledBiomeResolver {

    private static final int OUTER_ISLANDS_BLOCK_DISTANCE = 1024;

    // Aliases are a workaround for a loom issue.
    @Shadow(aliases = {"getNoiseBiome", "method_38109", "m_203407_"}, remap = false)
    public abstract Holder<Biome> getNoiseBiome(int xQuart, int yQuart, int zQuart, Climate.Sampler sampler);

    @Override
    public Holder<Biome> getFiddledNoiseBiome(int xQuart, int yQuart, int zQuart, Climate.Sampler sampler, long fiddleSeed) {
        return BiomeFiddleHelper.sampleFiddled(xQuart, yQuart, zQuart, fiddleSeed,
                (xBlock, yBlock, zBlock) -> {
                    int shiftX = xBlock - QuartPos.toBlock(xQuart);
                    int shiftY = yBlock - QuartPos.toBlock(yQuart);
                    int shiftZ = zBlock - QuartPos.toBlock(zQuart);
                    return this.getNoiseBiome(
                            xQuart, yQuart, zQuart,
                            ((IMixinClimateSampler)(Object) sampler).transformAll(function -> new ConstantShiftedFunction(function, shiftX, shiftY, shiftZ))
                    );
                }
        );
    }

    @WrapOperation(method = {"getNoiseBiome", "method_38109", "m_203407_"}, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/DensityFunction;compute(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D",
            remap = true
    ))
    private double replaceDensityFunctionPoint(DensityFunction function, DensityFunction.FunctionContext originalContext, Operation<Double> original, int xQuart, int yQuart, int zQuart, Climate.Sampler sampler) {
        return original.call(
                function,

                // Remaining granularity covered by offsets in `ConstantShiftedFunction` if reached via `getFiddledNoiseBiome`
                new DensityFunction.SinglePointContext(
                        QuartPos.toBlock(xQuart),
                        QuartPos.toBlock(yQuart),
                        QuartPos.toBlock(zQuart)
                )
        );
    }

    @WrapOperation(method = {"getNoiseBiome", "method_38109", "m_203407_"}, remap = false, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;blockToSectionCoord(I)I",
            ordinal = 0,
            remap = true
    ))
    private int replaceOriginDistanceComponentX(int value, Operation<Integer> original, int xQuart, int yQuart, int zQuart, Climate.Sampler sampler) {
        int xBlock = QuartPos.toBlock(xQuart);
        if (sampler.erosion() instanceof ConstantShiftedFunction constantShiftedFunction) {
            xBlock += constantShiftedFunction.shiftX();
        }
        return xBlock;
    }

    @WrapOperation(method = {"getNoiseBiome", "method_38109", "m_203407_"}, remap = false, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;blockToSectionCoord(I)I",
            ordinal = 1,
            remap = true
    ))
    private int replaceOriginDistanceComponentZ(int value, Operation<Integer> original, int xQuart, int yQuart, int zQuart, Climate.Sampler sampler) {
        int zBlock = QuartPos.toBlock(zQuart);
        if (sampler.erosion() instanceof ConstantShiftedFunction constantShiftedFunction) {
            zBlock += constantShiftedFunction.shiftZ();
        }
        return zBlock;
    }

    @ModifyConstant(method = {"getNoiseBiome", "method_38109", "m_203407_"}, remap = false, constant = @Constant(longValue = 4096L))
    private long replaceSquaredOriginDistanceConstant(long value) {
        return OUTER_ISLANDS_BLOCK_DISTANCE * (OUTER_ISLANDS_BLOCK_DISTANCE + 1);
    }
}
