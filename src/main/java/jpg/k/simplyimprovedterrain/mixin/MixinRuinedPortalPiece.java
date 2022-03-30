package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.feature.structure.RuinedPortalPiece;
import net.minecraft.world.gen.feature.structure.StructurePiece;

@Mixin(RuinedPortalPiece.class)
public class MixinRuinedPortalPiece {

    @Shadow
    private @Final RuinedPortalPiece.Location verticalPlacement;

    @Shadow
    private @Final RuinedPortalPiece.Serializer properties;

    /**
     * @author K.jpg
     * @reason Euclideanize falloff shape
     */
    @Overwrite
    private void spreadNetherrack(Random random, IWorld world) {
        MutableBoundingBox boundingBox = ((StructurePiece)(Object)this).getBoundingBox();

        boolean onSurface = this.verticalPlacement == RuinedPortalPiece.Location.ON_LAND_SURFACE || this.verticalPlacement == RuinedPortalPiece.Location.ON_OCEAN_FLOOR;
        Vector3i center = boundingBox.getCenter();
        int cx = center.getX();
        int cz = center.getZ();
        float[] falloffThresholds = new float[] { 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F, 0.9F, 0.8F, 0.7F, 0.6F, 0.4F, 0.2F };
        int nFalloffThresholds = falloffThresholds.length;
        int averageWidth = (boundingBox.getXSpan() + boundingBox.getZSpan()) / 2;
        int falloffIndexOffset = random.nextInt(Math.max(1, 8 - averageWidth / 2));

        BlockPos.Mutable currentBlockPos = BlockPos.ZERO.mutable();
        int nFalloffThresholdsSq = nFalloffThresholds * nFalloffThresholds;
        float invNFalloffThresholdsSq = 1.0f / nFalloffThresholdsSq;

        for (int x = cx - nFalloffThresholds; x <= cx + nFalloffThresholds; ++x) {
            for (int z = cz - nFalloffThresholds; z <= cz + nFalloffThresholds; ++z) {

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
                        int y = getSurfaceY(world, x, z, this.verticalPlacement);
                        int yBounded = onSurface ? y : Math.min(boundingBox.y0, y);
                        currentBlockPos.set(x, yBounded, z);
                        if (Math.abs(yBounded - boundingBox.y0) <= 3 && this.canBlockBeReplacedByNetherrackOrMagma(world, currentBlockPos)) {
                            this.placeNetherrackOrMagma(random, world, currentBlockPos);
                            if (this.properties.overgrown) {
                                this.maybeAddLeavesAbove(random, world, currentBlockPos);
                            }

                            this.addNetherrackDripColumn(random, world, currentBlockPos.below());
                        }
                    }
                }
            }
        }
    }

    @Shadow
    private static int getSurfaceY(IWorld p_237009_0_, int p_237009_1_, int p_237009_2_, RuinedPortalPiece.Location p_237009_3_) {
        throw new AssertionError();
    }

    @Shadow
    private boolean canBlockBeReplacedByNetherrackOrMagma(IWorld p_237010_1_, BlockPos p_237010_2_) {
        throw new AssertionError();
    }

    @Shadow
    private void placeNetherrackOrMagma(Random p_237023_1_, IWorld p_237023_2_, BlockPos p_237023_3_) {
        throw new AssertionError();
    }

    @Shadow
    private void maybeAddLeavesAbove(Random p_237020_1_, IWorld p_237020_2_, BlockPos p_237020_3_) {
        throw new AssertionError();
    }

    @Shadow
    private void addNetherrackDripColumn(Random p_237022_1_, IWorld p_237022_2_, BlockPos p_237022_3_) {
        throw new AssertionError();
    }

}
