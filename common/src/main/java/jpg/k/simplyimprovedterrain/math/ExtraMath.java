package jpg.k.simplyimprovedterrain.math;

import net.minecraft.util.Mth;

public final class ExtraMath {

    public static double smoothMax(double a, double b, double smoothingFactor, double inverseSmoothingFactor) {
        double t = clampedFadeWithSymmetricDomainAndUnitRange((b - a) * inverseSmoothingFactor);
        return Mth.lerp(t, a + 0.25 * smoothingFactor * t, b + 0.25 * smoothingFactor * (1 - t));
    }

    /** Clamped fade curve with the usual range of [0,1], but a domain of [-1,1] instead. */
    public static double clampedFadeWithSymmetricDomainAndUnitRange(double t) {
        if (t <= -1) return 0;
        if (t <= 1) return 0.5 + t * (0.75 + t * t * -0.25);
        else return 1;
    }

    /** Clamped fade curve with the usual range of [0,1], but a domain of [-1,1] instead. */
    public static float clampedFadeWithSymmetricDomainAndUnitRange(float t) {
        if (t <= -1) return 0;
        if (t <= 1) return 0.5f + t * (0.75f + t * t * -0.25f);
        else return 1;
    }

}
