package core;

public class CosineSimilarity {
    public static float compute(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return (float) dot;
    }

    public static void normalize(float[] v) {
        if (v == null) return;
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
    }
}
