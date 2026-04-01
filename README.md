# Voice Recorder

An Android app built with Kotlin and Jetpack Compose that records and plays back audio entirely in memory — no files written to disk.

## Features

- Record audio via the device microphone
- Playback the recording immediately after stopping
- All audio kept in memory using `AudioRecord` and `AudioTrack`

## Architecture

MVVM with no XML layouts — all UI is written in Jetpack Compose.

```
android/app/src/main/java/com/voice/recorder/
├── MainActivity.kt
├── models/
│   ├── AppViewModel.kt        # State machine, coordinates models
│   ├── AudioRecorderModel.kt  # Captures PCM audio via AudioRecord
│   └── AudioPlayerModel.kt    # Plays back PCM audio via AudioTrack
└── ui/
    └── MainScreen.kt          # Compose UI
```

## Requirements

- Android SDK 34
- Min SDK 26 (Android 8.0)
- Java 17
- Gradle 8.7

## Build & Run

```bash
cd android
./run.sh
```

This will build, install, and launch the app on a connected device or emulator.

## Permissions

- `RECORD_AUDIO` — requested at runtime on first launch
