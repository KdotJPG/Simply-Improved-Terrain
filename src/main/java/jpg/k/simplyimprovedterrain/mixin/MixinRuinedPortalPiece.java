package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RuinedPortalPiece.class)
public class MixinRuinedPortalPiece {

    @Shadow
    private @Final RuinedPortalPiece.VerticalPlacement verticalPlacement;

    @Shadow
    private @Final RuinedPortalPiece.Properties properties;

    @Inject(method = "spreadNetherrack", at = @At("HEAD"), cancellable = true)
    public void injectSpreadNetherrack(RandomSource random, LevelAccessor levelAccessor, CallbackInfo ci) {
        BoundingBox boundingBox = ((StructurePiece)(Object)this).getBoundingBox();

        boolean flag = this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_LAND_SURFACE || this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR;
        BlockPos center = boundingBox.getCenter();
        int cx = center.getX();
        int cz = center.getZ();
        float[] falloffThresholds = new float[] {1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F, 0.9F, 0.8F, 0.7F, 0.6F, 0.4F, 0.2F};
        int nFalloffThresholds = falloffThresholds.length;
        int averageWidth = (boundingBox.getXSpan() + boundingBox.getZSpan()) / 2;
        int falloffIndexOffset = random.nextInt(Math.max(1, 8 - averageWidth / 2));

        BlockPos.MutableBlockPos currentBlockPos = BlockPos.ZERO.mutable();
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
                        int y = getSurfaceY(levelAccessor, x, z, this.verticalPlacement);
                        int yBounded = flag ? y : Math.min(boundingBox.minY(), y);
                        currentBlockPos.set(x, yBounded, z);
                        if (Math.abs(yBounded - boundingBox.minY()) <= 3 && this.canBlockBeReplacedByNetherrackOrMagma(levelAccessor, currentBlockPos)) {
                            this.placeNetherrackOrMagma(random, levelAccessor, currentBlockPos);
                            if (this.properties.overgrown) {
                                this.maybeAddLeavesAbove(random, levelAccessor, currentBlockPos);
                            }

                            this.addNetherrackDripColumn(random, levelAccessor, currentBlockPos.below());
                        }
                    }
                }
            }
        }

        ci.cancel();
    }

    @Shadow
    private static int getSurfaceY(LevelAccessor world, int x, int y, RuinedPortalPiece.VerticalPlacement verticalPlacement) {
        throw new AssertionError();
    }

    @Shadow
    private boolean canBlockBeReplacedByNetherrackOrMagma(LevelAccessor levelAccessor, BlockPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private void placeNetherrackOrMagma(RandomSource random, LevelAccessor levelAccessor, BlockPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private void maybeAddLeavesAbove(RandomSource random, LevelAccessor levelAccessor, BlockPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private void addNetherrackDripColumn(RandomSource random, LevelAccessor levelAccessor, BlockPos pos) {
        throw new AssertionError();
    }

}
