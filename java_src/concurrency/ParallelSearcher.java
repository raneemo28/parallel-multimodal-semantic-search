package concurrency;

import core.MediaItem;
import core.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

public class ParallelSearcher {
    private final List<MediaItem> items;

    public ParallelSearcher(List<MediaItem> items) {
        this.items = items;
    }

    public SearchStats search(float[] query, int topK, int nThreads) throws InterruptedException, ExecutionException {
        int n = Math.max(1, Math.min(nThreads, Math.max(1, items.size())));
        int chunk = (items.size() + n - 1) / n;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<List<SearchResult>>> futures = new ArrayList<>();
        long t0 = System.nanoTime();
        for (int t = 0; t < n; t++) {
            int from = t * chunk;
            int to = Math.min(from + chunk, items.size());
            if (from >= items.size()) break;
            futures.add(pool.submit(new SearchTask(items, from, to, query, topK)));
        }
        List<SearchResult> merged = new ArrayList<>();
        try {
            for (Future<List<SearchResult>> f : futures) {
                merged.addAll(f.get());
            }
        } finally {
            pool.shutdown();
        }
        merged.sort(Comparator.comparingDouble((SearchResult r) -> r.score).reversed());
        if (merged.size() > topK) merged = merged.subList(0, topK);
        long t1 = System.nanoTime();
        return new SearchStats(merged, (t1 - t0) / 1_000_000.0);
    }

    public static class SearchStats {
        public final List<SearchResult> results;
        public final double elapsedMs;
        public SearchStats(List<SearchResult> results, double elapsedMs) {
            this.results = results;
            this.elapsedMs = elapsedMs;
        }
    }
}
