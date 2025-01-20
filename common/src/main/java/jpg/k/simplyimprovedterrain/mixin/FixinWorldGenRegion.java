package jpg.k.simplyimprovedterrain.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public class FixinWorldGenRegion {

    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At(value = "INVOKE", target = "Ljava/lang/IllegalStateException;<init>(Ljava/lang/String;)V"),
            cancellable = true
    )
    public void reimplementCheck(int x, int z, ChunkStatus chunkStatusMin, boolean crashOnFailure, CallbackInfoReturnable<ChunkAccess> callbackInfoReturnable) {
        if (!crashOnFailure) {
            callbackInfoReturnable.setReturnValue(null);
        }
    }

}
