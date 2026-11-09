import os
os.environ["HF_HUB_DISABLE_XET"] = "1"
os.environ["HF_HUB_DISABLE_SYMLINKS_WARNING"] = "1"

import asyncio
import json
import shutil
import tempfile
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor

import torch
import uvicorn
from fastapi import FastAPI, File, Form, HTTPException, UploadFile

app = FastAPI()

# ---- Device & model ----
device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"Using device: {device}")

print("Loading EmbeddingGemma 2 multimodal model...")
model = SentenceTransformer(
    "google/embeddinggemma-2",
    revision="914f7f89142e33e77833254d9c9b90c3cef7303b",
    device=device,
)
print("EmbeddingGemma 2 model loaded successfully!")

# ---- Threading ----
# Auto-scaling thread pool to serve requests, capped at 200 workers
executor = ThreadPoolExecutor(max_workers=200, thread_name_prefix="embed-worker")
# GPU inference lock: CUDA context is not thread-safe for concurrent model.forward()
model_lock = threading.Lock()


def _encode_sync(payload):
    """Run model.encode under the GPU lock."""
    with model_lock:
        return model.encode(payload).tolist()


def _save_temp_file(upload_file: UploadFile) -> str:
    """Save upload to a secure temp file with UUID name, return path."""
    suffix = os.path.splitext(upload_file.filename)[1] if upload_file.filename else ""
    # Use UUID to avoid collisions; keep extension for model compatibility
    tmp = tempfile.NamedTemporaryFile(prefix="embed_", suffix=suffix, delete=False)
    tmp_path = tmp.name
    tmp.close()
    with open(tmp_path, "wb") as buffer:
        shutil.copyfileobj(upload_file.file, buffer)
    return tmp_path


@app.post("/embed")
async def get_multimodal_embedding(
    text: str = Form(None),
    file: UploadFile = File(None),
):
    temp_file_path = None
    try:
        input_data = []
        if text and text.strip():
            input_data.append(text.strip())
        if file and file.filename:
            temp_file_path = _save_temp_file(file)
            input_data.append(temp_file_path)

        if not input_data:
            raise HTTPException(status_code=400, detail="Provide either text or a media file.")

        payload = input_data if len(input_data) > 1 else input_data[0]

        loop = asyncio.get_event_loop()
        vector = await loop.run_in_executor(executor, lambda: _encode_sync(payload))

        if temp_file_path and os.path.exists(temp_file_path):
            os.remove(temp_file_path)
        return {"vector": vector}
    except HTTPException:
        raise
    except Exception as e:
        if temp_file_path and os.path.exists(temp_file_path):
            try:
                os.remove(temp_file_path)
            except Exception:
                pass
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/embed_batch")
async def get_multimodal_embedding_batch(
    texts: str = Form(None),
    files: list[UploadFile] = File(None),
):
    """Embed multiple items in one request to amortize GPU overhead.

    - texts: JSON array of strings, or newline-separated plain text
    - files: multiple media files (videos are attempted; server returns 500 if unsupported)
    Returns {"vectors": [[...], ...]} in input order (texts first, then files).
    """
    text_list = []
    if texts:
        texts = texts.strip()
        if texts:
            try:
                parsed = json.loads(texts)
                if isinstance(parsed, list):
                    text_list = [str(t) for t in parsed]
                else:
                    text_list = [texts]
            except json.JSONDecodeError:
                text_list = [t for t in texts.splitlines() if t.strip()]

    temp_paths = []
    payload = []
    try:
        for t in text_list:
            payload.append(t)

        if files:
            for f in files:
                if f.filename:
                    tmp = _save_temp_file(f)
                    temp_paths.append(tmp)
                    payload.append(tmp)

        if not payload:
            raise HTTPException(status_code=400, detail="Provide at least one text or file.")

        # Try fast batch encode first. If any item poisons the whole batch
        # (e.g. video/audio that model.encode can't handle), fall back to
        # per-item encoding so good items still succeed.
        try:
            loop = asyncio.get_event_loop()
            vectors = await loop.run_in_executor(executor, lambda: _encode_sync(payload))
        except Exception:
            vectors = []
            loop = asyncio.get_event_loop()
            for item in payload:
                try:
                    v = await loop.run_in_executor(executor, lambda it=item: _encode_sync(it))
                    vectors.append(v)
                except Exception:
                    vectors.append(None)

        return {"vectors": vectors, "count": len(vectors)}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        for tmp in temp_paths:
            if os.path.exists(tmp):
                try:
                    os.remove(tmp)
                except Exception:
                    pass


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8000)
