package jpg.k.simplyimprovedterrain.mixin;

import com.mojang.serialization.Codec;
import jpg.k.simplyimprovedterrain.math.ExtraMath;
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
import net.minecraft.world.level.material.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@SuppressWarnings("deprecation")
@Mixin(LakeFeature.class)
public abstract class MixinLakeFeature extends Feature<LakeFeature.Configuration> {

    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();

    private static final double FILLABLE_RADIUS = 9.7;
    private static final double LIQUID_DEPTH = 4.25;
    private static final double SURFACE_CAVITY_DEPTH = 4.25;

    // These supplement the guaranteed barrier.
    private static final double NOISY_BARRIER_THICKNESS_XZ_MAX = 1.3;
    private static final double NOISY_BARRIER_THICKNESS_XZ_MIN = 0.65;
    private static final double NOISY_BARRIER_THICKNESS_Y_MAX = 1.5;
    private static final double NOISY_BARRIER_THICKNESS_Y_MIN = 0.75;

    private static final double LIQUID_DEPTH_PADDED = LIQUID_DEPTH + NOISY_BARRIER_THICKNESS_Y_MAX;
    private static final double SURFACE_CAVITY_DEPTH_PADDED = SURFACE_CAVITY_DEPTH + NOISY_BARRIER_THICKNESS_Y_MAX;
    private static final int REQUIRED_CLEARANCE_BELOW = (int)Math.ceil(LIQUID_DEPTH + Math.max(1.0, NOISY_BARRIER_THICKNESS_Y_MAX));
    private static final int REQUIRED_CLEARANCE_ABOVE = (int)Math.ceil(SURFACE_CAVITY_DEPTH + Math.max(1.0, NOISY_BARRIER_THICKNESS_Y_MAX));
    private static final int TOTAL_VERTICAL_CLEARANCE = REQUIRED_CLEARANCE_BELOW + REQUIRED_CLEARANCE_ABOVE + 1;

    private static final int PLACEABLE_RADIUS = (int)Math.ceil(FILLABLE_RADIUS + Math.max(1.0, NOISY_BARRIER_THICKNESS_XZ_MAX));
    private static final int PLACEABLE_DIAMETER = PLACEABLE_RADIUS * 2 + 1;

    private static final int MIN_ELLIPSOID_COUNT = 5;
    private static final int MAX_ELLIPSOID_COUNT = 10;

    private static final double ELLIPSE_SIZE_XZ_MIN = 3.5;
    private static final double ELLIPSE_SIZE_XZ_MAX = 11.5;
    private static final double ELLIPSE_SIZE_Y_MIN = 2.5;
    private static final double ELLIPSE_SIZE_Y_MAX = 7.5;

    private static final double BARRIER_THICKNESS_NOISE_FREQUENCY_XZ = 0.1375;
    private static final double BARRIER_THICKNESS_NOISE_FREQUENCY_Y = 0.1375;
    private static final long BARRIER_THICKNESS_NOISE_SEED_BIT_FLIP = 0x6F54FAF3B1426565L;

    private static final double BARRIER_PERFORATION_NOISE_FREQUENCY_XZ = 0.275;
    private static final double BARRIER_PERFORATION_NOISE_FREQUENCY_Y = 0.275;
    private static final long BARRIER_PERFORATION_NOISE_SEED_BIT_FLIP = -0x23520947DBB98316L;

    // Can't put an enum in a mixin class. Preferring self-contained code over descriptive typing.
    private static final int STATE_NONE = 0;
    private static final int STATE_BARRIER = 1;
    private static final int STATE_FLUID_OR_AIR = 2;

    public MixinLakeFeature(Codec<LakeFeature.Configuration> codec) {
        super(codec);
    }

