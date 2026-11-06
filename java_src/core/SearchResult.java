package core;

public class SearchResult implements Comparable<SearchResult> {
    public final MediaItem item;
    public final float score;

    public SearchResult(MediaItem item, float score) {
        this.item = item;
        this.score = score;
    }

    @Override
    public int compareTo(SearchResult o) {
        return Float.compare(o.score, this.score);
    }
}
