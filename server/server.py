import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "generated"))

import grpc
import wave
import io
import datetime
import logging
import threading
import numpy as np
from concurrent import futures
import whisper

import audio_pb2
import audio_pb2_grpc

SAVE_DIR = os.path.join(os.path.dirname(__file__), "recordings")
PORT = 50051

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


class AudioServiceServicer(audio_pb2_grpc.AudioServiceServicer):

    def UploadAudio(self, request, context):
        peer = context.peer()
        pcm_size_kb = len(request.pcm_data) / 1024
        duration_s = len(request.pcm_data) / (request.sample_rate * request.channels * (request.bit_depth // 8))
        logger.info(f"Receiving audio from {peer} — {pcm_size_kb:.1f} KB PCM, {duration_s:.1f}s @ {request.sample_rate}Hz")

        try:
            os.makedirs(SAVE_DIR, exist_ok=True)

            logger.info("Converting PCM to WAV...")
            wav_bytes = _pcm_to_wav(
                request.pcm_data,
                request.sample_rate,
                request.channels,
                request.bit_depth,
            )

            timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            filename = os.path.join(SAVE_DIR, f"recording_{timestamp}.wav")

            with open(filename, "wb") as f:
                f.write(wav_bytes)

            size_kb = len(wav_bytes) / 1024
            logger.info(f"Saved {os.path.basename(filename)} ({size_kb:.1f} KB)")

            audio_f32 = np.frombuffer(request.pcm_data, dtype=np.int16).astype(np.float32) / 32768.0
            logger.info("Transcribing...")
            with whisper_lock:
                result = model.transcribe(audio_f32, fp16=True, language="en")
                text = result["text"].strip()
                print(f"\n--- Transcription ---\n{text}\n---------------------\n", flush=True)

            return audio_pb2.AudioResponse(success=True, message=text or "No speech detected")

        except Exception as e:
            logger.exception("Failed to process audio")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return audio_pb2.AudioResponse(success=False, message=str(e))


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    audio_pb2_grpc.add_AudioServiceServicer_to_server(AudioServiceServicer(), server)
    server.add_insecure_port(f"[::]:{PORT}")
    server.start()
    logger.info(f"gRPC server listening on port {PORT}")
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
