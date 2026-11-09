package concurrency;

import core.CosineSimilarity;
import core.MediaItem;
import core.SearchResult;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SearchCorrectnessTest {

    public static void main(String[] args) throws Exception {
        System.out.println("Testing sequential vs parallel search correctness...\n");

        // Create deterministic test data
        List<MediaItem> items = createTestItems(1000);
        float[] query = createRandomVector(768, 42);
        // Normalize query so dot product = cosine similarity
        CosineSimilarity.normalize(query);

        int topK = 10;
        int nThreads = 4;

        // Run sequential search
        SequentialSearcher seq = new SequentialSearcher(items);
        ParallelSearcher.SearchStats seqStats = seq.search(query, topK);

        // Run parallel search
        ParallelSearcher par = new ParallelSearcher(items);
        ParallelSearcher.SearchStats parStats = par.search(query, topK, nThreads);

        // Compare results
        boolean passed = compareResults(seqStats.results, parStats.results, topK);

        System.out.println("Sequential top-" + topK + " scores:");
        for (int i = 0; i < seqStats.results.size(); i++) {
            System.out.printf("  %d: %.6f  %s\n", i + 1, seqStats.results.get(i).score, seqStats.results.get(i).item.id);
        }

        System.out.println("\nParallel top-" + topK + " scores:");
        for (int i = 0; i < parStats.results.size(); i++) {
            System.out.printf("  %d: %.6f  %s\n", i + 1, parStats.results.get(i).score, parStats.results.get(i).item.id);
        }

        System.out.println("\n" + (passed ? "✓ TEST PASSED: Sequential and parallel search return identical top-K results"
                : "✗ TEST FAILED: Results differ"));
        System.exit(passed ? 0 : 1);
    }

    private static List<MediaItem> createTestItems(int count) {
        List<MediaItem> items = new ArrayList<>(count);
        Random rand = new Random(12345);
        for (int i = 0; i < count; i++) {
            float[] vec = createRandomVector(768, rand.nextInt());
            // Pre-normalize so cosine = dot product
            CosineSimilarity.normalize(vec);
            MediaItem item = new MediaItem(
                "item-" + i,
                "Test Item " + i,
                "Content " + i,
                Paths.get("test", "item" + i + ".txt"),
                MediaItem.Type.TEXT,
                "en",
                new String[]{"test"}
            );
            item.vector = vec;
            item.indexed = true;
            items.add(item);
        }
        return items;
    }

    private static float[] createRandomVector(int dim, int seed) {
        Random rand = new Random(seed);
        float[] vec = new float[dim];
        for (int i = 0; i < dim; i++) {
            vec[i] = rand.nextFloat() * 2 - 1; // [-1, 1]
        }
        return vec;
    }

    private static boolean compareResults(List<SearchResult> seq, List<SearchResult> par, int topK) {
        if (seq.size() != par.size()) {
            System.out.println("Size mismatch: seq=" + seq.size() + " par=" + par.size());
            return false;
        }
        for (int i = 0; i < seq.size(); i++) {
            SearchResult s = seq.get(i);
            SearchResult p = par.get(i);
            if (!s.item.id.equals(p.item.id)) {
                System.out.println("Mismatch at rank " + (i + 1) + ": seq=" + s.item.id + " par=" + p.item.id);
                return false;
            }
            if (Math.abs(s.score - p.score) > 1e-6) {
                System.out.println("Score mismatch at rank " + (i + 1) + ": seq=" + s.score + " par=" + p.score);
                return false;
            }
        }
        return true;
    }
}