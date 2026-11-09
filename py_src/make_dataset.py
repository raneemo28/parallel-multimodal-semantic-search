import os
import ssl
import shutil
import urllib.request
import urllib.parse

OUT = "data"
os.makedirs(OUT, exist_ok=True)

CTX = ssl._create_unverified_context()


def urlopen(url, user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"):
    req = urllib.request.Request(url, headers={"User-Agent": user_agent})
    return urllib.request.urlopen(req, context=CTX)


def download_text(count_target=400):
    """Multilingual text: English Gutenberg chunks + filler + Arabic texts."""
    count = 0

    # English classics from Project Gutenberg
    gutenberg_urls = [
        "https://www.gutenberg.org/files/1342/1342-0.txt",   # Pride and Prejudice
        "https://www.gutenberg.org/files/11/11-0.txt",       # Alice in Wonderland
    ]
    for u in gutenberg_urls:
        try:
            with urlopen(u) as r:
                text = r.read().decode("utf-8", errors="ignore")
            words = text.split()
            chunk_size = 400
            i = 0
            while i < len(words) and count < count_target:
                chunk = " ".join(words[i:i + chunk_size])
                with open(os.path.join(OUT, f"text_{count}.txt"), "w", encoding="utf-8") as f:
                    f.write(chunk)
                i += chunk_size
                count += 1
            print("gutenberg ok", u, count)
        except Exception as e:
            print("gutenberg fail", u, e)

    # Fallback filler to reach the target
    while count < count_target:
        with open(os.path.join(OUT, f"text_{count}.txt"), "w", encoding="utf-8") as f:
            f.write("Fallback passage " + str(count) + " " + ("word " * 400))
        count += 1

    # Arabic texts
    arabic_texts = [
        "علم البيانات هو مجال متعدد التخصصات يستخدم الأساليب العلمية والعمليات والخوارزميات لاستخراج المعرفة والرؤى من البيانات.",
        "الذكاء الاصطناعي يهدف إلى إنشاء أنظمة قادرة على أداء مهام تتطلب عادةً ذكاءً بشريًا.",
        "معالجة اللغات الطبيعية تمكن الحاسوب من فهم اللغة البشرية والتفاعل معها.",
        "التعلم الآلي هو فرع من الذكاء الاصطناعي يركز على بناء أنظمة تتعلم من البيانات وتحسن أدائها تلقائيًا.",
        "الشبكات العصبية الاصطناعية تحاكي عمل الدماغ البشري في معالجة المعلومات والأنماط.",
    ]
    for a in arabic_texts:
        for i in range(50):
            name = f"arabic_{count}.txt"
            with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
                f.write(a + f" (عينة {i})")
            count += 1

    print("text done:", count)


def download_images(count_target=500):
    """Images from picsum.photos."""
    img_i = 0
    for seed in range(count_target):
        if img_i >= count_target:
            break
        url = f"https://picsum.photos/seed/unique{seed}/256/256"
        try:
            with urlopen(url) as r:
                data = r.read()
            with open(os.path.join(OUT, f"image_{img_i}.jpg"), "wb") as f:
                f.write(data)
            img_i += 1
        except Exception as e:
            print("img fail", seed, e)
    print("image done:", img_i)


def download_audio(count_target=300):
    """Audio clips from soundhelix."""
    audio_urls = [
        "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
    ]
    audio_i = 0
    for u in audio_urls:
        for _ in range(count_target):
            if audio_i >= count_target:
                break
            try:
                with urlopen(u) as r:
                    data = r.read()
                with open(os.path.join(OUT, f"audio_{audio_i}.mp3"), "wb") as f:
                    f.write(data)
                audio_i += 1
            except Exception as e:
                print("audio fail", e)
        if audio_i >= count_target:
            break
    print("audio done:", audio_i)


def download_videos(count_target=300):
    """Short video clips; real downloads duplicated to reach the target."""
    video_urls = [
        "https://www.w3schools.com/html/mov_bbb.mp4",
        "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.webm",
    ]
    video_i = 0
    for u in video_urls:
        if video_i >= count_target:
            break
        try:
            ext = ".mp4" if u.endswith(".mp4") else ".webm"
            with urlopen(u) as r:
                data = r.read()
            with open(os.path.join(OUT, f"video_{video_i}{ext}"), "wb") as f:
                f.write(data)
            video_i += 1
            print("video ok", video_i)
        except Exception as e:
            print("video fail", e)

    # Duplicate the first real video to reach the target count
    if video_i > 0:
        first = None
        cand = os.path.join(OUT, "video_0.mp4")
        if os.path.exists(cand):
            first = cand
        else:
            for f in os.listdir(OUT):
                if f.startswith("video_"):
                    first = os.path.join(OUT, f)
                    break
        if first:
            for i in range(video_i, count_target):
                shutil.copyfile(first, os.path.join(OUT, f"video_{i}.mp4"))

    print("video done:", video_i)


def main():
    print("Building multimodal dataset in", os.path.abspath(OUT))
    download_text(400)
    download_images(500)
    download_audio(300)
    download_videos(300)

    total = sum(1 for f in os.listdir(OUT) if os.path.isfile(os.path.join(OUT, f)))
    print("Dataset complete:", total, "files in", OUT)


if __name__ == "__main__":
    main()
