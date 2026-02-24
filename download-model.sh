#!/bin/bash
# FlowVoice - Vosk Model Download Script
# Run this from the project root directory

set -e

MODEL_URL="https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
MODEL_NAME="vosk-model-small-en-us-0.15"
ASSETS_DIR="app/src/main/assets"
TARGET_DIR="$ASSETS_DIR/model-en-us"

echo "🎤 FlowVoice Model Setup"
echo "========================"

# Create assets directory
mkdir -p "$ASSETS_DIR"

# Check if model already exists
if [ -d "$TARGET_DIR" ]; then
    echo "✅ Model already exists at $TARGET_DIR"
    echo "   Delete it first if you want to re-download"
    exit 0
fi

# Download
echo "📥 Downloading Vosk model (~50MB)..."
echo "   URL: $MODEL_URL"

if command -v wget &> /dev/null; then
    wget -q --show-progress "$MODEL_URL" -O "${MODEL_NAME}.zip"
elif command -v curl &> /dev/null; then
    curl -L --progress-bar "$MODEL_URL" -o "${MODEL_NAME}.zip"
else
    echo "❌ Error: wget or curl required"
    exit 1
fi

# Extract
echo "📦 Extracting model..."
unzip -q "${MODEL_NAME}.zip" -d "$ASSETS_DIR/"
mv "$ASSETS_DIR/$MODEL_NAME" "$TARGET_DIR"

# Cleanup
rm "${MODEL_NAME}.zip"

# Verify
if [ -d "$TARGET_DIR/am" ] && [ -d "$TARGET_DIR/conf" ]; then
    echo "✅ Model installed successfully!"
    echo "   Location: $TARGET_DIR"
    ls -la "$TARGET_DIR"
else
    echo "❌ Error: Model extraction may have failed"
    exit 1
fi

echo ""
echo "🚀 Ready to build! Open in Android Studio and run."
