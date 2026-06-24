#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  AetherX — Android Build Script"
echo "=========================================="

# 1. Build the web app
echo ""
echo "🔨  Step 1/4: Building web app..."
bun run build

# 2. Add Android platform if missing
echo ""
echo "📱  Step 2/4: Checking Android platform..."
if [ ! -d "android" ]; then
  echo "    Android platform not found. Adding it now..."
  bunx cap add android
else
  echo "    Android platform already present."
fi

# 3. Sync Capacitor with Android
echo ""
echo "🔄  Step 3/4: Syncing Capacitor + Android..."
bunx cap sync android

# 4. Open Android Studio
echo ""
echo "🚀  Step 4/4: Opening Android Studio..."
bunx cap open android

echo ""
echo "=========================================="
echo "  Done! Android Studio is launching."
echo "  Build the APK from Studio and install"
echo "  on your device to test live wallpapers."
echo "=========================================="
