package core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal .env loader. Lookup order for a key:
 * 1) real environment variable, 2) entry in the .env file, 3) the supplied default.
 * The .env file is read from the current working directory (the project root when
 * running `java -cp out Main`).
 */
public final class Env {
    private static final Map<String, String> VALUES = load(Paths.get(".env"));

    private Env() {}

    public static String get(String key, String defaultValue) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        String fromFile = VALUES.get(key);
        if (fromFile != null && !fromFile.isBlank()) return fromFile;
        return defaultValue;
    }

    private static Map<String, String> load(Path path) {
        Map<String, String> map = new HashMap<>();
        if (!Files.isRegularFile(path)) return map;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                        || (v.startsWith("'") && v.endsWith("'")))) {
                    v = v.substring(1, v.length() - 1);
                }
                map.put(k, v);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not read .env: " + e.getMessage());
        }
        return map;
    }
}
