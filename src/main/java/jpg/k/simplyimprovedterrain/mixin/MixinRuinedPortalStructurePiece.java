package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.math.BlockPos;
import net.minecraft.structure.RuinedPortalStructurePiece;

@Mixin(RuinedPortalStructurePiece.class)
public class MixinRuinedPortalStructurePiece {

    @Shadow
    private @Final RuinedPortalStructurePiece.VerticalPlacement verticalPlacement;

    @Shadow
    private @Final RuinedPortalStructurePiece.Properties properties;

    @Inject(method = "placeNetherrackBase", at = @At("HEAD"), cancellable = true)
    public void injectPlaceNetherracKBase(Random random, WorldAccess world, CallbackInfo ci) {
        BlockBox boundingBox = ((StructurePiece)(Object)this).getBoundingBox();

        boolean flag = this.verticalPlacement == RuinedPortalStructurePiece.VerticalPlacement.ON_LAND_SURFACE || this.verticalPlacement == RuinedPortalStructurePiece.VerticalPlacement.ON_OCEAN_FLOOR;
        Vec3i center = boundingBox.getCenter();
        int cx = center.getX();
        int cz = center.getZ();
        float[] falloffThresholds = new float[]{1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F, 0.9F, 0.8F, 0.7F, 0.6F, 0.4F, 0.2F};
        int nFalloffThresholds = falloffThresholds.length;
        int averageWidth = (boundingBox.getBlockCountX() + boundingBox.getBlockCountZ()) / 2;
        int falloffIndexOffset = random.nextInt(Math.max(1, 8 - averageWidth / 2));

        BlockPos.Mutable currentBlockPos = BlockPos.ORIGIN.mutableCopy();
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
                        int y = getBaseHeight(world, x, z, this.verticalPlacement);
                        int yBounded = flag ? y : Math.min(boundingBox.minY, y);
                        currentBlockPos.set(x, yBounded, z);
                        if (Math.abs(yBounded - boundingBox.minY) <= 3 && this.canFillNetherrack(world, currentBlockPos)) {
                            this.placeNetherrackBottom(random, world, currentBlockPos);
                            if (this.properties.overgrown) {
                                this.generateOvergrownLeaves(random, world, currentBlockPos);
                            }

                            this.updateNetherracks(random, world, currentBlockPos.down());
                        }
                    }
                }
            }
        }

        ci.cancel();
    }

    @Shadow
    private static int getBaseHeight(WorldAccess world, int x, int y, RuinedPortalStructurePiece.VerticalPlacement verticalPlacement) {
        throw new AssertionError();
    }

    @Shadow
    private boolean canFillNetherrack(WorldAccess world, BlockPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private void placeNetherrackBottom(Random random, WorldAccess world, BlockPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private void generateOvergrownLeaves(Random random, WorldAccess world, BlockPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private void updateNetherracks(Random random, WorldAccess world, BlockPos pos) {
        throw new AssertionError();
    }

}
