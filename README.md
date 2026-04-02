# Voice Recorder

An Android app built with Kotlin and Jetpack Compose that records audio, streams it in real-time to a Python gRPC server, and displays a live transcription using OpenAI Whisper running locally on GPU.

## Features

- Record audio via the device microphone
- Live transcription displayed in the app as you speak
- Sliding window transcription — fast initial guesses refined by growing corrections
- Optional local playback (toggle, off by default)
- Recordings can optionally be saved as WAV files (debug flag)
- All audio kept in memory — no temp files on device

## Architecture

MVVM with no XML layouts — all UI is written in Jetpack Compose.

```
android/app/src/main/java/com/voice/recorder/
├── MainActivity.kt
├── models/
│   ├── AppViewModel.kt        # State machine, transcription state
│   ├── AudioRecorderModel.kt  # Captures PCM via AudioRecord, emits Flow<ByteArray>
│   ├── AudioPlayerModel.kt    # Plays back PCM via AudioTrack
│   └── AudioUploadClient.kt   # Bidirectional gRPC streaming client
└── ui/
    └── MainScreen.kt          # Compose UI with live transcription box

server/
├── proto/audio.proto          # gRPC service definition
├── server.py                  # Receives audio, transcribes, streams text back
├── requirements.txt
└── generate_proto.sh          # Generates Python stubs
```

## Whisper Benchmark

Transcription time vs audio clip length for the `medium` model on GPU (RTX 5060):

![Whisper Benchmark](server/benchmark.png)

## How the Sliding Window Transcription Works

Transcription uses a two-pass approach to balance responsiveness with accuracy:

1. **Fast pass (every 1 second):** Whisper transcribes only the most recent 1 second of audio. This appears immediately in the app but may be rough — short clips give Whisper little context.

2. **Verification pass (sliding window, capped at 15 seconds):** After each fast pass, Whisper re-transcribes the last 15 seconds of accumulated audio. With more context, it produces a significantly more accurate result. This correction replaces the full displayed text in the app.

As you speak:
- Fast text appears within ~1 second per word
- Every second, the full visible text is silently replaced with a more accurate version
- The longer you speak (up to 15 seconds of context), the better the corrections get

The 15-second cap keeps transcription time bounded — without it, verification would eventually transcribe minutes of audio every second and never catch up.

## Requirements

### Android
- Android SDK 34
- Min SDK 26 (Android 8.0)
- Java 17
- Gradle 8.7

### Server
- Python 3.8+
- NVIDIA GPU with CUDA (runs on CPU without one, but much slower)
- Device and server on the same WiFi network

## Setup

### Server

```bash
cd server
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
bash generate_proto.sh
python server.py
```

Transcriptions print to the console in real-time. To also save recordings as WAV files:

```bash
DEBUG_SAVE_WAV=true python server.py
```

### Android

Update `SERVER_HOST` in `AudioUploadClient.kt` with your machine's LAN IP, then:

```bash
cd android
./run.sh
```

This will build, install, and launch the app on a connected device or emulator.

## Permissions

- `RECORD_AUDIO` — requested at runtime on first launch
- `INTERNET` — required for gRPC streaming
