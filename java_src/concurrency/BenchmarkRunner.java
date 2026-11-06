package concurrency;

import core.MediaItem;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkRunner {
    private final List<MediaItem> items;
    private final float[] query;
    private final int topK;

    public BenchmarkRunner(List<MediaItem> items, float[] query, int topK) {
        this.items = items;
        this.query = query;
        this.topK = topK;
    }

    public List<BenchmarkResult> run(List<Integer> threadCounts) throws InterruptedException {
        List<BenchmarkResult> results = new ArrayList<>();
        SequentialSearcher seq = new SequentialSearcher(items);
        double sequentialMs = seq.search(query, topK).elapsedMs;
        for (int n : threadCounts) {
            ParallelSearcher par = new ParallelSearcher(items);
            double parallelMs = par.search(query, topK, n).elapsedMs;
            results.add(new BenchmarkResult(n, sequentialMs, parallelMs));
        }
        return results;
    }

    public static class BenchmarkResult {
        public final int threads;
        public final double sequentialMs;
        public final double parallelMs;
        public final double speedup;

        public BenchmarkResult(int threads, double sequentialMs, double parallelMs) {
            this.threads = threads;
            this.sequentialMs = sequentialMs;
            this.parallelMs = parallelMs;
            this.speedup = parallelMs > 0 ? sequentialMs / parallelMs : 0.0;
        }
    }
}
