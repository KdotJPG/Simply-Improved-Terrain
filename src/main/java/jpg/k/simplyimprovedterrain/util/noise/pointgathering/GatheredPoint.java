package jpg.k.simplyimprovedterrain.util.noise.pointgathering;

public class GatheredPoint<TTag> {
    private double x, z;
    private int hash;
    private TTag tag;

    public GatheredPoint(double x, double z, int hash) {
        this.x = x;
        this.z = z;
        this.hash = hash;
    }

    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }

    public double getHash() {
        return hash;
    }

    public TTag getTag() {
        return tag;
    }

    public void setTag(TTag tag) {
        this.tag = tag;
    }
}