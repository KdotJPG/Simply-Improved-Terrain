package jpg.k.simplyimprovedterrain.mixin;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.math.ExtraMath;
import jpg.k.simplyimprovedterrain.math.RotatedEllipse;
import jpg.k.simplyimprovedterrain.terrain.OpenSimplex2S;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Replaces square placement range with circular, adds rotation to ellipsoids, and adds noise variation to borders.
 * Visual impact: ★★★★☆
 */
@SuppressWarnings("deprecation")
@Mixin(value = LakeFeature.class, priority = 250)
public abstract class MixinLakeFeature extends Feature<LakeFeature.Configuration> {

    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();

    private static final float FILLABLE_RADIUS = 9.7f;
    private static final float LIQUID_DEPTH = 4.25f;
    private static final float SURFACE_CAVITY_DEPTH = 4.25f;

    // These supplement the guaranteed barrier.
    private static final float NOISY_BARRIER_THICKNESS_XZ_MAX = 1.3f;
    private static final float NOISY_BARRIER_THICKNESS_XZ_MIN = 0.65f;
    private static final float NOISY_BARRIER_THICKNESS_Y_MAX = 1.5f;
    private static final float NOISY_BARRIER_THICKNESS_Y_MIN = 0.75f;

    private static final double SURFACE_CAVITY_DEPTH_PADDED = SURFACE_CAVITY_DEPTH + NOISY_BARRIER_THICKNESS_Y_MAX;
    private static final int REQUIRED_CLEARANCE_BELOW = (int)Math.ceil(LIQUID_DEPTH + Math.max(1.0, NOISY_BARRIER_THICKNESS_Y_MAX));
    private static final int REQUIRED_CLEARANCE_ABOVE = (int)Math.ceil(SURFACE_CAVITY_DEPTH + Math.max(1.0, NOISY_BARRIER_THICKNESS_Y_MAX));
    private static final int TOTAL_VERTICAL_CLEARANCE = REQUIRED_CLEARANCE_BELOW + REQUIRED_CLEARANCE_ABOVE + 1;

    private static final int PLACEABLE_RADIUS = (int)Math.ceil(FILLABLE_RADIUS + Math.max(1.0, NOISY_BARRIER_THICKNESS_XZ_MAX));
    private static final int PLACEABLE_DIAMETER = PLACEABLE_RADIUS * 2 + 1;

    private static final int MIN_ELLIPSOID_COUNT = 5;
    private static final int MAX_ELLIPSOID_COUNT = 10;

    private static final float ELLIPSE_RADIUS_XZ_MIN = 1.75f;
    private static final float ELLIPSE_RADIUS_XZ_MAX = 5.75f;
    private static final float ELLIPSE_RADIUS_Y_MIN = 1.25f;
    private static final float ELLIPSE_RADIUS_Y_MAX = 3.75f;

    private static final double BARRIER_THICKNESS_NOISE_FREQUENCY_XZ = 0.1375;
    private static final double BARRIER_THICKNESS_NOISE_FREQUENCY_Y = 0.1375;
    private static final long BARRIER_THICKNESS_NOISE_SEED_BIT_FLIP = 0x6F54FAF3B1426565L;

    private static final double BARRIER_PERFORATION_NOISE_FREQUENCY_XZ = 0.275;
    private static final double BARRIER_PERFORATION_NOISE_FREQUENCY_Y = 0.275;
    private static final long BARRIER_PERFORATION_NOISE_SEED_BIT_FLIP = -0x23520947DBB98316L;

    // Can't put an enum in a mixin class, so opting for self-contained code over descriptive typing.
    private static final int STATE_NONE = 0;
    private static final int STATE_BARRIER = 1;
    private static final int STATE_FLUID_OR_AIR = 2;

    public MixinLakeFeature(Codec<LakeFeature.Configuration> codec) {
        super(codec);
    }

