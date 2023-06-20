package jpg.k.simplyimprovedterrain.terrain.customdensityfunctions;

import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;

import java.util.Arrays;

public class ArrayBackedCache2D implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {

    private NoiseChunk.Cache2D base;
    private int chunkX, chunkZ;
    private double[] cache;

    public ArrayBackedCache2D(int chunkX, int chunkZ, NoiseChunk.Cache2D base) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.base = base;
        cache = new double[LevelChunkSection.SECTION_WIDTH * LevelChunkSection.SECTION_WIDTH];
        Arrays.fill(cache, Double.NaN);
    }

    @Override
    public DensityFunction wrapped() {
        return base.wrapped();
    }

    @Override
    public double compute(FunctionContext context) {
        int x = context.blockX();
        int z = context.blockZ();
        int xInChunk = x - chunkX;
        int zInChunk = z - chunkZ;

        if (xInChunk < 0 || xInChunk >= LevelChunkSection.SECTION_WIDTH || zInChunk < 0 || zInChunk >= LevelChunkSection.SECTION_WIDTH) {
            return base.compute(context);
        }

        int index = zInChunk * LevelChunkSection.SECTION_WIDTH + xInChunk;
        double value = cache[index];
        if (Double.isNaN(value)) {
            value = cache[index] = base.wrapped().compute(context);
        }
        return value;
    }

    @Override
    public void fillArray(double[] ds, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(ds, this);
    }

    @Override
    public DensityFunctions.Marker.Type type() {
        return DensityFunctions.Marker.Type.Cache2D;
    }
}
