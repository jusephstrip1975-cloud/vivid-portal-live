#!/usr/bin/env bash
# Downloads the vendored FFmpegKit AAR into android/app/libs/.
# Arthenica retired ffmpeg-kit from Maven Central in 2025, so the AAR
# must be pulled from a mirror at build time (it is too large to commit).
set -euo pipefail

LIBS_DIR="android/app/libs"
AAR_NAME="ffmpeg-kit-min-6.0-2.aar"
TARGET="${LIBS_DIR}/${AAR_NAME}"

mkdir -p "${LIBS_DIR}"

if [ -f "${TARGET}" ] && [ "$(stat -c%s "${TARGET}" 2>/dev/null || stat -f%z "${TARGET}")" -gt 1000000 ]; then
  echo "[fetch-ffmpegkit] ${TARGET} already present, skipping."
  exit 0
fi

URLS=(
  "https://maven.aliyun.com/repository/public/com/arthenica/ffmpeg-kit-min/6.0-2/ffmpeg-kit-min-6.0-2.aar"
  "https://maven.aliyun.com/repository/central/com/arthenica/ffmpeg-kit-min/6.0-2/ffmpeg-kit-min-6.0-2.aar"
)

for url in "${URLS[@]}"; do
  echo "[fetch-ffmpegkit] trying ${url}"
  if curl -fsSL --retry 3 --retry-delay 2 -o "${TARGET}" "${url}"; then
    SIZE=$(stat -c%s "${TARGET}" 2>/dev/null || stat -f%z "${TARGET}")
    if [ "${SIZE}" -gt 1000000 ]; then
      echo "[fetch-ffmpegkit] OK (${SIZE} bytes) from ${url}"
      exit 0
    else
      echo "[fetch-ffmpegkit] file too small (${SIZE} bytes), retrying..."
      rm -f "${TARGET}"
    fi
  fi
done

echo "[fetch-ffmpegkit] FAILED to download ${AAR_NAME}" >&2
exit 1
