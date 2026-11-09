package core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.Locale;
import java.util.stream.Stream;

public class Dataset {
    private static final String[] LANGUAGES = {"en", "es", "fr", "de", "zh", "ja", "ar", "hi", "ru", "pt"};
    private final List<MediaItem> items = new ArrayList<>();

    public static Dataset load(Path dir) throws IOException {
        Dataset ds = new Dataset();
        if (!Files.isDirectory(dir)) {
            System.err.println("Dataset directory not found: " + dir);
            return ds;
        }
        List<Path> sorted = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(sorted::add);
        }
        sorted.sort(Comparator.comparing(p -> p.getFileName().toString()));
        int idx = 0;
        for (Path p : sorted) {
            String name = p.getFileName().toString();
            MediaItem.Type type = MediaItem.typeFromFileName(name);
            if (type == null) continue;
            String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
            String lang = LANGUAGES[idx % LANGUAGES.length];
            idx++;
            String content = null;
            String[] tags;
            if (type == MediaItem.Type.TEXT) {
                try { content = Files.readString(p); } catch (IOException e) { content = ""; }
                tags = new String[]{"text", lang};
            } else if (type == MediaItem.Type.IMAGE) {
                tags = new String[]{"image", lang};
            } else if (type == MediaItem.Type.VIDEO) {
                tags = new String[]{"video", lang};
            } else {
                tags = new String[]{"audio", lang};
            }
            ds.items.add(new MediaItem("item-" + stem, stem, content, p, type, lang, tags));
        }
        return ds;
    }

    public List<MediaItem> items() { return Collections.unmodifiableList(items); }
    public int size() { return items.size(); }
}
