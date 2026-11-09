package api;

import core.CosineSimilarity;
import core.MediaItem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class EmbeddingApiClient {
    private static final int VECTOR_DIM = 768;
    private final HttpClient http;
    private final String baseUrl;

    public EmbeddingApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public float[] embed(MediaItem item) throws IOException, InterruptedException {
        if (item.type == MediaItem.Type.TEXT) {
            String text = item.content != null && !item.content.isBlank() ? item.content : item.title;
            return embedText(text);
        }
        return embedFile(item.path);
    }

    public List<float[]> embedBatch(List<MediaItem> items) throws IOException, InterruptedException {
        if (items.isEmpty()) {
            return List.of();
        }

        List<String> texts = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        List<Integer> textIndexMap = new ArrayList<>();
        List<Integer> fileIndexMap = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            MediaItem it = items.get(i);
            if (it.type == MediaItem.Type.TEXT) {
                String text = it.content != null && !it.content.isBlank() ? it.content : it.title;
                texts.add(text);
                textIndexMap.add(i);
            } else {
                files.add(it.path);
                fileIndexMap.add(i);
            }
        }

        float[][] results = new float[items.size()][];

        if (!texts.isEmpty() || !files.isEmpty()) {
            try {
                List<float[]> vectors = embedBatchRaw(texts, files);
                int idx = 0;
                for (int i = 0; i < textIndexMap.size(); i++) {
                    results[textIndexMap.get(i)] = vectors.get(idx++);
                }
                for (int i = 0; i < fileIndexMap.size(); i++) {
                    results[fileIndexMap.get(i)] = vectors.get(idx++);
                }
            } catch (IOException e) {
                // Batch failed (e.g. a video caused 500). Fall back to per-item calls
                // so text/images still succeed and only the bad item fails.
                for (int i = 0; i < items.size(); i++) {
                    MediaItem it = items.get(i);
                    try {
                        results[i] = embed(it);
                    } catch (Exception ex) {
                        results[i] = null;
                    }
                }
            }
        }

        List<float[]> out = new ArrayList<>(items.size());
        for (float[] v : results) {
            out.add(v);
        }
        return out;
    }

    public List<float[]> embedBatchRaw(List<String> texts, List<Path> files)
            throws IOException, InterruptedException {
        if (texts.isEmpty() && files.isEmpty()) {
            return List.of();
        }

        String boundary = "----JavaBatchBoundary" + System.currentTimeMillis();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // texts as JSON array
        if (!texts.isEmpty()) {
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Disposition: form-data; name=\"texts\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write(toJsonArray(texts).getBytes(StandardCharsets.UTF_8));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        // files as multiple file parts
        for (Path file : files) {
            if (file == null || !Files.exists(file)) {
                continue;
            }
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"files\"; filename=\""
                    + file.getFileName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write(Files.readAllBytes(file));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "embed_batch"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .timeout(Duration.ofSeconds(300))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new IOException("API " + res.statusCode() + ": " + res.body());
        }
        return parseVectors(res.body());
    }

    public float[] embedText(String text) throws IOException, InterruptedException {
        String boundary = "----JavaBoundary" + System.currentTimeMillis();
        byte[] body = buildMultipart(boundary, text, null);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "embed"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new IOException("API " + res.statusCode() + ": " + res.body());
        float[] v = parseVector(res.body());
        CosineSimilarity.normalize(v);
        return v;
    }

    public float[] embedFile(Path file) throws IOException, InterruptedException {
        String boundary = "----JavaBoundary" + System.currentTimeMillis();
        byte[] body = buildMultipart(boundary, null, file);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "embed"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new IOException("API " + res.statusCode() + ": " + res.body());
        float[] v = parseVector(res.body());
        CosineSimilarity.normalize(v);
        return v;
    }

    private static byte[] buildMultipart(String boundary, String text, Path file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (text != null && !text.isBlank()) {
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Disposition: form-data; name=\"text\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write(text.getBytes(StandardCharsets.UTF_8));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        if (file != null && Files.exists(file)) {
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                    + file.getFileName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write(Files.readAllBytes(file));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
    }

    private static String toJsonArray(List<String> texts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append('"');
            sb.append(escapeJson(texts.get(i)));
            sb.append('"');
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<float[]> parseVectors(String json) throws IOException {
        int key = json.indexOf("\"vectors\"");
        if (key < 0) throw new IOException("No 'vectors' field in response: " + json);
        int start = json.indexOf('[', key);
        int end = json.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            throw new IOException("Malformed vectors array: " + json);
        }
        String inner = json.substring(start + 1, end).trim();
        if (inner.isEmpty()) {
            return List.of();
        }

        List<float[]> result = new ArrayList<>();
        int depth = 0;
        int vecStart = -1;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '[') {
                if (depth == 0) vecStart = i;
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0 && vecStart >= 0) {
                    String vecStr = inner.substring(vecStart, i + 1);
                    float[] v = parseVector(vecStr);
                    CosineSimilarity.normalize(v);
                    result.add(v);
                    vecStart = -1;
                }
            } else if (depth == 0 && c == 'n' && inner.startsWith("null", i)) {
                result.add(null);
                i += 3;
            }
        }
        return result;
    }

    private static float[] parseVector(String json) throws IOException {
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            throw new IOException("Malformed vector array: " + json);
        }
        String[] nums = json.substring(start + 1, end).split(",");
        float[] v = new float[nums.length];
        for (int i = 0; i < nums.length; i++) {
            v[i] = Float.parseFloat(nums[i].trim());
        }
        return v;
    }
}
