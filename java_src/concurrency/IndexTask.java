package concurrency;

import api.EmbeddingApiClient;
import core.MediaItem;

import java.util.List;
import java.util.concurrent.Callable;

public class IndexTask implements Callable<List<MediaItem>> {
    private final EmbeddingApiClient client;
    private final List<MediaItem> items;

    public IndexTask(EmbeddingApiClient client, List<MediaItem> items) {
        this.client = client;
        this.items = items;
    }

    @Override
    public List<MediaItem> call() throws Exception {
        List<float[]> vectors = client.embedBatch(items);
        for (int i = 0; i < items.size(); i++) {
            MediaItem it = items.get(i);
            float[] v = vectors.get(i);
            if (v != null) {
                it.vector = v;
                it.indexed = true;
            } else {
                it.indexed = false;
            }
        }
        return items;
    }
}