    /**
     * @author K.jpg
     * @reason Targeted injects/redirects/wraps would get quite messy here.
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<LakeFeature.Configuration> featurePlaceContext) {
        BlockPos origin = featurePlaceContext.origin();
        WorldGenLevel worldGenLevel = featurePlaceContext.level();
        RandomSource random = featurePlaceContext.random();
        LakeFeature.Configuration configuration = featurePlaceContext.config();
        long worldSeed = featurePlaceContext.level().getSeed();
        long barrierThicknessNoiseSeed = worldSeed ^ BARRIER_THICKNESS_NOISE_SEED_BIT_FLIP;
        long barrierPerforationNoiseSeed = worldSeed ^ BARRIER_PERFORATION_NOISE_SEED_BIT_FLIP;

        if (origin.getY() - REQUIRED_CLEARANCE_BELOW <= worldGenLevel.getMinBuildHeight()) {
            return false;
        } else {

            // Quick compatibility fix for Structure Gel, which uses this as an injection point into the Vanilla code.
            // Note that the value will not actually be used here, nor is it used in Structure Gel.
            BlockPos noOpBlockPos = origin.below(REQUIRED_CLEARANCE_BELOW);

            int[] states = new int[PLACEABLE_DIAMETER * PLACEABLE_DIAMETER * TOTAL_VERTICAL_CLEARANCE];
            int ellipsoidCount = random.nextInt(MAX_ELLIPSOID_COUNT - MIN_ELLIPSOID_COUNT) + MIN_ELLIPSOID_COUNT;

            for (int i = 0; i < ellipsoidCount; ++i) {

                // Ellipsoid scale factors
                float ellipseAngle = random.nextFloat() * Mth.TWO_PI;

                float radiusYFluid = Mth.randomBetween(random, ELLIPSE_RADIUS_Y_MIN,  ELLIPSE_RADIUS_Y_MAX);
                float radiusUFluid = Mth.randomBetween(random, ELLIPSE_RADIUS_XZ_MIN, ELLIPSE_RADIUS_XZ_MAX);
                float radiusVFluid = Mth.randomBetween(random, ELLIPSE_RADIUS_XZ_MIN, ELLIPSE_RADIUS_XZ_MAX);
                float radiusYBarrierMin = radiusYFluid + NOISY_BARRIER_THICKNESS_Y_MIN;
                float radiusUBarrierMin = radiusUFluid + NOISY_BARRIER_THICKNESS_XZ_MIN;
                float radiusVBarrierMin = radiusVFluid + NOISY_BARRIER_THICKNESS_XZ_MIN;
                float radiusYBarrierMax = radiusYFluid + NOISY_BARRIER_THICKNESS_Y_MAX;
                float radiusUBarrierMax = radiusUFluid + NOISY_BARRIER_THICKNESS_XZ_MAX;
                float radiusVBarrierMax = radiusVFluid + NOISY_BARRIER_THICKNESS_XZ_MAX;

                // Ellipsoid 2D ellipse components
                float ellipseCos = Mth.cos(ellipseAngle), ellipseSin = Mth.sin(ellipseAngle);
                RotatedEllipse ellipseFluid = RotatedEllipse.createFromUnitVectorAndRadii(ellipseCos, ellipseSin, radiusUFluid, radiusVFluid);
                RotatedEllipse ellipseBarrierMin = RotatedEllipse.createFromUnitVectorAndRadii(ellipseCos, ellipseSin, radiusUBarrierMin, radiusVBarrierMin);
                RotatedEllipse ellipseBarrierMax = RotatedEllipse.createFromUnitVectorAndRadii(ellipseCos, ellipseSin, radiusUBarrierMax, radiusVBarrierMax);

                // Vertical scales
                float yScaleFluid = 1.0f / radiusYFluid;
                float yScaleBarrierMin = 1.0f / radiusYBarrierMin;
                float yScaleBarrierMax = 1.0f / radiusYBarrierMax;

                // Ellipsoid placement within boundary circle,
                // accounting for its shape and orientation.
                float startAngle = random.nextFloat() * Mth.TWO_PI;
                float startUVScale = 1.0f - Math.abs(random.nextFloat() - random.nextFloat());
                float startU = startUVScale * (FILLABLE_RADIUS - radiusUFluid) * Mth.cos(startAngle);
                float startV = startUVScale * (FILLABLE_RADIUS - radiusVFluid) * Mth.sin(startAngle);
                float startX = ellipseCos * startU + ellipseSin * startV;
                float startZ = ellipseSin * startU - ellipseSin * startV;

                // Ellipsoid vertical placement
                float minStartY = -LIQUID_DEPTH + radiusYFluid;
                float maxStartY = SURFACE_CAVITY_DEPTH - radiusYFluid;
                float startY = Mth.randomBetween(random, minStartY, maxStartY);

                for (int dz = -PLACEABLE_RADIUS; dz <= PLACEABLE_RADIUS; ++dz) {
                    for (int dx = -PLACEABLE_RADIUS; dx <= PLACEABLE_RADIUS; ++dx) {
                        for (int dy = -REQUIRED_CLEARANCE_BELOW; dy <= REQUIRED_CLEARANCE_ABOVE; ++dy) {
                            int index = index(dx, dy, dz);
                            int currentState = states[index];

                            if (currentState == STATE_FLUID_OR_AIR) continue;

                            // Fluid (or air) ellipsoid
                            float fluidEllipsoidValue = ellipseFluid.compute(dx - startX, dz - startZ) + Mth.square((dy - startY) * yScaleFluid);
                            if (fluidEllipsoidValue < 1) {
                                states[index] = STATE_FLUID_OR_AIR;
                                continue;
                            }

                            if (currentState != STATE_NONE) continue;

                            // Barrier ellipsoid guaranteed to place
                            float barrierMinEllipsoidValue = ellipseBarrierMin.compute(dx - startX, dz - startZ) + Mth.square((dy - startY) * yScaleBarrierMin);
                            if (barrierMinEllipsoidValue < 1) {
                                states[index] = STATE_BARRIER;
                                continue;
                            }
                            
                            // Barrier ellipsoid that can place depending on noise
                            float barrierMaxEllipsoidValue = ellipseBarrierMax.compute(dx - startX, dz - startZ) + Mth.square((dy - startY) * yScaleBarrierMax);
                            float barrierThicknessNoiseValue = sampleBarrierThicknessNoise(
                                    barrierThicknessNoiseSeed,
                                    dx + origin.getX(),
                                    dy + origin.getY(),
                                    dz + origin.getZ()
                            );
                            if (Mth.lerp(barrierThicknessNoiseValue, barrierMinEllipsoidValue, barrierMaxEllipsoidValue) < 1) {
                                states[index] = STATE_BARRIER;
                            }

                        }
                    }
                }
            }

            BlockState fluidBlockState = configuration.fluid().getState(random, origin);

            // Abort conditions for the barrier...
            for (int dz = -PLACEABLE_RADIUS; dz <= PLACEABLE_RADIUS; ++dz) {
                for (int dx = -PLACEABLE_RADIUS; dx <= PLACEABLE_RADIUS; ++dx) {
                    for (int dy = -REQUIRED_CLEARANCE_BELOW; dy <= REQUIRED_CLEARANCE_ABOVE; ++dy) {
                        int index = index(dx, dy, dz);
                        int state = states[index];

                        // Still need to force barriers directly around/below fluid.
                        // The smallest placement-guaranteeing values for the minimum barrier constants, 1.0,
                        // make them too thick on average. By re-adding this, we can safely use nicer-looking constants.
                        if (state == STATE_NONE && dy < 0) {
                            if (
                                    states[index(dx, dy + 1, dz)] == STATE_FLUID_OR_AIR ||
                                    (dx > -PLACEABLE_RADIUS && states[index(dx - 1, dy, dz)] == STATE_FLUID_OR_AIR) ||
                                    (dx <  PLACEABLE_RADIUS && states[index(dx + 1, dy, dz)] == STATE_FLUID_OR_AIR) ||
                                    (dz > -PLACEABLE_RADIUS && states[index(dx, dy, dz - 1)] == STATE_FLUID_OR_AIR) ||
                                    (dz <  PLACEABLE_RADIUS && states[index(dx, dy, dz + 1)] == STATE_FLUID_OR_AIR)
                            ) {
                                state = states[index] = STATE_BARRIER;
                            }
                        }

                        if (state == STATE_BARRIER) {
                            BlockPos blockPosHere = origin.offset(dx, dy, dz);
                            BlockState blockStateHere = worldGenLevel.getBlockState(blockPosHere);

                            // Abort if we encounter fluid above the surface...
                            if (dy >= 0 && blockStateHere.liquid()) {
                                return false;
                            }

                            // ... or any non-solidity + unmatching fluidity below it.
                            if (dy < 0 && !blockStateHere.isSolid() && worldGenLevel.getBlockState(blockPosHere) != fluidBlockState) {
                                return false;
                            }
                        }
                    }
                }
            }

            // Place fluid/air
            for (int dz = -PLACEABLE_RADIUS; dz <= PLACEABLE_RADIUS; ++dz) {
                for (int dx = -PLACEABLE_RADIUS; dx <= PLACEABLE_RADIUS; ++dx) {
                    for (int dy = -REQUIRED_CLEARANCE_BELOW; dy <= REQUIRED_CLEARANCE_ABOVE; ++dy) {
                        if (states[index(dx, dy, dz)] == STATE_FLUID_OR_AIR) {
                            BlockPos blockPosHere = origin.offset(dx, dy, dz);
                            if (this.canReplaceBlock(worldGenLevel.getBlockState(blockPosHere))) {
                                boolean isAboveSurface = (dy >= 0);
                                worldGenLevel.setBlock(blockPosHere, isAboveSurface ? AIR : fluidBlockState, 2);
                                if (isAboveSurface) {
                                    worldGenLevel.scheduleTick(blockPosHere, AIR.getBlock(), 0);
                                    this.markAboveForPostProcessing(worldGenLevel, blockPosHere);
                                }
                            }
                        }
                    }
                }
            }

            BlockState barrierBlockState = configuration.barrier().getState(random, origin);

            // Place barrier
            if (!barrierBlockState.isAir()) {
                for (int dz = -PLACEABLE_RADIUS; dz <= PLACEABLE_RADIUS; ++dz) {
                    for (int dx = -PLACEABLE_RADIUS; dx <= PLACEABLE_RADIUS; ++dx) {
                        for (int dy = -REQUIRED_CLEARANCE_BELOW; dy <= REQUIRED_CLEARANCE_ABOVE; ++dy) {
                            if (states[index(dx, dy, dz)] != STATE_BARRIER) continue;

                            // Always place the barrier below the surface.
                            boolean shouldPlaceBarrier = (dy < 0);

                            BlockPos blockPosHere = origin.offset(dx, dy, dz);

                            // Above the surface, place the barrier sometimes, based on a noise map.
                            if (!shouldPlaceBarrier) {
                                double barrierPerforationNoiseValue = sampleBarrierPerforationNoise(
                                        barrierPerforationNoiseSeed,
                                        blockPosHere.getX(),
                                        blockPosHere.getY(),
                                        blockPosHere.getZ()
                                );

                                // Place it less often as we go up.
                                // Offset by 1 to avoid guaranteeing that the first layer above the surface always places the barrier.
                                // Square the noise value to make the gaps bigger faster.
                                double barrierNoiseThreshold = (dy + 1) * (1.0 / (SURFACE_CAVITY_DEPTH_PADDED + 1));
                                barrierPerforationNoiseValue *= barrierPerforationNoiseValue;
                                shouldPlaceBarrier = (barrierPerforationNoiseValue > barrierNoiseThreshold);
                            }

                            if (shouldPlaceBarrier) {
                                BlockState blockStateHere = worldGenLevel.getBlockState(blockPosHere);
                                if (blockStateHere.isSolid() && !blockStateHere.is(BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE)) {
                                    worldGenLevel.setBlock(blockPosHere, barrierBlockState, 2);
                                    this.markAboveForPostProcessing(worldGenLevel, blockPosHere);
                                }
                            }
                        }
                    }
                }
            }

            // Surface water -> ice in frozen biomes
            if (fluidBlockState.getFluidState().is(FluidTags.WATER)) {
                for (int dz = -PLACEABLE_RADIUS; dz <= PLACEABLE_RADIUS; ++dz) {
                    for (int dx = -PLACEABLE_RADIUS; dx <= PLACEABLE_RADIUS; ++dx) {
                        BlockPos blockPosHere = origin.offset(dx, -1, dz);
                        if ((worldGenLevel.getBiome(blockPosHere).value()).shouldFreeze(worldGenLevel, blockPosHere, false) &&
                                this.canReplaceBlock(worldGenLevel.getBlockState(blockPosHere))) {
                            worldGenLevel.setBlock(blockPosHere, Blocks.ICE.defaultBlockState(), 2);
                        }
                    }
                }
            }

            return true;
        }
    }

    private float sampleBarrierThicknessNoise(long noiseSeed, double x, double y, double z) {
        float value = OpenSimplex2S.noise3_ImproveXZ(
                noiseSeed,
               x * BARRIER_THICKNESS_NOISE_FREQUENCY_XZ,
                y * BARRIER_THICKNESS_NOISE_FREQUENCY_Y,
                z * BARRIER_THICKNESS_NOISE_FREQUENCY_XZ
        );

        // Rescale to [0, 1] and make extreme values more common.
        return ExtraMath.clampedFadeWithSymmetricDomainAndUnitRange(value);
    }

    private float sampleBarrierPerforationNoise(long noiseSeed, double x, double y, double z) {
        float value = OpenSimplex2S.noise3_ImproveXZ(
                noiseSeed,
                x * BARRIER_PERFORATION_NOISE_FREQUENCY_XZ,
                y * BARRIER_PERFORATION_NOISE_FREQUENCY_Y,
                z * BARRIER_PERFORATION_NOISE_FREQUENCY_XZ
        );

        // Rescale to [0, 1] and make extreme values more common.
        return ExtraMath.clampedFadeWithSymmetricDomainAndUnitRange(value);
    }

    private static int index(int x, int y, int z) {
        return ((z + PLACEABLE_RADIUS) * PLACEABLE_DIAMETER + (x + PLACEABLE_RADIUS)) * TOTAL_VERTICAL_CLEARANCE + (y + REQUIRED_CLEARANCE_BELOW);
    }

    @Shadow
    protected abstract boolean canReplaceBlock(BlockState blockState);

}