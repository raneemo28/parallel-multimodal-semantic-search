package concurrency;

import core.MediaItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class BenchmarkRunner {
    private final List<MediaItem> items;
    private final float[] query;
    private final int topK;
    private static final int WARMUP_RUNS = 3;
    private static final int MEASUREMENT_RUNS = 5;

    public BenchmarkRunner(List<MediaItem> items, float[] query, int topK) {
        this.items = items;
        this.query = query;
        this.topK = topK;
    }

    public List<BenchmarkResult> run(List<Integer> threadCounts) throws InterruptedException, ExecutionException {
        List<BenchmarkResult> results = new ArrayList<>();
        SequentialSearcher seq = new SequentialSearcher(items);
        
        // Warm-up sequential search
        for (int i = 0; i < WARMUP_RUNS; i++) {
            seq.search(query, topK);
        }
        
        // Measure sequential search (median of runs)
        List<Double> seqTimes = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            seqTimes.add(seq.search(query, topK).elapsedMs);
        }
        double sequentialMs = median(seqTimes);

        for (int n : threadCounts) {
            ParallelSearcher par = new ParallelSearcher(items);
            
            // Warm-up parallel search
            for (int i = 0; i < WARMUP_RUNS; i++) {
                par.search(query, topK, n);
            }
            
            // Measure parallel search (median of runs)
            List<Double> parTimes = new ArrayList<>();
            for (int i = 0; i < MEASUREMENT_RUNS; i++) {
                parTimes.add(par.search(query, topK, n).elapsedMs);
            }
            double parallelMs = median(parTimes);
            
            results.add(new BenchmarkResult(n, sequentialMs, parallelMs));
        }
        return results;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        }
        return sorted.get(mid);
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
