package jpg.k.simplyimprovedterrain.config;

import java.util.Optional;

public record DimensionConfig(
        Boolean transformsDensityFormula
) {
    public static final DimensionConfig OOPS_ALL_NULL = new DimensionConfig(null);

    public static final DimensionConfig DEFAULT = new DimensionConfig(true);

    public DimensionConfig withFallback(DimensionConfig fallback) {
        if (fallback == null) return this;
        return new DimensionConfig(
                Optional.ofNullable(transformsDensityFormula).orElse(fallback.transformsDensityFormula)
        );
    }
}