    /**
     * @author K.jpg
     * @reason Rework to increase directional uniformity
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

            int[] states = new int[PLACEABLE_DIAMETER * PLACEABLE_DIAMETER * TOTAL_VERTICAL_CLEARANCE];
            int ellipsoidCount = random.nextInt(MAX_ELLIPSOID_COUNT - MIN_ELLIPSOID_COUNT) + MIN_ELLIPSOID_COUNT;

            for (int i = 0; i < ellipsoidCount; ++i) {

                // Ellipsoid scale factors
                double ellipseAngle = random.nextDouble() * Mth.TWO_PI;
                double radiusYFluid = random.nextDouble() * (ELLIPSE_SIZE_Y_MAX - ELLIPSE_SIZE_Y_MIN) / 2 + (ELLIPSE_SIZE_Y_MIN / 2);
                double radiusUFluid = random.nextDouble() * (ELLIPSE_SIZE_XZ_MAX - ELLIPSE_SIZE_XZ_MIN) / 2 + (ELLIPSE_SIZE_XZ_MIN / 2);
                double radiusVFluid = random.nextDouble() * (ELLIPSE_SIZE_XZ_MAX - ELLIPSE_SIZE_XZ_MIN) / 2 + (ELLIPSE_SIZE_XZ_MIN / 2);
                double radiusYBarrierMin = radiusYFluid + NOISY_BARRIER_THICKNESS_Y_MIN;
                double radiusUBarrierMin = radiusUFluid + NOISY_BARRIER_THICKNESS_XZ_MIN;
                double radiusVBarrierMin = radiusVFluid + NOISY_BARRIER_THICKNESS_XZ_MIN;
                double radiusYBarrierMax = radiusYFluid + NOISY_BARRIER_THICKNESS_Y_MAX;
                double radiusUBarrierMax = radiusUFluid + NOISY_BARRIER_THICKNESS_XZ_MAX;
                double radiusVBarrierMax = radiusVFluid + NOISY_BARRIER_THICKNESS_XZ_MAX;

                // Ellipsoid rotation vectors
                double ellipseSin = Math.sin(ellipseAngle), ellipseCos = Math.cos(ellipseAngle);
                double uxFluid = ellipseSin / radiusUFluid, uzFluid =  ellipseCos / radiusUFluid;
                double vxFluid = ellipseCos / radiusVFluid, vzFluid = -ellipseSin / radiusVFluid;
                double uxBarrierMin = ellipseSin / radiusUBarrierMin, uzBarrierMin =  ellipseCos / radiusUBarrierMin;
                double vxBarrierMin = ellipseCos / radiusVBarrierMin, vzBarrierMin = -ellipseSin / radiusVBarrierMin;
                double uxBarrierMax = ellipseSin / radiusUBarrierMax, uzBarrierMax =  ellipseCos / radiusUBarrierMax;
                double vxBarrierMax = ellipseCos / radiusVBarrierMax, vzBarrierMax = -ellipseSin / radiusVBarrierMax;

                // Vertical scales
                double yScaleFluid = 1.0 / radiusYFluid;
                double yScaleBarrierMin = 1.0 / radiusYBarrierMin;
                double yScaleBarrierMax = 1.0 / radiusYBarrierMax;

                // Ellipsoid placement within boundary circle,
                // accounting for its shape and orientation.
                double startAngle = random.nextDouble() * Mth.TWO_PI;
                double startUVScale = 1.0 - Math.abs(random.nextDouble() - random.nextDouble());
                double startU = startUVScale * (FILLABLE_RADIUS - radiusUFluid) * Math.sin(startAngle);
                double startV = startUVScale * (FILLABLE_RADIUS - radiusVFluid) * Math.cos(startAngle);
                double startX = ellipseSin * startU + ellipseCos * startV;
                double startZ = ellipseCos * startU - ellipseCos * startV;

                // Ellipsoid vertical placement
                double minStartY = -LIQUID_DEPTH + radiusYFluid;
                double maxStartY = SURFACE_CAVITY_DEPTH - radiusYFluid;
                double startY = random.nextDouble() * (maxStartY - minStartY) + minStartY;

                for (int z = -PLACEABLE_RADIUS; z <= PLACEABLE_RADIUS; ++z) {
                    for (int x = -PLACEABLE_RADIUS; x <= PLACEABLE_RADIUS; ++x) {
                        for (int y = -REQUIRED_CLEARANCE_BELOW; y <= REQUIRED_CLEARANCE_ABOVE; ++y) {
                            int index = index(x, y, z);
                            int currentState = states[index];

                            if (currentState == STATE_FLUID_OR_AIR) continue;

                            // Fluid (or air) ellipsoid
                            double rFluid;
                            {
                                double duInner = uxFluid * (x - startX) + uzFluid * (z - startZ);
                                double dvInner = vxFluid * (x - startX) + vzFluid * (z - startZ);
                                double dyInner = (y - startY) * yScaleFluid;
                                rFluid = dyInner * dyInner + duInner * duInner + dvInner * dvInner;
                            }
                            if (rFluid < 1) {
                                states[index] = STATE_FLUID_OR_AIR;
                                continue;
                            }

                            if (currentState != STATE_NONE) continue;

                            // Barrier ellipsoid guaranteed to place
                            double rBarrierMin;
                            {
                                double duBarrierMin = uxBarrierMin * (x - startX) + uzBarrierMin * (z - startZ);
                                double dvBarrierMin = vxBarrierMin * (x - startX) + vzBarrierMin * (z - startZ);
                                double dyBarrierMin = (y - startY) * yScaleBarrierMin;
                                rBarrierMin = dyBarrierMin * dyBarrierMin + duBarrierMin * duBarrierMin + dvBarrierMin * dvBarrierMin;
                            }
                            if (rBarrierMin < 1) {
                                states[index] = STATE_BARRIER;
                                continue;
                            }
                            
                            // Barrier ellipsoid that can place depending on noise
                            double rBarrierMax;
                            {
                                double duBarrierMax = uxBarrierMax * (x - startX) + uzBarrierMax * (z - startZ);
                                double dvBarrierMax = vxBarrierMax * (x - startX) + vzBarrierMax * (z - startZ);
                                double dyBarrierMax = (y - startY) * yScaleBarrierMax;
                                rBarrierMax = dyBarrierMax * dyBarrierMax + duBarrierMax * duBarrierMax + dvBarrierMax * dvBarrierMax;
                            }
                            double barrierThicknessNoiseValue = sampleBarrierThicknessNoise(
                                    barrierThicknessNoiseSeed,
                                    x + origin.getX(),
                                    y + origin.getY(),
                                    z + origin.getZ()
                            );
                            if (Mth.lerp(barrierThicknessNoiseValue, rBarrierMin, rBarrierMax) < 1) {
                                states[index] = STATE_BARRIER;
                            }

                        }
                    }
                }
            }

            BlockState fluidBlockState = configuration.fluid().getState(random, origin);

            // Abort conditions for the barrier...
            for (int z = -PLACEABLE_RADIUS; z <= PLACEABLE_RADIUS; ++z) {
                for (int x = -PLACEABLE_RADIUS; x <= PLACEABLE_RADIUS; ++x) {
                    for (int y = -REQUIRED_CLEARANCE_BELOW; y <= REQUIRED_CLEARANCE_ABOVE; ++y) {
                        int index = index(x, y, z);
                        int state = states[index(x, y, z)];

                        // Still need to force barriers directly around/below fluid.
                        // The smallest placement-guaranteeing values for the minimum barrier constants, 1.0,
                        // make them too thick on average. By re-adding this, we can safely use nicer-looking constants.
                        if (state == STATE_NONE && y < 0) {
                            if (
                                    states[index(x, y + 1, z)] == STATE_FLUID_OR_AIR ||
                                    (x > -PLACEABLE_RADIUS && states[index(x - 1, y, z)] == STATE_FLUID_OR_AIR) ||
                                    (x <  PLACEABLE_RADIUS && states[index(x + 1, y, z)] == STATE_FLUID_OR_AIR) ||
                                    (z > -PLACEABLE_RADIUS && states[index(x, y, z - 1)] == STATE_FLUID_OR_AIR) ||
                                    (z <  PLACEABLE_RADIUS && states[index(x, y, z + 1)] == STATE_FLUID_OR_AIR)
                            ) {
                                state = states[index(x, y, z)] = STATE_BARRIER;
                            }
                        }

                        if (state == STATE_BARRIER) {
                            BlockPos blockPosHere = origin.offset(x, y, z);
                            Material material = worldGenLevel.getBlockState(blockPosHere).getMaterial();

                            // Abort if we encounter fluid above the surface...
                            if (y >= 0 && material.isLiquid()) {
                                return false;
                            }

                            // ... or any non-solidity + unmatching fluidity below it.
                            if (y < 0 && !material.isSolid() && worldGenLevel.getBlockState(blockPosHere) != fluidBlockState) {
                                return false;
                            }
                        }
                    }
                }
            }

            // Place fluid/air
            for (int z = -PLACEABLE_RADIUS; z <= PLACEABLE_RADIUS; ++z) {
                for (int x = -PLACEABLE_RADIUS; x <= PLACEABLE_RADIUS; ++x) {
                    for (int y = -REQUIRED_CLEARANCE_BELOW; y <= REQUIRED_CLEARANCE_ABOVE; ++y) {
                        if (states[index(x, y, z)] == STATE_FLUID_OR_AIR) {
                            BlockPos blockPosHere = origin.offset(x, y, z);
                            if (this.canReplaceBlock(worldGenLevel.getBlockState(blockPosHere))) {
                                boolean isAboveSurface = (y >= 0);
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
                for (int z = -PLACEABLE_RADIUS; z <= PLACEABLE_RADIUS; ++z) {
                    for (int x = -PLACEABLE_RADIUS; x <= PLACEABLE_RADIUS; ++x) {
                        for (int y = -REQUIRED_CLEARANCE_BELOW; y <= REQUIRED_CLEARANCE_ABOVE; ++y) {
                            if (states[index(x, y, z)] != STATE_BARRIER) continue;

                            // Always place the barrier below the surface.
                            boolean shouldPlaceBarrier = (y < 0);

                            BlockPos blockPosHere = origin.offset(x, y, z);

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
                                double barrierNoiseThreshold = (y + 1) * (1.0 / (SURFACE_CAVITY_DEPTH_PADDED + 1));
                                barrierPerforationNoiseValue *= barrierPerforationNoiseValue;
                                shouldPlaceBarrier = (barrierPerforationNoiseValue > barrierNoiseThreshold);
                            }

                            if (shouldPlaceBarrier) {
                                BlockState blockStateHere = worldGenLevel.getBlockState(blockPosHere);
                                if (blockStateHere.getMaterial().isSolid() && !blockStateHere.is(BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE)) {
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
                for (int z = -PLACEABLE_RADIUS; z <= PLACEABLE_RADIUS; ++z) {
                    for (int x = -PLACEABLE_RADIUS; x <= PLACEABLE_RADIUS; ++x) {
                        BlockPos blockPosHere = origin.offset(x, -1, z);
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

    private double sampleBarrierThicknessNoise(long noiseSeed, double x, double y, double z) {
        double value = OpenSimplex2S.noise3_ImproveXZ(
                noiseSeed,
               x * BARRIER_THICKNESS_NOISE_FREQUENCY_XZ,
                y * BARRIER_THICKNESS_NOISE_FREQUENCY_Y,
                z * BARRIER_THICKNESS_NOISE_FREQUENCY_XZ
        );

        // Rescale to [0, 1] and make extreme values more common.
        return ExtraMath.clampedFadeWithSymmetricDomainAndUnitRange(value);
    }

    private double sampleBarrierPerforationNoise(long noiseSeed, double x, double y, double z) {
        double value = OpenSimplex2S.noise3_ImproveXZ(
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