package jpg.k.simplyimprovedterrain.world.gen;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityClassification;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.NetherGenSettings;
import net.minecraft.world.gen.feature.Feature;

import java.util.List;

public class NetherChunkGeneratorImproved extends NoiseChunkGeneratorImproved<NetherGenSettings> {
    private final double[] constHeightThresholds = this.generateConstHeightThresholds();

    public NetherChunkGeneratorImproved(World world, BiomeProvider provider, NetherGenSettings settings) {
        super(world, provider, 4,128, settings, 4, 2, 684.412D, 2053.236D, 8.555150000000001D, 34.2206D);
    }

    protected double[] getColumnBiomeParams(int biomeParam1, int biomeParam2) {
        return new double[] { 0.0D, 0.0D };
    }

    protected double getHeightThreshold(double biomeParam1, double biomeParam2, int y) {
        return this.constHeightThresholds[y];
    }

    private double[] generateConstHeightThresholds() {
        double[] thresholds = new double[128];

        // In place of top fade from Vanilla NoiseChunkGenerator
        double g = 13.0;
        double gd = 3.0;
        double gg = -10.0;

        for (int y = 0; y < 128; y++) {
            thresholds[y] = Math.cos(y * 3.141592653589793 * 6.0 / 128.0) * 2.0;
            double yc = y / 8.0;
            if (yc > 8.0) {
                yc = 16 - 1 - yc;
            }

            if (yc < 4.0) {
                yc = 4.0 - yc;
                thresholds[y] -= yc * yc * yc * 10.0;
            }

            // In place of top fade from Vanilla NoiseChunkGenerator
            // Not actually sure what this does for the nether, but it's here in case I missed its purpose.
            if (yc > g) {
                double t = (yc - g) / gd;
                if (t > 1) t = 1; // clamp
                //else t = t * t * (3 - 2 * t); // Not from Vanilla originally. But might as well make it smooth.
                else t = t * t; // Only the beginning matters to be smooth, actually.
                thresholds[y] -= (t * gg) / (1 - t);
            }

        }

        return thresholds;
    }

    public List<Biome.SpawnListEntry> getPossibleCreatures(EntityClassification creatureType, BlockPos pos) {
        if(creatureType == EntityClassification.MONSTER) {
            if(Feature.NETHER_BRIDGE.isPositionInsideStructure(this.world, pos)) {
                return Feature.NETHER_BRIDGE.getSpawnList();
            }

            if(Feature.NETHER_BRIDGE.isPositionInStructure(this.world, pos) && this.world.getBlockState(pos.down()).getBlock() == Blocks.NETHER_BRICKS) {
                return Feature.NETHER_BRIDGE.getSpawnList();
            }
        }

        return super.getPossibleCreatures(creatureType, pos);
    }

    public int getGroundHeight() {
        return this.world.getSeaLevel() + 1;
    }

    public int getMaxHeight() {
        return 128;
    }

    public int getSeaLevel() {
        return 32;
    }
}
