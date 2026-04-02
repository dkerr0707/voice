# Voice Recorder

An Android app built with Kotlin and Jetpack Compose that records audio, plays it back on device, and uploads it to a Python gRPC server which saves it as a WAV file.

## Features

- Record audio via the device microphone
- Playback the recording immediately after stopping
- Audio kept in memory using `AudioRecord` and `AudioTrack` — no temp files
- Uploads recording to a gRPC server in the background after each recording

## Architecture

MVVM with no XML layouts — all UI is written in Jetpack Compose.

```
android/app/src/main/java/com/voice/recorder/
├── MainActivity.kt
├── models/
│   ├── AppViewModel.kt        # State machine, coordinates models
│   ├── AudioRecorderModel.kt  # Captures PCM audio via AudioRecord
│   ├── AudioPlayerModel.kt    # Plays back PCM audio via AudioTrack
│   └── AudioUploadClient.kt   # gRPC client, uploads PCM to server
└── ui/
    └── MainScreen.kt          # Compose UI

server/
├── proto/audio.proto          # gRPC service definition
├── server.py                  # Receives audio, saves as WAV
├── requirements.txt
└── generate_proto.sh          # Generates Python stubs
```

## Requirements

### Android
- Android SDK 34
- Min SDK 26 (Android 8.0)
- Java 17
- Gradle 8.7

### Server
- Python 3.8+
- Device and server on the same network

## Setup

### Server

```bash
cd server
pip install -r requirements.txt
bash generate_proto.sh
python server.py
```

Recordings are saved to `server/recordings/` as timestamped WAV files.

### Android

Update `SERVER_HOST` in `AudioUploadClient.kt` with your machine's LAN IP, then:

```bash
cd android
./run.sh
```

This will build, install, and launch the app on a connected device or emulator.

## Permissions

- `RECORD_AUDIO` — requested at runtime on first launch
- `INTERNET` — required for gRPC upload
