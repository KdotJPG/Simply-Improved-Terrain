package jpg.k.simplyimprovedterrain.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import jpg.k.simplyimprovedterrain.config.ConfigAccess;
import jpg.k.simplyimprovedterrain.terrain.formulamodification.TerrainFormulaModification;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public class MixinChunkMap {

    @Unique private static final Logger LOGGER = LogUtils.getLogger();

    @WrapOperation(method = "<init>", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;" +
                     "create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;" +
                     "Lnet/minecraft/core/HolderGetter;J)" +
                     "Lnet/minecraft/world/level/levelgen/RandomState;"
    ))
    private RandomState transformNoise(
            NoiseGeneratorSettings noiseGeneratorSettings,
            HolderGetter<NormalNoise.NoiseParameters> noises,
            long worldSeed, Operation<RandomState> original,
            ServerLevel serverLevel
    ) {
        var dimensionLocation = serverLevel.dimension().location();
        var dimensionConfig = ConfigAccess.get().resolveForDimension(dimensionLocation);

        if (!dimensionConfig.transformsDensityFormula()) {
            LOGGER.info("No density function tree modification for: {}", dimensionLocation);
            return original.call(noiseGeneratorSettings, noises, worldSeed);
        }

        LOGGER.info("Modifying density function tree for for: {}", dimensionLocation);
        var noiseGeneratorSettingsTransformed = new NoiseGeneratorSettings(
                noiseGeneratorSettings.noiseSettings(),
                noiseGeneratorSettings.defaultBlock(),
                noiseGeneratorSettings.defaultFluid(),
                TerrainFormulaModification.translateFormula(noiseGeneratorSettings.noiseRouter(), noises),
                noiseGeneratorSettings.surfaceRule(),
                noiseGeneratorSettings.spawnTarget(),
                noiseGeneratorSettings.seaLevel(),
                noiseGeneratorSettings.disableMobGeneration(),
                noiseGeneratorSettings.aquifersEnabled(),
                noiseGeneratorSettings.oreVeinsEnabled(),
                noiseGeneratorSettings.useLegacyRandomSource()
        );

        return original.call(noiseGeneratorSettingsTransformed, noises, worldSeed);
    }

}
