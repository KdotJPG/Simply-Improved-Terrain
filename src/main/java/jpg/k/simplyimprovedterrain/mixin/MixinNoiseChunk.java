package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.mixinapi.IMixinNoiseChunk;
import jpg.k.simplyimprovedterrain.terrain.IrreguLerper;
import jpg.k.simplyimprovedterrain.terrain.IrreguLerperFactory;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunk.class)
public class MixinNoiseChunk implements IMixinNoiseChunk {

    @Shadow @Final private NoiseSettings noiseSettings;
    @Shadow @Final int cellWidth;
    @Shadow @Final int cellHeight;
    @Shadow @Final int cellCountY;

    @Shadow @Final private int firstCellX;
    @Shadow int cellNoiseMinY;
    @Shadow @Final private int firstCellZ;
    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;

    private IrreguLerper irreguLerper = null;
    private IrreguLerper.ChunkColumnSampler columnSampler = null;
    private IrreguLerper.ChunkColumnSampler.Registrar columnSamplerRegistrar = null;

    @Override
    public void setLocalXZ(int x, int z) {
        int cellStartBlockXLocal = x & ~(this.cellWidth - 1);
        int cellStartBlockZLocal = z & ~(this.cellWidth - 1);
        this.cellStartBlockX = cellStartBlockXLocal + this.firstCellX * this.cellWidth;
        this.cellStartBlockZ = cellStartBlockZLocal + this.firstCellZ * this.cellWidth;
        this.inCellX = x - cellStartBlockXLocal;
        this.inCellZ = z - cellStartBlockZLocal;
        this.columnSampler.setXZ(x, z);
    }

    @Override
    public void setGlobalY(int y) {
        this.cellStartBlockY = y & (this.cellHeight - 1);
        this.inCellY = y - this.cellStartBlockY;
        this.columnSampler.setY(y - this.cellNoiseMinY * this.cellHeight);
    }

    @Inject(method = "<init>(IIILnet/minecraft/world/level/levelgen/NoiseRouter;IILnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)V", at = @At("TAIL"))
    private void injectConstructor(int cellCountXZ, int cellCountY, int cellNoiseMinY, NoiseRouter noiseRouter, int chunkStartX, int chunkStartZ, DensityFunctions.BeardifierOrMarker beardifierOrMarker, NoiseGeneratorSettings noiseGeneratorSettings, Aquifer.FluidPicker fluidPicker, Blender blender, CallbackInfo callbackInfo) {
        if (this.columnSamplerRegistrar == null) return;
        this.columnSamplerRegistrar.commit(chunkStartX, cellNoiseMinY * this.cellHeight, chunkStartZ, (NoiseChunk)(Object)this);
        this.columnSamplerRegistrar = null;
    }

    @Inject(method = "stopInterpolation()V", at = @At("HEAD"), cancellable = true)
    private void injectStopInterpolation(CallbackInfo callbackInfo) {
        if (this.columnSampler == null)
            throw new IllegalStateException("Trying to stop irregulerping when the column sampler was never assigned.");
        this.columnSampler.stopRunning();
        callbackInfo.cancel();
    }

    @Inject(method = "wrapNew(Lnet/minecraft/world/level/levelgen/DensityFunction;)Lnet/minecraft/world/level/levelgen/DensityFunction;", at = @At("HEAD"), cancellable = true)
    private void injectWrapNew(DensityFunction densityFunction, CallbackInfoReturnable<DensityFunction> cir) {

        if (this.irreguLerper == null) {
            this.irreguLerper = IrreguLerperFactory.get(0, this.cellWidth, this.cellHeight, this.cellCountY); // TODO seed
            this.columnSampler = null;
            this.columnSamplerRegistrar = null;
        }

        if (this.columnSampler == null) {
            this.columnSampler = irreguLerper.chunkColumnSampler();
            this.columnSamplerRegistrar = null;
        }

        if (this.columnSamplerRegistrar == null) {
            this.columnSamplerRegistrar = this.columnSampler.beginRegistration();
        }

        if (densityFunction instanceof DensityFunctions.Marker) {
            DensityFunctions.Marker marker = (DensityFunctions.Marker)densityFunction;
            switch (marker.type()) {
                case CacheOnce:
                    cir.setReturnValue(this.columnSamplerRegistrar.registerCellular(marker.wrapped()));
                    break;
                case Interpolated:
                    cir.setReturnValue(this.columnSamplerRegistrar.registerInterpolated(marker.wrapped()));
                    break;
                case Cache2D:
                case FlatCache:
                    // TODO cache
                    cir.setReturnValue(marker.wrapped());
                    break;
                case CacheAllInCell:
                    // TODO cache
                    cir.setReturnValue(marker.wrapped());
                    break;
            }
            return;
        }
    }

}


