import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "generated"))

import time
import numpy as np
import whisper
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

SAMPLE_RATE = 16000
MODEL_NAME = "medium"

print(f"Loading Whisper {MODEL_NAME} model...")
model = whisper.load_model(MODEL_NAME)
print("Model ready.\n")

# Warm up the model before timing
print("Warming up...")
warmup_audio = np.zeros(SAMPLE_RATE * 2, dtype=np.float32)
model.transcribe(warmup_audio, fp16=True, language="en")
print("Done.\n")

results = []

for clip_secs in range(1, 61):
    # White noise simulates the complexity of real speech better than silence
    rng = np.random.default_rng(seed=42)
    audio = rng.standard_normal(SAMPLE_RATE * clip_secs).astype(np.float32) * 0.1

    start = time.perf_counter()
    result = model.transcribe(audio, fp16=True, language="en")
    elapsed = time.perf_counter() - start

    rtf = elapsed / clip_secs
    text = result["text"].strip()[:40] or "(silence)"

    results.append((clip_secs, elapsed, rtf))
    print(f"  {clip_secs}s done", flush=True)

header = f"{'Clip (s)':>10}  {'Run time (ms)':>15}  {'Real-time factor':>18}"
divider = "-" * 47
lines = [header, divider]
for clip_secs, elapsed, rtf in results:
    lines.append(f"{clip_secs:>10}  {elapsed * 1000:>15.1f}  {rtf:>18.3f}x")

times = [r[1] for r in results]
rtfs  = [r[2] for r in results]
summary = [
    "",
    "--- Summary ---",
    f"Fastest: {min(times) * 1000:.1f}ms  (clip={results[times.index(min(times))][0]}s)",
    f"Slowest: {max(times) * 1000:.1f}ms  (clip={results[times.index(max(times))][0]}s)",
    f"Best RTF:  {min(rtfs):.3f}x  (clip={results[rtfs.index(min(rtfs))][0]}s)",
    f"Worst RTF: {max(rtfs):.3f}x  (clip={results[rtfs.index(max(rtfs))][0]}s)",
]
lines += summary

output = "\n".join(lines)
print("\n" + output)

results_path = os.path.join(os.path.dirname(__file__), "benchmark_results.txt")
with open(results_path, "w") as f:
    f.write(output + "\n")
print(f"\nResults saved to {results_path}")

clip_lengths = [r[0] for r in results]
run_times_ms = [r[1] * 1000 for r in results]

fig, ax = plt.subplots(figsize=(10, 5))
ax.plot(clip_lengths, run_times_ms, marker="o", linewidth=2, markersize=4, color="#1976D2")
ax.set_xlabel("Clip Length (s)", fontsize=12)
ax.set_ylabel("Run Time (ms)", fontsize=12)
ax.set_title(f"Whisper {MODEL_NAME} — Clip Length vs Transcription Time (GPU)", fontsize=13)
ax.grid(True, linestyle="--", alpha=0.5)
ax.set_xlim(0, 61)
ax.set_ylim(0)

graph_path = os.path.join(os.path.dirname(__file__), "benchmark.png")
fig.tight_layout()
fig.savefig(graph_path, dpi=150)
print(f"Graph saved to {graph_path}")
