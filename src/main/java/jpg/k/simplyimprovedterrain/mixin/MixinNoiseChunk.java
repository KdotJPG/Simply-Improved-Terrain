package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.mixinapi.IMixinNoiseChunk;
import jpg.k.simplyimprovedterrain.terrain.IrreguLerper;
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
    @Shadow @Final int cellCountXZ;
    @Shadow @Final int cellCountY;

    @Shadow @Final private int firstCellX;
    @Shadow @Final int cellNoiseMinY;
    @Shadow @Final private int firstCellZ;
    @Shadow private int cellStartBlockX;
    @Shadow int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow int inCellX;
    @Shadow int inCellY;
    @Shadow int inCellZ;

    private long irreguLerperSeed;
    private IrreguLerper irreguLerper = null;
    private IrreguLerper.Registrar irreguLerperRegistrar = null;

    @Override
    public void setLocalXZ(int x, int z) {
        int cellStartBlockXLocal = x & -this.cellWidth;
        int cellStartBlockZLocal = z & -this.cellWidth;
        this.cellStartBlockX = cellStartBlockXLocal + this.firstCellX * this.cellWidth;
        this.cellStartBlockZ = cellStartBlockZLocal + this.firstCellZ * this.cellWidth;
        this.inCellX = x - cellStartBlockXLocal;
        this.inCellZ = z - cellStartBlockZLocal;
        this.irreguLerper.setXZ(x, z);
    }

    @Override
    public void setGlobalY(int y) {
        this.cellStartBlockY = y & (this.cellHeight - 1);
        this.inCellY = y - this.cellStartBlockY;
        this.irreguLerper.setY(y - this.cellNoiseMinY * this.cellHeight);
    }

    @Inject(method = "<init>(IIILnet/minecraft/world/level/levelgen/NoiseRouter;IILnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)V", at = @At("TAIL"))
    private void injectConstructor(int cellCountXZ, int cellCountY, int cellNoiseMinY, NoiseRouter noiseRouter, int chunkStartX, int chunkStartZ, DensityFunctions.BeardifierOrMarker beardifierOrMarker, NoiseGeneratorSettings noiseGeneratorSettings, Aquifer.FluidPicker fluidPicker, Blender blender, CallbackInfo callbackInfo) {
        if (this.irreguLerperRegistrar == null) return;
        this.irreguLerperRegistrar.commit(chunkStartX, cellNoiseMinY * this.cellHeight, chunkStartZ, (NoiseChunk)(Object)this);
        this.irreguLerperRegistrar = null;

        // Ideally this would be the world seed, but this should be fine.
        this.irreguLerperSeed = noiseRouter.aquiferPositionalRandomFactory().at(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE).nextLong();
    }

    @Inject(method = "stopInterpolation()V", at = @At("HEAD"), cancellable = true)
    private void injectStopInterpolation(CallbackInfo callbackInfo) {
        if (this.irreguLerper == null)
            throw new IllegalStateException("Trying to stop irregulerping when the irregulerper field was never assigned to.");
        this.irreguLerper.stopRunning();
        callbackInfo.cancel();
    }

    @Inject(method = "wrapNew(Lnet/minecraft/world/level/levelgen/DensityFunction;)Lnet/minecraft/world/level/levelgen/DensityFunction;", at = @At("HEAD"), cancellable = true)
    private void injectWrapNew(DensityFunction densityFunction, CallbackInfoReturnable<DensityFunction> cir) {

        if (this.irreguLerper == null) {
            this.irreguLerper = new IrreguLerper(this.irreguLerperSeed, this.cellCountXZ * this.cellWidth, this.cellHeight * this.cellCountY, this.cellWidth, this.cellHeight);
            this.irreguLerperRegistrar = null;
        }

        if (this.irreguLerperRegistrar == null) {
            this.irreguLerperRegistrar = this.irreguLerper.beginRegistration();
        }

        if (densityFunction instanceof DensityFunctions.Marker) {
            DensityFunctions.Marker marker = (DensityFunctions.Marker)densityFunction;
            switch (marker.type()) {
                case CacheOnce:
                    cir.setReturnValue(this.irreguLerperRegistrar.registerCellular(marker.wrapped()));
                    break;
                case Interpolated:
                    cir.setReturnValue(this.irreguLerperRegistrar.registerInterpolated(marker.wrapped()));
                    break;
                case Cache2D:
                    // Vanilla Cache2D is fine.
                    break;
                case FlatCache:
                    // TODO cache
                    cir.setReturnValue(marker.wrapped());
                    break;
                case CacheAllInCell:
                    // TODO cache
                    cir.setReturnValue(marker.wrapped());
                    break;
            }
        }
    }

}


