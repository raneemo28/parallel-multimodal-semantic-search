package concurrency;

import core.CosineSimilarity;
import core.MediaItem;
import core.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;

public class SearchTask implements Callable<List<SearchResult>> {
    private final List<MediaItem> items;
    private final int from;
    private final int to;
    private final float[] query;
    private final int topK;

    public SearchTask(List<MediaItem> items, int from, int to, float[] query, int topK) {
        this.items = items;
        this.from = from;
        this.to = to;
        this.query = query;
        this.topK = topK;
    }

    @Override
    public List<SearchResult> call() {
        PriorityQueue<SearchResult> pq = new PriorityQueue<>(topK);
        for (int i = from; i < to; i++) {
            MediaItem it = items.get(i);
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
        List<SearchResult> out = new ArrayList<>(pq);
        out.sort(Comparator.comparingDouble((SearchResult r) -> r.score).reversed());
        return out;
    }
}
