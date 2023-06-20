package jpg.k.simplyimprovedterrain.mixin;

import jpg.k.simplyimprovedterrain.math.DistributionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.WeepingVinesFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static net.minecraft.world.level.levelgen.feature.WeepingVinesFeature.placeWeepingVinesColumn;

/**
 * Replaces pseudo-normal distribution with circular..
 * Visual impact: ★★★☆☆
 */
@Mixin(WeepingVinesFeature.class)
public class MixinWeepingVinesFeature {

    // This mixin uses rejection sampling instead of trigonometric formulas.
    // Those would work here, too, but this is a good opportunity to show another way.

    // Vanilla values, adjusted for spherical rejection sampling.
    // Ordinarily, you would just toss in one value or another and tune it until things look right.
    // Even these are only rough matches, all things considered.
    private static final int MAX_PLACEMENT_ATTEMPTS_ROOF_NETHER_WART = Math.round(200 * DistributionUtils.VOLUME_RATIO_SPHERE_TO_CUBE);
    private static final float RADIUS_ROOF_NETHER_WART = 6.0f * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;
    private static final int MAX_PLACEMENT_ATTEMPTS_ROOF_WEEPING_VINES = Math.round(100 * DistributionUtils.VOLUME_RATIO_SPHERE_TO_CUBE);
    private static final float RADIUS_ROOF_WEEPING_VINES = 8.0f * DistributionUtils.RADIUS_RATIO_SPHERE_TO_CUBE;

    private static final int VINE_LENGTH_MIN = 1;
    private static final int VINE_LENGTH_MAX = 7;

    private static final int VINE_LENGTH_DOUBLE_RARITY = 6;
    private static final int VINE_LENGTH_RESET_RARITY = 5;

    @Shadow @Final
    private static Direction[] DIRECTIONS = Direction.values();

