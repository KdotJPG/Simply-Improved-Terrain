package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.feature.structure.RuinedPortalPiece;
import net.minecraft.world.gen.feature.structure.StructurePiece;

@Mixin(RuinedPortalPiece.class)
public class MixinRuinedPortalPiece {

    @Shadow
    private @Final RuinedPortalPiece.Location field_237007_h_;

    @Shadow
    private @Final RuinedPortalPiece.Serializer field_237008_i_;

    @Inject(method = "func_237019_b_", at = @At("HEAD"), cancellable = true)
    public void inject_func_237019_b_(Random random, IWorld world, CallbackInfo ci) {
        MutableBoundingBox boundingBox = ((StructurePiece)(Object)this).getBoundingBox();

        boolean flag = this.field_237007_h_ == RuinedPortalPiece.Location.ON_LAND_SURFACE || this.field_237007_h_ == RuinedPortalPiece.Location.ON_OCEAN_FLOOR;
        Vector3i center = boundingBox.func_215126_f();
        int cx = center.getX();
        int cz = center.getZ();
        float[] falloffThresholds = new float[]{1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F, 0.9F, 0.8F, 0.7F, 0.6F, 0.4F, 0.2F};
        int nFalloffThresholds = falloffThresholds.length;
        int averageWidth = (boundingBox.getXSize() + boundingBox.getZSize()) / 2;
        int falloffIndexOffset = random.nextInt(Math.max(1, 8 - averageWidth / 2));

        BlockPos.Mutable currentBlockPos = BlockPos.ZERO.func_239590_i_();
        int nFalloffThresholdsSq = nFalloffThresholds * nFalloffThresholds;
        float invNFalloffThresholdsSq = 1.0f / nFalloffThresholdsSq;

        for(int x = cx - nFalloffThresholds; x <= cx + nFalloffThresholds; ++x) {
            for(int z = cz - nFalloffThresholds; z <= cz + nFalloffThresholds; ++z) {

                // Euclidean Distance Squared
                float euclideanFalloff = (x - cx) * (x - cx) + (z - cz) * (z - cz);

                // Re-use existing falloff threshold array by converting this value.
                int falloffIndex;
                if (euclideanFalloff > nFalloffThresholdsSq) falloffIndex = nFalloffThresholds;
                else {

                    // Alter the squared distance curve to be closer to true Euclidean distance, without using an expensive sqrt call.
                    euclideanFalloff *= invNFalloffThresholdsSq; // First, rescale to 0 to 1 in desired range
                    euclideanFalloff = 1 - (1 - euclideanFalloff) * (1 - euclideanFalloff); // Apply 1-(1-t)^2 curve to counter some of the parabolic curve
                    euclideanFalloff *= nFalloffThresholds; // Scale back up to the desired range, true to the non-squared distance.

                    // Truncate to int to re-use existing threshold array.
                    falloffIndex = (int)euclideanFalloff;
                }

                int boundedOffsetIndex = Math.max(0, falloffIndex + falloffIndexOffset);
                if (boundedOffsetIndex < nFalloffThresholds) {
                    float threshold = falloffThresholds[boundedOffsetIndex];
                    if (random.nextDouble() < (double)threshold) {
                        int y = func_237009_a_(world, x, z, this.field_237007_h_);
                        int yBounded = flag ? y : Math.min(boundingBox.minY, y);
                        currentBlockPos.setPos(x, yBounded, z);
                        if (Math.abs(yBounded - boundingBox.minY) <= 3 && this.func_237010_a_(world, currentBlockPos)) {
                            this.func_237023_d_(random, world, currentBlockPos);
                            if (this.field_237008_i_.field_237028_e_) {
                                this.func_237020_b_(random, world, currentBlockPos);
                            }

                            this.func_237022_c_(random, world, currentBlockPos.down());
                        }
                    }
                }
            }
        }

        ci.cancel();
    }

    @Shadow
    private static int func_237009_a_(IWorld p_237009_0_, int p_237009_1_, int p_237009_2_, RuinedPortalPiece.Location p_237009_3_) {
        throw new AssertionError();
    }

    @Shadow
    private boolean func_237010_a_(IWorld p_237010_1_, BlockPos p_237010_2_) {
        throw new AssertionError();
    }

    @Shadow
    private void func_237023_d_(Random p_237023_1_, IWorld p_237023_2_, BlockPos p_237023_3_) {
        throw new AssertionError();
    }

    @Shadow
    private void func_237020_b_(Random p_237020_1_, IWorld p_237020_2_, BlockPos p_237020_3_) {
        throw new AssertionError();
    }

    @Shadow
    private void func_237022_c_(Random p_237022_1_, IWorld p_237022_2_, BlockPos p_237022_3_) {
        throw new AssertionError();
    }

}
