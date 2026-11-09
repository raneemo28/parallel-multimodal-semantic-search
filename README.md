# Multimodal Semantic Search (Java + EmbeddingGemma 2)

**Headline results:** 5.9× search speedup, 1.4× indexing speedup (8 threads vs. 1 thread, 1,200 items). Parallelism works because `/embed_batch` amortizes GPU overhead and `ParallelSearcher` splits cosine similarity across threads — see Benchmarking below for details.

A university parallel-programming project: a Java desktop GUI (Swing) that performs semantic search over a multimodal, multilingual media dataset (text, images, audio, video) using Google's EmbeddingGemma 2 via a FastAPI backend, with sequential vs. parallel search benchmarking and Amdahl's-law speedup reporting.

## What the app does

1. **Indexes a local dataset** — loads every file in `data/` (text, images, audio, video), sends each item to the EmbeddingGemma 2 API to get a 768-dimensional vector, and stores the vector with the item.
2. **Searches by similarity** — you type a text query or browse for a media file; the app embeds it and runs cosine-similarity search against all indexed vectors, returning the top-K matches.
3. **Benchmarks threading** — measures sequential search time vs. parallel search time across thread counts (`N = 1, 2, 4, 8, …`) and reports `Speedup = T_sequential / T_parallel` for Amdahl's-law analysis.

## Project structure

```
HW_1/
├── README.md
├── py_src/
│   ├── backend.py          # Python FastAPI embedding server (EmbeddingGemma 2)
│   └── make_dataset.py     # Single-file multimodal dataset generator
├── java_src/
│   ├── Main.java           # Application entry point
│   ├── api/
│   │   └── EmbeddingApiClient.java   # Java HTTP client for /embed and /embed_batch
│   ├── core/
│   │   ├── MediaItem.java            # Dataset item model (TEXT/IMAGE/AUDIO/VIDEO)
│   │   ├── SearchResult.java         # Item + cosine score, Comparable
│   │   ├── CosineSimilarity.java     # compute(float[], float[])
│   │   └── Dataset.java              # Loads files from a directory
│   ├── concurrency/
│   │   ├── ParallelIndexer.java      # Fixed thread pool, batched /embed_batch requests
│   │   ├── IndexTask.java            # One task per batch
│   │   ├── ParallelSearcher.java     # Chunked parallel top-K search
│   │   ├── SearchTask.java           # Top-K over a chunk
│   │   ├── SequentialSearcher.java   # Single-threaded top-K search with timing
│   │   └── BenchmarkRunner.java      # Runs benchmark across thread counts
│   └── ui/
│       └── SearchGUI.java            # Swing GUI (index, search, benchmark, log)
├── docs/
│   └── Parallel_Semantic_Search_Report.pdf  # Project report
└── .gitignore              # Ignores out/, data/, .venv/, __pycache__/
```

## Prerequisites

**Java:**
- JDK 21+ (`javac` and `java` on PATH)

**Python (backend):**
- Python 3.8+
- `fastapi`, `uvicorn`, `sentence-transformers`, `torch`, `pillow`, `ffmpeg` (for dataset generation)

## 1) Generate the dataset

```powershell
python py_src\make_dataset.py
# default: 400 text, 500 images, 200 audio, 300 videos into ./data

# Or customize counts:
python py_src\make_dataset.py --text 400 --images 500 --audio 200 --videos 300 --out data
```

`make_dataset.py` creates multilingual `text_*.txt`, synthetic `image_*.jpg` (Pillow), `audio_*.mp3`, and short `video_*.mp4`/`.webm` (ffmpeg) files.

The dataset is already present in `data/` — skip this step if you don't need to regenerate.

## 2) Start the embedding API (required before running the app)

The Java app **always calls the real EmbeddingGemma 2 API** — there is no offline/mock mode.

### Option A: Local backend (CPU) — simplest, slower indexing

```powershell
cd py_src
pip install fastapi uvicorn sentence-transformers pillow torch
python backend.py
# Server listens on http://127.0.0.1:8000
```

Keep this terminal open while using the Java app.

### Option B: Colab GPU backend — much faster indexing (recommended)

EmbeddingGemma 2 on CPU is slow for 1,200+ items. A GPU backend indexes far faster.

1. Open a new notebook in **Google Colab**.
2. Enable GPU: **Runtime → Change runtime type → T4 GPU**.
3. Install dependencies:
   ```python
   !pip install fastapi uvicorn sentence-transformers torch pyngrok
   ```
4. Upload `py_src/backend.py` to Colab (or paste its contents into a cell).
5. Start the backend + ngrok tunnel:
   ```python
   import uvicorn, threading, pyngrok
   from backend import app

   ngrok_tunnel = pyngrok.ngrok.connect(8000)
   print("Public URL:", ngrok_tunnel.public_url)

   threading.Thread(target=lambda: uvicorn.run(app, host="0.0.0.0", port=8000), daemon=True).start()
   ```
6. Copy the `https://…ngrok.io` URL.
7. In `java_src/ui/SearchGUI.java`, change:
   ```java
   private static final String API_BASE = "https://your-ngrok-url.ngrok.io";
   ```
