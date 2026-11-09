package core;

import java.nio.file.Path;
import java.util.Locale;

public class MediaItem {
    public final String id;
    public final String title;
    public final String content;
    public final Path path;
    public final Type type;
    public final String language;
    public final String[] tags;
    public float[] vector;
    public boolean indexed;

    public MediaItem(String id, String title, String content, Path path, Type type, String language, String[] tags) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.path = path;
        this.type = type;
        this.language = language;
        this.tags = tags;
    }

    public enum Type { TEXT, IMAGE, AUDIO, VIDEO }

    public static Type typeFromFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt")) return Type.TEXT;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) return Type.IMAGE;
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".avi") || lower.endsWith(".mov")) return Type.VIDEO;
        if (lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".ogg")) return Type.AUDIO;
        return null;
    }

    @Override
    public String toString() {
        return id + " [" + type + "] " + title + " (" + language + ")";
    }
}
