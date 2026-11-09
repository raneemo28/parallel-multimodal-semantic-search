package concurrency;

import core.CosineSimilarity;
import core.MediaItem;
import core.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class SequentialSearcher {
    private final List<MediaItem> items;

    public SequentialSearcher(List<MediaItem> items) {
        this.items = items;
    }

    public ParallelSearcher.SearchStats search(float[] query, int topK) {
        long t0 = System.nanoTime();
        PriorityQueue<SearchResult> pq = new PriorityQueue<>(topK);
        for (MediaItem it : items) {
            if (!it.indexed || it.vector == null) continue;
            float s = CosineSimilarity.compute(query, it.vector);
            SearchResult r = new SearchResult(it, s);
            if (pq.size() < topK) {
                pq.offer(r);
            } else if (s > pq.peek().score) {
                pq.poll();
                pq.offer(r);
            }
        }
        List<SearchResult> res = new ArrayList<>(pq);
        res.sort(Comparator.comparingDouble((SearchResult r) -> r.score).reversed());
        long t1 = System.nanoTime();
        return new ParallelSearcher.SearchStats(res, (t1 - t0) / 1_000_000.0);
    }
}
