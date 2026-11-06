package core;

public class CosineSimilarity {
    public static float compute(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0f : (float) (dot / denom);
    }
}
