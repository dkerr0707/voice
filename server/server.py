import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "generated"))

import grpc
import wave
import io
import datetime
import logging
import threading
import time
import numpy as np
from concurrent import futures
import whisper

import audio_pb2
import audio_pb2_grpc

SAVE_DIR = os.path.join(os.path.dirname(__file__), "recordings")
PORT = 50051
STEP_SECONDS = 0.5
WINDOW_SECONDS = 5 
DEBUG_SAVE_WAV = os.environ.get("DEBUG_SAVE_WAV", "false").lower() == "true"

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

logger.info("Loading Whisper medium model...")
model = whisper.load_model("medium")
whisper_lock = threading.Lock()
logger.info("Whisper model ready.")


def _pcm_to_wav(pcm_data: bytes, sample_rate: int, channels: int, bit_depth: int) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(channels)
        wf.setsampwidth(bit_depth // 8)
        wf.setframerate(sample_rate)
        wf.writeframes(pcm_data)
    return buf.getvalue()


def _transcribe(audio_f32: np.ndarray) -> tuple[str, float]:
    start = time.perf_counter()
    with whisper_lock:
        result = model.transcribe(audio_f32, fp16=True, language="en")
    elapsed_ms = (time.perf_counter() - start) * 1000
    return result["text"].strip(), elapsed_ms


class AudioServiceServicer(audio_pb2_grpc.AudioServiceServicer):

    def StreamAudio(self, request_iterator, context):
        peer = context.peer()
        logger.info(f"Stream started from {peer}")

        sample_rate = 16000
        step_buffer = np.array([], dtype=np.float32)
        full_audio = np.array([], dtype=np.float32)
        full_pcm = bytearray()

        for chunk in request_iterator:
            sample_rate = chunk.sample_rate
            full_pcm.extend(chunk.pcm_data)

            audio_f32 = np.frombuffer(chunk.pcm_data, dtype=np.int16).astype(np.float32) / 32768.0
            step_buffer = np.concatenate([step_buffer, audio_f32])
            full_audio = np.concatenate([full_audio, audio_f32])

            if len(step_buffer) >= sample_rate * STEP_SECONDS:
                window = full_audio[-int(sample_rate * WINDOW_SECONDS):]
                window_secs = len(window) / sample_rate
                text, elapsed_ms = _transcribe(window)
                logger.info(f"{window_secs:.1f}s  {elapsed_ms:.0f}ms  {text}")
                if text:
                    yield audio_pb2.TranscriptionChunk(text=text)
                step_buffer = np.array([], dtype=np.float32)

        # Transcribe any remaining audio in the step buffer
        if len(step_buffer) > sample_rate * 0.5:
            window = full_audio[-int(sample_rate * WINDOW_SECONDS):]
            window_secs = len(window) / sample_rate
            text, elapsed_ms = _transcribe(window)
            logger.info(f"{window_secs:.1f}s  {elapsed_ms:.0f}ms  {text}")
            if text:
                yield audio_pb2.TranscriptionChunk(text=text)

        if DEBUG_SAVE_WAV:
            try:
                os.makedirs(SAVE_DIR, exist_ok=True)
                wav_bytes = _pcm_to_wav(bytes(full_pcm), sample_rate, 1, 16)
                timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S_%f")
                filename = os.path.join(SAVE_DIR, f"recording_{timestamp}.wav")
                with open(filename, "wb") as f:
                    f.write(wav_bytes)
                logger.info(f"Saved {os.path.basename(filename)} ({len(wav_bytes) / 1024:.1f} KB)")
            except Exception as e:
                logger.exception("Failed to save WAV")


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    audio_pb2_grpc.add_AudioServiceServicer_to_server(AudioServiceServicer(), server)
    server.add_insecure_port(f"[::]:{PORT}")
    server.start()
    logger.info(f"gRPC server listening on port {PORT}")
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
