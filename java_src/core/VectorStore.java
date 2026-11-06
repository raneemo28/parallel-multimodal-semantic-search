package core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class VectorStore {
    private final ConcurrentMap<String, MediaItem> store = new ConcurrentHashMap<>();
    public void put(MediaItem m) { store.put(m.id, m); }
    public MediaItem get(String id) { return store.get(id); }
    public List<MediaItem> all() { return new ArrayList<>(store.values()); }
    public int size() { return store.size(); }
    public void clear() { store.clear(); }
}
