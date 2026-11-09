package ui;

import api.EmbeddingApiClient;
import concurrency.BenchmarkRunner;
import concurrency.ParallelIndexer;
import concurrency.ParallelSearcher;
import concurrency.SequentialSearcher;
import core.MediaItem;
import core.SearchResult;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SearchGUI extends JFrame {
    private static final String API_BASE = "http://localhost:8000";
    private static final int TOP_K = 10;

    private final EmbeddingApiClient client = new EmbeddingApiClient(API_BASE);
    private final ParallelIndexer indexer = new ParallelIndexer(client);

    private List<MediaItem> dataset = new ArrayList<>();
    private List<MediaItem> indexedItems = new ArrayList<>();
    private File selectedFile = null;

    private final JTextField threadField = new JTextField("4", 6);
    private final JTextField queryField = new JTextField(30);
    private final JTextArea log = new JTextArea(14, 60);
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"N", "Sequential (ms)", "Parallel (ms)", "Speedup"}, 0);
    private final JTable table = new JTable(tableModel);
    private final JTextField minField = new JTextField("1", 6);
    private final JTextField maxField = new JTextField("16", 6);
    private final JLabel selectedFileLabel = new JLabel("No file selected");

    public SearchGUI() {
        super("Multimodal Semantic Search - Benchmark");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Thread count N:"));
        top.add(threadField);
        JButton idxBtn = new JButton("Index Dataset");
        JButton searchBtn = new JButton("Search");
        JButton benchBtn = new JButton("Run Benchmark");
        top.add(idxBtn); top.add(searchBtn); top.add(benchBtn);
        top.add(new JLabel("Query text:"));
        top.add(queryField);
        top.add(new JLabel("Selected file:"));
        top.add(selectedFileLabel);
        JButton browseBtn = new JButton("Browse...");
        top.add(browseBtn);
        add(top, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), new JScrollPane(log));
        split.setResizeWeight(0.4);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(new JLabel("Data dir:"));
        JTextField dataDirField = new JTextField("data", 20);
        bottom.add(dataDirField);
        bottom.add(new JLabel("Benchmark min N:"));
        bottom.add(minField);
        bottom.add(new JLabel("max N:"));
        bottom.add(maxField);
        add(bottom, BorderLayout.SOUTH);

        idxBtn.addActionListener(e -> runAsync(() -> doIndex(dataDirField.getText().trim())));
        searchBtn.addActionListener(e -> runAsync(this::doSearch));
        benchBtn.addActionListener(e -> runAsync(this::doBenchmark));
        browseBtn.addActionListener(e -> browseMedia());

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);

        pack();
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void runAsync(Runnable r) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                r.run();
                return null;
            }
        };
        worker.execute();
    }

    private void log(String s) {
        SwingUtilities.invokeLater(() -> log.append(s + System.lineSeparator()));
    }

    private void clearTable() {
        SwingUtilities.invokeLater(() -> tableModel.setRowCount(0));
    }

    private void addBenchRow(int n, double seq, double par, double speedup) {
        SwingUtilities.invokeLater(() -> tableModel.addRow(
                new Object[]{n, String.format("%.2f", seq), String.format("%.2f", par), String.format("%.2f", speedup)}));
    }

    /** Parse and validate a non-zero positive thread count from a text field. */
    private int parseThreadCount(JTextField field, String label) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(label + " is empty.");
        }
        int n;
        try {
            n = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be an integer.");
        }
        if (n <= 0) {
            throw new IllegalArgumentException(label + " must be a non-zero positive number.");
        }
        return n;
    }

    /** Build thread counts from min to max: min, min*2, min*4, ... plus max if not reached. */
    private List<Integer> buildThreadCounts(int min, int max) {
        List<Integer> counts = new ArrayList<>();
        for (int n = min; n <= max; n *= 2) {
            counts.add(n);
            if (n >= max / 2) break;
        }
        if (counts.isEmpty() || counts.get(counts.size() - 1) != max) {
            counts.add(max);
        }
        return counts;
    }

    private List<MediaItem> loadDataset(String dir) {
        List<MediaItem> items = new ArrayList<>();
        File d = new File(dir);
        File[] files = d.listFiles();
        if (files == null) return items;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        int idx = 0;
        for (File f : files) {
            String name = f.getName();
            MediaItem.Type type = MediaItem.typeFromFileName(name);
            if (type == null) continue;
            String content = null;
            if (type == MediaItem.Type.TEXT) {
                try { content = Files.readString(f.toPath()); } catch (Exception ignored) { content = ""; }
            }
            items.add(new MediaItem("item-" + idx++, name, content, f.toPath(), type, "en", new String[0]));
        }
        return items;
    }

    /** Resolve the query: file takes precedence, otherwise text field. Returns the MediaItem to embed. */
    private MediaItem resolveQuery() {
        if (selectedFile != null) {
            MediaItem.Type type = MediaItem.typeFromFileName(selectedFile.getName());
            String content = null;
            if (type == MediaItem.Type.TEXT) {
                try {
                    content = Files.readString(selectedFile.toPath());
                } catch (Exception e) {
                    throw new IllegalArgumentException("Could not read file: " + e.getMessage());
                }
            }
            return new MediaItem("query", selectedFile.getName(), content, selectedFile.toPath(), type, "en", new String[0]);
        }
        String text = queryField.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Enter query text or browse for a media file.");
        }
        return new MediaItem("query", text, text, null, MediaItem.Type.TEXT, "en", new String[0]);
    }

    private void doIndex(String dir) {
        int n = parseThreadCount(threadField, "Thread count N");
        long t0 = System.nanoTime();
        dataset = loadDataset(dir);
        if (dataset.isEmpty()) {
            log("No dataset items found in: " + dir);
            return;
        }
        log("Loaded " + dataset.size() + " dataset items from " + dir);
        try {
            ParallelIndexer.Stats stats = indexer.index(dataset, n);
            indexedItems = dataset;
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log(String.format("Indexed %d/%d items in %d ms (N=%d)", stats.success, stats.total, ms, n));
            if (stats.failed > 0) log("Failed: " + stats.failed);
        } catch (Exception e) {
            log("Index error: " + e.getMessage());
        }
    }

    private void doSearch() {
        int n = parseThreadCount(threadField, "Thread count N");
        MediaItem q;
        try {
            q = resolveQuery();
        } catch (IllegalArgumentException e) {
            log(e.getMessage());
            return;
        }
        try {
            float[] qv = client.embed(q);
            ParallelSearcher ps = new ParallelSearcher(indexedItems.isEmpty() ? dataset : indexedItems);
            ParallelSearcher.SearchStats stats = ps.search(qv, TOP_K, n);
            log(String.format("Search N=%d (%s) took %.2f ms, top %d results:",
                    n, q.title, stats.elapsedMs, TOP_K));
            for (SearchResult r : stats.results) {
                log(String.format("  score=%.4f  %s  [%s]", r.score, r.item.title, r.item.type));
            }
        } catch (Exception e) {
            log("Search error: " + e.getMessage());
        }
    }

    private void doBenchmark() {
        List<MediaItem> searchSet = indexedItems.isEmpty() ? dataset : indexedItems;
        if (searchSet.isEmpty()) {
            log("Please index the dataset first.");
            return;
        }
        int min = parseThreadCount(minField, "Benchmark min N");
        int max = parseThreadCount(maxField, "Benchmark max N");
        if (min > max) {
            log("Benchmark min N must be <= max N.");
            return;
        }

        clearTable();
        MediaItem q;
        try {
            q = resolveQuery();
        } catch (IllegalArgumentException e) {
            // fall back to default text query for benchmark
            q = new MediaItem("query", "benchmark query", "benchmark query", null, MediaItem.Type.TEXT, "en", new String[0]);
            log("Benchmark using default query: benchmark query");
        }
        float[] qv;
        try {
            qv = client.embed(q);
            log("Benchmark query: " + q.title + (selectedFile != null ? " (file)" : " (text)"));
        } catch (Exception e) {
            log("Could not embed benchmark query: " + e.getMessage());
            return;
        }
        try {
            List<Integer> counts = buildThreadCounts(min, max);
            BenchmarkRunner runner = new BenchmarkRunner(searchSet, qv, TOP_K);
            double sequentialMs = new SequentialSearcher(searchSet).search(qv, TOP_K).elapsedMs;
            List<BenchmarkRunner.BenchmarkResult> results = runner.run(counts);
            for (BenchmarkRunner.BenchmarkResult r : results) {
                addBenchRow(r.threads, sequentialMs, r.parallelMs, r.speedup);
                log(String.format("N=%d  seq=%.2f ms  par=%.2f ms  speedup=%.2f",
                        r.threads, r.sequentialMs, r.parallelMs, r.speedup));
            }
        } catch (Exception e) {
            log("Benchmark error: " + e.getMessage());
        }
    }

    private void browseMedia() {
        JFileChooser fc = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Media files", "txt", "jpg", "jpeg", "png", "mp4", "webm", "avi", "mov", "wav", "mp3", "ogg");
        fc.setFileFilter(filter);
        fc.setAcceptAllFileFilterUsed(true);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = fc.getSelectedFile();
            selectedFileLabel.setText(selectedFile.getAbsolutePath());
            log("Selected file: " + selectedFile.getAbsolutePath());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SearchGUI::new);
    }
}
