package concurrency;

import api.EmbeddingApiClient;
import core.MediaItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelIndexer {
    public static class Stats {
        public int total;
        public int success;
        public int failed;
        public long elapsedMs;
    }

    private final EmbeddingApiClient client;

    public ParallelIndexer(EmbeddingApiClient client) {
        this.client = client;
    }

    public Stats index(List<MediaItem> items, int nThreads) throws Exception {
        int n = Math.max(1, nThreads);
        int total = items.size();
        Stats stats = new Stats();
        stats.total = total;

        // Group items into batches with a capped size so each thread reads
        // fewer files into memory at once (avoids OOM on large images/videos).
        final int MAX_BATCH = 20;
        int batchSize = Math.min(MAX_BATCH, Math.max(1, (total + n - 1) / n));
        List<List<MediaItem>> batches = new ArrayList<>();
        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            batches.add(new ArrayList<>(items.subList(i, end)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        long t0 = System.nanoTime();

        List<Callable<List<MediaItem>>> tasks = new ArrayList<>();
        for (List<MediaItem> batch : batches) {
            tasks.add(new IndexTask(client, batch));
        }

        try {
            // Overall timeout so indexing can't hang forever if a request stalls.
            List<Future<List<MediaItem>>> futures =
                    pool.invokeAll(tasks, 600, TimeUnit.SECONDS);
            for (Future<List<MediaItem>> f : futures) {
                try {
                    List<MediaItem> batch = f.get();
                    for (MediaItem it : batch) {
                        if (it.indexed && it.vector != null) {
                            success.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                    }
                } catch (ExecutionException e) {
                    failed.incrementAndGet();
                    System.err.println("Index task execution error: " + e.getCause());
                } catch (Exception e) {
                    failed.incrementAndGet();
                    System.err.println("Index task error: " + e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            pool.shutdown();
        }

        stats.success = success.get();
        stats.failed = failed.get();
        stats.elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        return stats;
    }
}