8. Recompile the Java app (step 3) and run.

**Backend threading:** `backend.py` uses a `ThreadPoolExecutor(max_workers=200)` to accept concurrent requests, with a `threading.Lock` around `model.encode()` because the CUDA context is not thread-safe. Requests are handled concurrently but GPU inference is serialized, one batch at a time.

**Security note:** The local backend binds to `127.0.0.1` only (not `0.0.0.0`). If you expose it via ngrok, the tunnel provides the public endpoint; do not change the bind address to `0.0.0.0` unless you add authentication.

## 3) Compile the Java application

```powershell
javac -d out -sourcepath java_src -encoding UTF-8 java_src/Main.java java_src/api/*.java java_src/core/*.java java_src/concurrency/*.java java_src/ui/*.java
```

Output classes go to `out/`.

## 4) Run the application

```powershell
java -cp out Main
```

The Swing window opens. Make sure the backend is running first, otherwise indexing/search will report connection errors.

## Using the GUI

| Control | What it does |
|---------|-------------|
| **Thread count N** | Enter a non-zero positive integer (1, 2, 4, 8, 16, …). Invalid input is rejected before indexing/searching. |
| **Index Dataset** | Loads `data/` and embeds every item via the API using a fixed thread pool of `N` threads. Items are split into `N` batches (one per thread) and sent to `/embed_batch`. Success/failure counts are logged. |
| **Query text** | Type a multilingual text query. |
| **Browse…** | Open the file picker and select a text, image, audio, or video file to use as the search query. The path appears next to "Selected file:". A file takes precedence over the text box. |
| **Search** | Embeds the selected file (text content sent as text; media uploaded as file) or the typed query text, then runs parallel top-K cosine-similarity search split into `N` chunks with a Top-K reduction. Logs results. |
| **Run Benchmark** | Enter **min N** and **max N**. Measures sequential search time (with warm-up, median of repeated runs), then parallel time for `min, min*2, min*4, …` up to `max` (ensuring `max` is included). Computes `Speedup = T_sequential / T_parallel`. Uses the selected file (or typed text, else a default query). Results appear in the table and log. |

All heavy work (indexing, search, benchmarking) runs on `SwingWorker` background threads, so the UI stays responsive.

## Threading model (for the homework)

- **Indexing:** `ParallelIndexer` creates a `java.util.concurrent.ExecutorService` fixed thread pool of `N` threads. It splits the dataset into `N` batches; each `IndexTask` sends one `/embed_batch` request containing all items in its batch. Results are collected via `Future` and aggregated into success/failure counts. An overall 600s timeout prevents indefinite hangs.
- **Search:** `ParallelSearcher` splits the indexed item list into `N` chunks; each `SearchTask` computes top-K cosine scores over its chunk. A `PriorityQueue`-based reduction merges the per-chunk top-K lists into the global top-K. Errors from failed chunks are propagated (no silent swallowing).
- **Benchmark:** `BenchmarkRunner` runs warm-up iterations (3), then measures median time over 5 repeated runs for both sequential and parallel search at each thread count, reporting speedup.

**Why thread count matters:** Without batching, every item would be a separate HTTP request and the backend would serialize them. With `/embed_batch`, each Java thread issues one batched request, so increasing `N` genuinely overlaps more work against the backend and you can observe real speedup (up to the backend's concurrency limit).

## Benchmarking methodology

The `BenchmarkRunner` follows a rigorous measurement protocol to produce reliable speedup numbers:

1. **Warm-up**: 3 unmeasured runs (JIT compilation, cache warm-up).
2. **Measurement**: 5 timed runs per configuration; the **median** is reported to reduce noise from GC and OS scheduling.
3. **Sequential baseline**: Measured once per benchmark session (same warm-up/measurement protocol).
4. **Parallel runs**: For each thread count, the same protocol applies.
5. **Speedup**: `T_sequential_median / T_parallel_median`.

This matches the methodology described in the project report.

## Known limitations

- **Video/audio embedding:** EmbeddingGemma 2 cannot process video/audio offline. The backend attempts `model.encode()` on them and returns `500 Internal Server Error`. The Java client catches batch failures and falls back to per-item calls, so text and images still index successfully; only video/audio items are marked failed. This is a server-side model limitation, not a client bug.
- **Colab disconnects:** Free Colab runtimes disconnect after ~12 hours (often less). Long indexing jobs may be interrupted.
- **No GPU locally:** On CPU, `model.encode()` is the serial bottleneck; Java threads queue behind it. Use Colab GPU for meaningful thread speedup.

## Quick commands summary

```powershell
# 1. Generate dataset (optional, already present)
python py_src\make_dataset.py --out data

# 2. Start backend (local CPU)
python py_src\backend.py

# 3. Compile Java
javac -d out -sourcepath java_src -encoding UTF-8 java_src/Main.java java_src/api/*.java java_src/core/*.java java_src/concurrency/*.java java_src/ui/*.java

# 4. Run
java -cp out Main
```