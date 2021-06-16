package jpg.k.simplyimprovedterrain.terrain;

import jpg.k.simplyimprovedterrain.util.noise.NeoNotchNoise;

/**
 * Created by K.jpg on 6/10/2021.
 */
public class SimplyImprovedTerrainNoiseSampler {

    private static final double NOISE_MAIN_FREQUENCY = 684.412 / 32768.0;
    private static final double BLEND_NOISE_RELATIVE_FREQUENCY = 256.0;
    private static final double PRIMARY_NOISE_MAIN_AMPLITUDE = 64.0;
    private static final double BLEND_NOISE_MAIN_AMPLITUDE = 6.4;
    private static final int N_OCTAVES_PRIMARY = 6;
    private static final int N_OCTAVES_BLEND = 3;

    NeoNotchNoise[] newNoiseOctaves1;
    NeoNotchNoise[] newNoiseOctaves2;
    NeoNotchNoise[] newNoiseOctavesBlend;
    double blendNoiseXZScale, blendNoiseYScale;
    double noiseXZScale, noiseYScale;
    private double[] primaryUncertaintyBounds;
    private double[] blendUncertaintyBounds;
    private double shelfSmoothingRatio;

    public SimplyImprovedTerrainNoiseSampler(NeoNotchNoise[] newNoiseOctaves1, NeoNotchNoise[] newNoiseOctaves2, NeoNotchNoise[] newNoiseOctavesBlend, double blendNoiseXZScale, double blendNoiseYScale, double noiseXZScale, double noiseYScale, double shelfSmoothingRatio) {
        this.newNoiseOctaves1 = newNoiseOctaves1;
        this.newNoiseOctaves2 = newNoiseOctaves2;
        this.newNoiseOctavesBlend = newNoiseOctavesBlend;
        this.blendNoiseXZScale = blendNoiseXZScale;
        this.blendNoiseYScale = blendNoiseYScale;
        this.noiseXZScale = noiseXZScale;
        this.noiseYScale = noiseYScale;
        this.shelfSmoothingRatio = shelfSmoothingRatio;

        // for N_OCTAVES_PRIMARY = 4, this would generate
        // { PRIMARY_NOISE_MAIN_AMPLITUDE * (1.0 + 0.5 + 0.25 + 0.125), PRIMARY_NOISE_MAIN_AMPLITUDE * (0.5 + 0.25 + 0.125), PRIMARY_NOISE_MAIN_AMPLITUDE * (0.25 + 0.125),  PRIMARY_NOISE_MAIN_AMPLITUDE * (0.125) };
        primaryUncertaintyBounds = new double[N_OCTAVES_PRIMARY];
        {
            double maxValueSum = 0.0;
            for (int i = N_OCTAVES_PRIMARY - 1; i >= 0; i--) {
                maxValueSum += 1.0 / (1 << i);
                primaryUncertaintyBounds[i] = PRIMARY_NOISE_MAIN_AMPLITUDE * maxValueSum;
            }
        }

        // for N_OCTAVES_BLEND = 3, this would generate
        // { BLEND_NOISE_MAIN_AMPLITUDE * (0.5 + 0.25), BLEND_NOISE_MAIN_AMPLITUDE * (0.25) };
        if (N_OCTAVES_BLEND != 0) {
            blendUncertaintyBounds = new double[N_OCTAVES_BLEND];
            {
                double maxValueSum = 0.0;
                for (int i = N_OCTAVES_BLEND - 1; i >= 0; i--) {
                    maxValueSum += 1.0 / (1 << i);
                    blendUncertaintyBounds[i] = BLEND_NOISE_MAIN_AMPLITUDE * maxValueSum + 0.5; // Note the +0.5
                }
            }
        }
    }

    public double sampleNoiseSign(double startingValue, int worldX, int worldY, int worldZ) {

        // Final noise value begins with the threshold.
        double signValue = startingValue;

        // If the noise couldn't possibly turn the threshold from air to solid (or vice-versa), we can skip modulating it.
        if (signValue > primaryUncertaintyBounds[0] || signValue < -primaryUncertaintyBounds[0])
            return signValue;

        // Compute blending noise fractal. We don't always need to calculate all of the octaves.
        double blendingValue = 0.0;
        if (newNoiseOctavesBlend.length != 0) {
            int octave = 0;
            double freqXZ = blendNoiseXZScale;
            double freqY = blendNoiseYScale;
            double amp = BLEND_NOISE_MAIN_AMPLITUDE;
            do {
                blendingValue += newNoiseOctavesBlend[octave].noise3(worldX * freqXZ, worldY * freqY, worldZ * freqXZ,
                        freqY * shelfSmoothingRatio) * amp;
                freqXZ *= 2.0;
                freqY *= 2.0;
                amp /= 2.0;
                octave++;
            } while (octave < newNoiseOctavesBlend.length && blendingValue > -blendUncertaintyBounds[octave]
                    && blendingValue < blendUncertaintyBounds[octave]);

            if (blendingValue <= -0.5)
                blendingValue = 0;
            else if (blendingValue >= 0.5)
                blendingValue = 1;
            else {
                blendingValue += 0.5;
                blendingValue = blendingValue * blendingValue * (3 - blendingValue * 2); // Smooth
            }
        }

        // Compute regular noise fractal(s). We don't always need to calculate all of the octaves. And most of the time, we only need to calculate one of the two fractals.
        {
            int octave = 0;
            double freqXZ = noiseXZScale;
            double freqY = noiseYScale;
            double amp = PRIMARY_NOISE_MAIN_AMPLITUDE;
            do {
                if (blendingValue < 1)
                    signValue += (1 - blendingValue) * amp * newNoiseOctaves1[octave].noise3(worldX * freqXZ,
                            worldY * freqY, worldZ * freqXZ, freqY * shelfSmoothingRatio);
                if (blendingValue > 0)
                    signValue += blendingValue * amp * newNoiseOctaves2[octave].noise3(worldX * freqXZ, worldY * freqY,
                            worldZ * freqXZ, freqY * shelfSmoothingRatio);
                freqXZ *= 2.0;
                freqY *= 2.0;
                amp /= 2.0;
                octave++;
            } while (octave < newNoiseOctaves1.length && signValue > -primaryUncertaintyBounds[octave]
                    && signValue < primaryUncertaintyBounds[octave]);

        }

        return signValue;
    }

}