    /**
     * @author K.jpg
     * @reason Spherical sampling
     */
    @Overwrite
    private void placeRoofNetherWart(LevelAccessor levelAccessor, RandomSource random, BlockPos origin) {
        levelAccessor.setBlock(origin, Blocks.NETHER_WART_BLOCK.defaultBlockState(), 2);
        BlockPos.MutableBlockPos placementMutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborMutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < MAX_PLACEMENT_ATTEMPTS_ROOF_NETHER_WART * 2; ++i) {

            // Spherical rejection sampling #1
            // Note the halving of the radius.
            float dxA = Mth.randomBetween(random, -RADIUS_ROOF_NETHER_WART / 2, RADIUS_ROOF_NETHER_WART / 2);
            float dyA = Mth.randomBetween(random, -RADIUS_ROOF_NETHER_WART / 2, RADIUS_ROOF_NETHER_WART / 2);
            float dzA = Mth.randomBetween(random, -RADIUS_ROOF_NETHER_WART / 2, RADIUS_ROOF_NETHER_WART / 2);
            float distanceSquaredA = dxA * dxA + dyA * dyA + dzA * dzA;
            if (distanceSquaredA > RADIUS_ROOF_NETHER_WART * RADIUS_ROOF_NETHER_WART / 4) continue;

            for (; i < MAX_PLACEMENT_ATTEMPTS_ROOF_NETHER_WART * 2; ++i) {

                // Spherical rejection sampling #2
                // Same deal with the halving.
                float dxB = Mth.randomBetween(random, -RADIUS_ROOF_NETHER_WART / 2, RADIUS_ROOF_NETHER_WART / 2);
                float dyB = Mth.randomBetween(random, -RADIUS_ROOF_NETHER_WART / 2, RADIUS_ROOF_NETHER_WART / 2);
                float dzB = Mth.randomBetween(random, -RADIUS_ROOF_NETHER_WART / 2, RADIUS_ROOF_NETHER_WART / 2);
                float distanceSquaredB = dxB * dxB + dyB * dyB + dzB * dzB;
                if (distanceSquaredB > RADIUS_ROOF_NETHER_WART * RADIUS_ROOF_NETHER_WART / 4) continue;

                // The trick: add the two together.
                // This gets use closer to the original distance distribution,
                // but without re-introducing the very functions that rejection sampling helps us avoid.
                float dx = dxA + dxB, dy = dyA + dyB, dz = dzA + dzB;
                placementMutableBlockPos.setWithOffset(origin, Math.round(dx), Math.round(dy), Math.round(dz));

                if (levelAccessor.isEmptyBlock(placementMutableBlockPos)) {
                    int attachableNeighborCount = 0;

                    // Don't place next to more than one attachable block.
                    for (Direction direction : DIRECTIONS) {
                        BlockState neighborBlockState = levelAccessor.getBlockState(neighborMutableBlockPos.setWithOffset(placementMutableBlockPos, direction));
                        if (neighborBlockState.is(Blocks.NETHERRACK) || neighborBlockState.is(Blocks.NETHER_WART_BLOCK)) {
                            ++attachableNeighborCount;
                        }
                        if (attachableNeighborCount > 1) {
                            break;
                        }
                    }

                    if (attachableNeighborCount == 1) {
                        levelAccessor.setBlock(placementMutableBlockPos, Blocks.NETHER_WART_BLOCK.defaultBlockState(), 2);
                    }
                }

                // Need to break out of this loop. Otherwise, we keep re-using the first sample when we need to replace it!
                break;
            }
        }
    }

    /**
     * @author K.jpg
     * @reason Spherical sampling & equilibrium states
     */
    @Overwrite
    private void placeRoofWeepingVines(LevelAccessor levelAccessor, RandomSource random, BlockPos origin) {
        BlockPos.MutableBlockPos placementMutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < MAX_PLACEMENT_ATTEMPTS_ROOF_WEEPING_VINES * 2; ++i) {

            // Spherical rejection sampling #1
            // Note the halving of the radius.
            float dxA = Mth.randomBetween(random, -RADIUS_ROOF_WEEPING_VINES / 2, RADIUS_ROOF_WEEPING_VINES / 2);
            float dyA = Mth.randomBetween(random, -RADIUS_ROOF_WEEPING_VINES / 2, RADIUS_ROOF_WEEPING_VINES / 2);
            float dzA = Mth.randomBetween(random, -RADIUS_ROOF_WEEPING_VINES / 2, RADIUS_ROOF_WEEPING_VINES / 2);
            float distanceSquaredA = dxA * dxA + dyA * dyA + dzA * dzA;
            if (distanceSquaredA > RADIUS_ROOF_WEEPING_VINES * RADIUS_ROOF_WEEPING_VINES / 4) continue;

            for (; i < MAX_PLACEMENT_ATTEMPTS_ROOF_WEEPING_VINES * 2; ++i) {

                // Spherical rejection sampling #2
                // Same deal with the halving.
                float dxB = Mth.randomBetween(random, -RADIUS_ROOF_WEEPING_VINES / 2, RADIUS_ROOF_WEEPING_VINES / 2);
                float dyB = Mth.randomBetween(random, -RADIUS_ROOF_WEEPING_VINES / 2, RADIUS_ROOF_WEEPING_VINES / 2);
                float dzB = Mth.randomBetween(random, -RADIUS_ROOF_WEEPING_VINES / 2, RADIUS_ROOF_WEEPING_VINES / 2);
                float distanceSquaredB = dxB * dxB + dyB * dyB + dzB * dzB;
                if (distanceSquaredB > RADIUS_ROOF_WEEPING_VINES * RADIUS_ROOF_WEEPING_VINES / 4) continue;

                // The trick: add the two together.
                // This gets use closer to the original distance distribution,
                // but without re-introducing the very functions that rejection sampling helps us avoid.
                float dx = dxA + dxB, dy = dyA + dyB, dz = dzA + dzB;
                placementMutableBlockPos.setWithOffset(origin, Math.round(dx), Math.round(dy), Math.round(dz));

                if (levelAccessor.isEmptyBlock(placementMutableBlockPos)) {
                    BlockState blockStateAbove = levelAccessor.getBlockState(placementMutableBlockPos.above());
                    if (blockStateAbove.is(Blocks.NETHERRACK) || blockStateAbove.is(Blocks.NETHER_WART_BLOCK)) {
                        int vineLength = Mth.nextInt(random, VINE_LENGTH_MIN, VINE_LENGTH_MAX + 1);
                        if (random.nextInt(VINE_LENGTH_DOUBLE_RARITY) == 0) {
                            vineLength *= 2;
                        }

                        if (random.nextInt(VINE_LENGTH_RESET_RARITY) == 0) {
                            vineLength = 1;
                        }

                        placeWeepingVinesColumn(levelAccessor, random, placementMutableBlockPos, vineLength,

                                // Increase immersion by avoiding generating the world in autonomously-unreachable non-equilibrium states.
                                // The world has long existed. Let the player suspend their disbelief!
                                GrowingPlantHeadBlock.MAX_AGE, GrowingPlantHeadBlock.MAX_AGE);
                    }
                }

                // Need to break out of this loop. Otherwise, we keep re-using the first sample when we need to replace it!
                break;
            }
        }

    }


    
}
