#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/generated"

mkdir -p "$OUT_DIR"
touch "$OUT_DIR/__init__.py"

python -m grpc_tools.protoc \
    -I"$SCRIPT_DIR/proto" \
    --python_out="$OUT_DIR" \
    --grpc_python_out="$OUT_DIR" \
    "$SCRIPT_DIR/proto/audio.proto"

echo "Generated stubs in $OUT_DIR"
