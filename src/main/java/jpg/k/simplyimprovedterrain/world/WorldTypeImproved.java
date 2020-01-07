package jpg.k.simplyimprovedterrain.world;

import jpg.k.simplyimprovedterrain.world.gen.EndChunkGeneratorImproved;
import jpg.k.simplyimprovedterrain.world.gen.NetherChunkGeneratorImproved;
import net.minecraft.block.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.biome.provider.BiomeProviderType;
import net.minecraft.world.biome.provider.OverworldBiomeProvider;
import net.minecraft.world.biome.provider.OverworldBiomeProviderSettings;
import net.minecraft.world.biome.provider.SingleBiomeProviderSettings;
import net.minecraft.world.dimension.EndDimension;
import net.minecraft.world.gen.*;

import jpg.k.simplyimprovedterrain.world.gen.OverworldChunkGeneratorImproved;

/**
 * Created by user on 12/30/2019.
 */
public class WorldTypeImproved extends WorldType {
    private static WorldTypeImproved INSTANCE;

    private WorldTypeImproved() {
        super("improved");
    }

    public static WorldTypeImproved getInstance() {
        if (INSTANCE == null) {
            init();
        }
        return INSTANCE;
    }

    public static void init() {
        INSTANCE = new WorldTypeImproved();
    }

    @Override // Client-only
    public String getTranslationKey() {
        return "gui.createWorld.worldtypename";
    }

    @Override
    public ChunkGenerator<?> createChunkGenerator(World world) {
        switch(world.dimension.getType().getId()) {

            case 0: // Overworld
                BiomeProviderType<OverworldBiomeProviderSettings, OverworldBiomeProvider> biomeprovidertype1 = BiomeProviderType.VANILLA_LAYERED;
                OverworldGenSettings overworldgensettings = ChunkGeneratorType.SURFACE.createSettings();
                OverworldBiomeProviderSettings overworldbiomeprovidersettings = (biomeprovidertype1.func_226840_a_(world.getWorldInfo())).setGeneratorSettings(overworldgensettings);
                return new OverworldChunkGeneratorImproved(world, biomeprovidertype1.create(overworldbiomeprovidersettings), overworldgensettings);

            case -1: // Nether
                NetherGenSettings nethergensettings = ChunkGeneratorType.CAVES.createSettings();
                nethergensettings.setDefaultBlock(Blocks.NETHERRACK.getDefaultState());
                nethergensettings.setDefaultFluid(Blocks.LAVA.getDefaultState());
                return new NetherChunkGeneratorImproved(world, BiomeProviderType.FIXED.create(BiomeProviderType.FIXED.func_226840_a_(world.getWorldInfo()).setBiome(Biomes.NETHER)), nethergensettings);

            case 1: // End
                EndGenerationSettings endgenerationsettings = ChunkGeneratorType.FLOATING_ISLANDS.createSettings();
                endgenerationsettings.setDefaultBlock(Blocks.END_STONE.getDefaultState());
                endgenerationsettings.setDefaultFluid(Blocks.AIR.getDefaultState());
                endgenerationsettings.setSpawnPos(EndDimension.SPAWN);
                return new EndChunkGeneratorImproved(world, BiomeProviderType.THE_END.create(BiomeProviderType.THE_END.func_226840_a_(world.getWorldInfo())), endgenerationsettings);

            default:
                return world.dimension.createChunkGenerator();
        }
    }
}
