package jpg.k.simplyimprovedterrain.mixin;

import java.util.Random;

import net.minecraft.util.math.MathHelper;
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

        // Modification: use float for this since it'll get applied to a float instead of an int
        float falloffIndexOffset = random.nextFloat() * Math.max(1, 8 - averageWidth / 2);

        BlockPos.Mutable currentBlockPos = BlockPos.ZERO.mutable();
        for (int x = cx - nFalloffThresholds; x <= cx + nFalloffThresholds; ++x) {
            for (int z = cz - nFalloffThresholds; z <= cz + nFalloffThresholds; ++z) {

                // Modification: replace Manhattan (axis-biased) distance with Euclidean (isotropic) distance.
                float falloffIndex = MathHelper.sqrt(MathHelper.square(x - cx) + MathHelper.square(z - cz));
                int boundedOffsetIndex = Math.max(0, (int)(falloffIndex + falloffIndexOffset));

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
