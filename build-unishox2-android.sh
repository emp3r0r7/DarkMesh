#!/usr/bin/env bash
set -euo pipefail

UNISHOX2_DIR="third_party/Unishox2"
OUT_DIR="app/src/main/jniLibs"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK_VERSION=""
API=24

while [[ $# -gt 0 ]]; do
  case "$1" in
    --unishox2-dir)
      UNISHOX2_DIR="$2"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="$2"
      shift 2
      ;;
    --sdk-dir)
      SDK_DIR="$2"
      shift 2
      ;;
    --ndk-version)
      NDK_VERSION="$2"
      shift 2
      ;;
    --api)
      API="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"

if [[ -z "$SDK_DIR" && -f "$REPO_ROOT/local.properties" ]]; then
  SDK_DIR="$(
    sed -n 's/^sdk\.dir=//p' "$REPO_ROOT/local.properties" |
      head -n 1 |
      sed 's/\\:/:/g'
  )"
fi

if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR" ]]; then
  echo "Android SDK not found. Pass --sdk-dir or set ANDROID_HOME." >&2
  exit 1
fi

NDK_ROOT="$SDK_DIR/ndk"
if [[ -n "$NDK_VERSION" ]]; then
  NDK_DIR="$NDK_ROOT/$NDK_VERSION"
else
  NDK_DIR="$(find "$NDK_ROOT" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
fi

if [[ -z "${NDK_DIR:-}" || ! -d "$NDK_DIR" ]]; then
  echo "NDK not found under $NDK_ROOT" >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin)
    HOST_TAG="darwin-x86_64"
    ;;
  Linux)
    HOST_TAG="linux-x86_64"
    ;;
  *)
    echo "Unsupported host OS: $(uname -s)" >&2
    exit 1
    ;;
esac

TOOLCHAIN_BIN="$NDK_DIR/toolchains/llvm/prebuilt/$HOST_TAG/bin"
SOURCE_FILE="$REPO_ROOT/$UNISHOX2_DIR/unishox2.c"
OUT_ROOT="$REPO_ROOT/$OUT_DIR"

if [[ ! -f "$SOURCE_FILE" ]]; then
  echo "Missing $SOURCE_FILE. Clone Unishox2 into $UNISHOX2_DIR first." >&2
  exit 1
fi

ABIS=(
  "arm64-v8a:aarch64-linux-android"
  "armeabi-v7a:armv7a-linux-androideabi"
  "x86:i686-linux-android"
  "x86_64:x86_64-linux-android"
)

for entry in "${ABIS[@]}"; do
  ABI="${entry%%:*}"
  TRIPLE="${entry#*:}"
  CLANG="$TOOLCHAIN_BIN/${TRIPLE}${API}-clang"

  if [[ ! -x "$CLANG" ]]; then
    echo "Missing compiler: $CLANG" >&2
    exit 1
  fi

  ABI_OUT="$OUT_ROOT/$ABI"
  mkdir -p "$ABI_OUT"

  OUTPUT="$ABI_OUT/libunishox2.so"
  "$CLANG" \
    -shared \
    -fPIC \
    -O2 \
    -std=c99 \
    -DUNISHOX_API_WITH_OUTPUT_LEN=1 \
    "-Wl,-z,max-page-size=16384" \
    "-Wl,-soname,libunishox2.so" \
    -Wall \
    -Wextra \
    -o "$OUTPUT" \
    "$SOURCE_FILE"

  echo "Built $OUTPUT"
done
