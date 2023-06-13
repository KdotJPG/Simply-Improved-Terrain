package jpg.k.simplyimprovedterrain.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import org.apache.commons.lang3.function.TriFunction;

public class BiomeFiddleHelper {

    public static final int BLOCK_XYZ_OFFSET = QuartPos.SIZE / 2;
    private static final double FIDDLE_MAGNITUDE = 1.0; // 0.9 in net.minecraft.world.level.biome.BiomeManager
    private static final int FIDDLE_HASH_BIT_START = 24;
    private static final int FIDDLE_HASH_BIT_COUNT = 10;
    private static final int FIDDLE_HASH_BIT_SHIFTED = 1 << FIDDLE_HASH_BIT_COUNT;
    private static final int FIDDLE_HASH_BIT_MASK = FIDDLE_HASH_BIT_SHIFTED - 1;

    public record BiomeAndWeight(Biome biome, Holder<Biome> holder, double weight) { }

    // net.minecraft.world.level.biome.BiomeManager.getFiddle, but with configurable FIDDLE_MAGNITUDE
    private static double getFiddle(long hash) {
        long hashBits = (hash >> FIDDLE_HASH_BIT_START) & FIDDLE_HASH_BIT_MASK;
        return hashBits * (FIDDLE_MAGNITUDE / FIDDLE_HASH_BIT_SHIFTED) - 0.5 * FIDDLE_MAGNITUDE;
    }

    // Cleaner for block offsetting. Functions as if FIDDLE_MAGNITUDE = 1.0
    private static int getFiddleInt(long hash) {
        return ((int)(hash >> (FIDDLE_HASH_BIT_START + FIDDLE_HASH_BIT_COUNT - QuartPos.BITS)) & QuartPos.MASK) - 2;
    }

    public static <T> T sampleFiddled(int quartX, int quartY, int quartZ, long seed, TriFunction<Integer, Integer, Integer, T> source) {
        long hash = LinearCongruentialGenerator.next(seed, quartX);
        hash = LinearCongruentialGenerator.next(hash, quartY);
        hash = LinearCongruentialGenerator.next(hash, quartZ);
        hash = LinearCongruentialGenerator.next(hash, quartX);
        hash = LinearCongruentialGenerator.next(hash, quartY);
        hash = LinearCongruentialGenerator.next(hash, quartZ);
        int jz = getFiddleInt(hash) - BLOCK_XYZ_OFFSET;
        hash = LinearCongruentialGenerator.next(hash, seed);
        int jy = getFiddleInt(hash) - BLOCK_XYZ_OFFSET;
        hash = LinearCongruentialGenerator.next(hash, seed);
        int jx = getFiddleInt(hash) - BLOCK_XYZ_OFFSET;
        return source.apply(QuartPos.toBlock(quartX) - jx, QuartPos.toBlock(quartY) - jy, QuartPos.toBlock(quartZ) - jz);
    }

    public static double getFiddledDistance(long seed, int quartX, int quartY, int quartZ, double dx, double dy, double dz) {
        long hash = LinearCongruentialGenerator.next(seed, quartX);
        hash = LinearCongruentialGenerator.next(hash, quartY);
        hash = LinearCongruentialGenerator.next(hash, quartZ);
        hash = LinearCongruentialGenerator.next(hash, quartX);
        hash = LinearCongruentialGenerator.next(hash, quartY);
        hash = LinearCongruentialGenerator.next(hash, quartZ);
        double jz = getFiddle(hash);
        hash = LinearCongruentialGenerator.next(hash, seed);
        double jy = getFiddle(hash);
        hash = LinearCongruentialGenerator.next(hash, seed);
        double jx = getFiddle(hash);
        return Mth.square(dz + jz) + Mth.square(dy + jy) + Mth.square(dx + jx);
    }


}
