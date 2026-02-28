#!/usr/bin/env bash
set -euo pipefail

OUTPUT_PATH="${1:-web/amazon-chime-sdk.min.js}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_FILE="$SCRIPT_DIR/$OUTPUT_PATH"
mkdir -p "$(dirname "$OUTPUT_FILE")"

URLS=(
  "https://unpkg.com/amazon-chime-sdk-js@3.20.0/build/amazon-chime-sdk.min.js"
  "https://cdn.jsdelivr.net/npm/amazon-chime-sdk-js@3.20.0/build/amazon-chime-sdk.min.js"
)

for url in "${URLS[@]}"; do
  echo "Downloading Chime SDK from: $url"
  if curl -fL "$url" -o "$OUTPUT_FILE"; then
    if [ -s "$OUTPUT_FILE" ]; then
      echo "Chime SDK ready at: $OUTPUT_FILE"
      exit 0
    fi
  fi
  echo "Failed from $url"
done

echo "Failed to download amazon-chime-sdk.min.js from all configured sources." >&2
exit 1
