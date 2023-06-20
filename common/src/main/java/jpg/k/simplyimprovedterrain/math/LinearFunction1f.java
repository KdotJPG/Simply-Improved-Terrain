package jpg.k.simplyimprovedterrain.math;

public record LinearFunction1f(float m, float b) {

    public static LinearFunction1f createAsMap(float inputA, float inputB, float outputA, float outputB) {
            float m = (outputB - outputA) / (inputB - inputA);
            float b = outputA - m * inputA;
            return new LinearFunction1f(m, b);
    }

    public float compute(float t) {
        return m * t + b;
    }

}
