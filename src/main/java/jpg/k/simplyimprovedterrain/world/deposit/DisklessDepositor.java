package jpg.k.simplyimprovedterrain.world.deposit;

import jpg.k.simplyimprovedterrain.util.noise.FastSimplexStyleNoise;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.placement.FrequencyConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;

import java.util.*;

/*
 * Translates Minecraft's disk deposits into more visually-appealing noise-pattern deposits
 */
public class DisklessDepositor {

    private IdentityHashMap<Biome, DisklessDepositConfigs> depositsForBiome = new IdentityHashMap<>();

    public DisklessDepositor(Collection<Biome> biomes, Random seededRandom) {
        HashMap<DisklessDepositNoiseKey, FastSimplexStyleNoise> noiseInstanceMap = new HashMap<>();

        for (Biome biome : biomes) {

            // Get all the Biome Features, to find the disk ones
            for (ConfiguredFeature<?, ?> baseConfiguredFeature : biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)) {

                // Onions have layers. Vanilla Minecraft Decorators have layers. (Lots of layers!)
                DecoratedFeatureConfig decoratedFeatureConfig = (DecoratedFeatureConfig) baseConfiguredFeature.config;
                ConfiguredFeature<?, ?> configuredFeature = decoratedFeatureConfig.feature;
                Feature<? extends IFeatureConfig> actualFeature = configuredFeature.feature;
                if (!(actualFeature instanceof SphereReplaceFeature)) continue;

                IFeatureConfig actualConfig = configuredFeature.config;
                if (!(actualConfig instanceof SphereReplaceConfig)) continue;

                IPlacementConfig placementConfig = decoratedFeatureConfig.decorator.config;
                if (!(placementConfig instanceof FrequencyConfig)) continue;

                SphereReplaceConfig sphereReplaceConfig = (SphereReplaceConfig) actualConfig;
                FrequencyConfig frequencyConfig = (FrequencyConfig) placementConfig;

                // Now we can get all of the data we need.
                int frequencyCount = frequencyConfig.count;
                int configuredRadius = sphereReplaceConfig.radius;
                int configuredDepth = sphereReplaceConfig.ySize;
                BlockState blockType = sphereReplaceConfig.state;

                DisklessDepositNoiseKey key = new DisklessDepositNoiseKey();
                key.blockType = blockType;
                key.frequencyCount = frequencyCount;

                // Make sure to re-use noise instances whenever the blocktype and frequency are the same.
                FastSimplexStyleNoise noiseInstance = noiseInstanceMap.get(key);
                if (noiseInstance == null) { //TODO change to computeIfAbsent
                    noiseInstance = new FastSimplexStyleNoise(seededRandom);
                    noiseInstanceMap.put(key, noiseInstance);
                }

                // TODO we can re-use these instances instead of making new ones.
                // But it doesn't affect functionality, and doesn't take up much memory.
                DisklessDepositConfig config = new DisklessDepositConfig();
                config.blockType = blockType;
                config.noiseInstance = noiseInstance;
                config.depth = configuredDepth;

                // Sqrt because the number of noise features per area increases with the square of the frequency.
                config.noiseFreq = Math.sqrt(frequencyCount / 768.0);

                // New features should get bigger for bigger radii,
                // and should somewhat remain similar in size when the noise frequency is adjusted.
                // Not an exact calculation but it would be interesting compute this more exactly.
                config.noiseThreshold = 1.0 - 2.5 * (config.noiseFreq * configuredRadius * configuredRadius / 6.0);

                DisklessDepositConfigs configs = depositsForBiome.get(biome);
                if (configs == null) { //TODO change to computeIfAbsent
                    configs = new DisklessDepositConfigs();
                    configs.list = new ArrayList(3);
                    depositsForBiome.put(biome, configs);
                }
                if (config.depth > configs.maxDepth) configs.maxDepth = config.depth;
                configs.list.add(config);
            }
        }
    }

    public void apply(IChunk chunk, Biome biome, BlockPos.Mutable blockPos) {
        DisklessDepositConfigs configs = depositsForBiome.get(biome);
        if (configs == null) return;

        // currentBestValues default to zero. If no noise value better than zero is found, keep it as dirt.
        double[] currentBestValues = new double[configs.maxDepth];
        BlockState[] newBlocks = new BlockState[configs.maxDepth];

        // For each block type to deposit
        for (DisklessDepositConfig config : configs.list) {

            // Compute a noise value that represents its "strength" at this coordinate, adjusted for its configuration.
            double noiseValue = config.noiseInstance.noise2(blockPos.getX() * config.noiseFreq, blockPos.getZ() * config.noiseFreq);
            noiseValue -= config.noiseThreshold;
            noiseValue /= (1.0 - config.noiseThreshold);

            // We do these more involved loops instead of just looping over the winner's depth,
            // because we want sand/gravel (default depth=2) to be able to generate under clay (default depth=1).
            for (int i = 0; i < configs.maxDepth; i++) {

                // If this block wins over what we already have, set this as what we're going to place instead.
                if (i < config.depth && noiseValue > currentBestValues[i]) {
                    currentBestValues[i] = noiseValue;
                    newBlocks[i] = config.blockType;
                }
            }
        }

        // Perform the replacements over the depth.
        // Note: blockPos.move is OK in this case because the next thing NoiseChunkGenerator does is set blockPos to another position.
        // If that changes in the future, change this to blockPos = blockPos.down()
        for (int i = 0; i < configs.maxDepth; i++, blockPos.move(0, -1, 0)) {
            if (newBlocks[i] != null && Blocks.DIRT.getDefaultState().equals(chunk.getBlockState(blockPos))) {
                chunk.setBlockState(blockPos, newBlocks[i], false);
            }
        }
    }

    static class DisklessDepositConfigs {
        List<DisklessDepositConfig> list;
        int maxDepth;
    }

    static class DisklessDepositConfig {
        BlockState blockType;
        double noiseFreq, noiseThreshold;
        int depth;
        FastSimplexStyleNoise noiseInstance;
    }

    // Needs equals and hashCode, because this is used as a HashMap key.
    static class DisklessDepositNoiseKey {
        BlockState blockType;
        int frequencyCount;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DisklessDepositNoiseKey that = (DisklessDepositNoiseKey) o;

            if (frequencyCount != that.frequencyCount) return false;
            return blockType.equals(that.blockType);
        }

        @Override
        public int hashCode() {
            int result = blockType.getBlock().hashCode();
            result = 31 * result + frequencyCount;
            return result;
        }
    }
}
