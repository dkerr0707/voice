#!/bin/bash
set -e

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ADB=~/Android/Sdk/platform-tools/adb

echo "==> Building..."
./gradlew installDebug

echo "==> Launching..."
$ADB shell am start -n com.voice.recorder/.MainActivity

echo "==> Done."
