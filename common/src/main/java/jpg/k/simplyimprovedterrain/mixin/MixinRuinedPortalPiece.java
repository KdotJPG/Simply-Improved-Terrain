package jpg.k.simplyimprovedterrain.mixin;

import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import jpg.k.simplyimprovedterrain.math.LinearFunction1f;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Replaces Manhattan falloff with Euclidean.
 * Visual impact: ★★★★★
 */
@Mixin(value = RuinedPortalPiece.class, priority = 250)
public abstract class MixinRuinedPortalPiece extends StructurePiece {

    // Continuous replacement for vanilla's discrete falloff threshold values.
    // Original values: { 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.9f, 0.9f, 0.8f, 0.7f, 0.6f, 0.4f, 0.2f }
    // https://www.desmos.com/calculator/dwo584epfj
    private static final float THRESHOLD_DROP_START = 6.0f;
    private static final float THRESHOLD_REACH_ZERO_AT = 14.0f;
    private static final LinearFunction1f THRESHOLD_SLIDE = LinearFunction1f.createAsMap(THRESHOLD_DROP_START, THRESHOLD_REACH_ZERO_AT, 0.0f, 1.0f);
    private static final Float2FloatFunction THRESHOLD_FUNCTION = (t) -> {
        if (t <= THRESHOLD_DROP_START) return 1.0f;
        return 1.0f - Mth.square(THRESHOLD_SLIDE.compute(t));
    };

    @Shadow
    private @Final RuinedPortalPiece.VerticalPlacement verticalPlacement;

    @Shadow
    private @Final RuinedPortalPiece.Properties properties;

    protected MixinRuinedPortalPiece(StructurePieceType structurePieceType, int genDepth, BoundingBox boundingBox) {
        super(structurePieceType, genDepth, boundingBox);
    }

    /**
     * @author K.jpg
     * @reason Let organic patterns be isotropic!
     */
    @Overwrite
    private void spreadNetherrack(RandomSource random, LevelAccessor levelAccessor) {
        BoundingBox boundingBox = this.getBoundingBox();

        boolean isOnSurface = this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_LAND_SURFACE ||
                this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR;
        BlockPos origin = boundingBox.getCenter();
        float halfAverageWidth = (boundingBox.getXSpan() + boundingBox.getZSpan()) / 4.0f;
        float radiusReduction = random.nextFloat() * halfAverageWidth;
        float radius = THRESHOLD_REACH_ZERO_AT - radiusReduction;
        int radiusLoopBound = (int)radius;

        BlockPos.MutableBlockPos currentBlockPos = BlockPos.ZERO.mutable();

        for (int dz = -radiusLoopBound; dz <= radiusLoopBound; ++dz) {
            for (int dx = -radiusLoopBound; dx <= radiusLoopBound; ++dx) {
                float distanceSquared = dx * dx + dz * dz;
                if (radius >= radius * radius) continue;

                float distance = Mth.sqrt(distanceSquared);
                float threshold = THRESHOLD_FUNCTION.get(distance + radiusReduction);

                if (threshold > 0.0f) {
                    if (random.nextFloat() < threshold) {
                        int worldX = dx + origin.getX();
                        int worldZ = dz + origin.getZ();
                        int y = getSurfaceY(levelAccessor, worldX, worldZ, this.verticalPlacement);
                        int yBounded = isOnSurface ? y : Math.min(boundingBox.minY(), y);
                        currentBlockPos.set(worldX, yBounded, worldZ);
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
