package jpg.k.simplyimprovedterrain.util;

import java.util.HashMap;

// Pre-generates coefficients for spline interpolation.
// Uses a quintic spline along each axis, defined as follows:
// * P(t) = Function(offset)
// * P'(t) = [Function(offset+step*(t+1))-Function(offset+step*(t-1))]/2, at each integer t
// * P''(t) = 0, at t=0,1
public class SplerpBase
{
    public static final int SPLERP_COEFF_LOOKUP_STEP_SIZE = 4;

    static HashMap<Integer, SplerpBase> instances = new HashMap<>();

    double[] allWeights;

    private SplerpBase(int stride) {
        allWeights = new double[SPLERP_COEFF_LOOKUP_STEP_SIZE * stride];
        for (int i = 0; i < stride; i++) {
            double t = i * 1.0 / stride;
            int baseIndex = i * SPLERP_COEFF_LOOKUP_STEP_SIZE;

            // Quintic with zero second-derivatives
            allWeights[baseIndex + 0] = -(t * (0.5 + t * t * (-3 + t * (4 + t * -1.5))));
            allWeights[baseIndex + 1] = 1 + t * t * t * (-8 + t * (11.5 - 4.5 * t));
            allWeights[baseIndex + 2] = t * (0.5 + t * t * (7 + t * (4.5 * t - 11)));
            allWeights[baseIndex + 3] = (t * t * t * (-2 + t * (3.5 + t * -1.5)));

            // Cubic
            /*allWeights[baseIndex + 0] = t * (-0.5 + t * (1 + t * -0.5));
            allWeights[baseIndex + 1] = 1 + t * t * (-2.5 + t * 1.5);
            allWeights[baseIndex + 2] = t * (0.5 + t * (2 + t * -1.5));
            allWeights[baseIndex + 3] = 0.5 * t * t * (t - 1);*/
        }
    }

    public static SplerpBase forStride(int stride) {
        return instances.computeIfAbsent(stride, (k) -> new SplerpBase(stride));
    }

    public double eval(double[] values, int valuesStartIndex, int index) {
        double value = 0;
        int baseIndex = index * SPLERP_COEFF_LOOKUP_STEP_SIZE;
        for (int i = 0; i < SPLERP_COEFF_LOOKUP_STEP_SIZE; i++) {
            value += allWeights[baseIndex + i] * values[valuesStartIndex + i];
        }
        return value;
    }

    public static class Splerp {
        SplerpBase pregen;
        double[] values;

        public Splerp(SplerpBase pregen, double[] values) {
            this.pregen = pregen;
            this.values = values;
        }

        public double at(int index) {
            return pregen.eval(values, 0, index);
        }
    }

    public static class BiSplerp {
        SplerpBase pregenA;
        SplerpBase pregenB;
        public double[] values;

        public BiSplerp(SplerpBase pregenA, SplerpBase pregenB,
                        double[] values) {
            this.pregenA = pregenA;
            this.pregenB = pregenB;
            this.values = values;
        }

        public Splerp at(int index) {
            double[] newValues = new double[SplerpBase.SPLERP_COEFF_LOOKUP_STEP_SIZE];
            for (int i = 0; i < newValues.length; i++) {
                newValues[i] = pregenA.eval(values, i * SplerpBase.SPLERP_COEFF_LOOKUP_STEP_SIZE, index);
            }
            return new Splerp(pregenB, newValues);
        }
    }

    public static class TriSplerp {
        SplerpBase pregenA;
        SplerpBase pregenB;
        SplerpBase pregenC;
        double[] values;

        public TriSplerp(SplerpBase pregenA, SplerpBase pregenB, SplerpBase pregenC,
                         double[] values) {
            this.pregenA = pregenA;
            this.pregenB = pregenB;
            this.pregenC = pregenC;
            this.values = values;
        }

        public BiSplerp at(int index) {
            double[] newValues = new double[SplerpBase.SPLERP_COEFF_LOOKUP_STEP_SIZE * SplerpBase.SPLERP_COEFF_LOOKUP_STEP_SIZE];
            for (int i = 0; i < newValues.length; i++) {
                newValues[i] = pregenA.eval(values, i * SplerpBase.SPLERP_COEFF_LOOKUP_STEP_SIZE, index);
            }
            return new BiSplerp(pregenB, pregenC, newValues);
        }
    }

}