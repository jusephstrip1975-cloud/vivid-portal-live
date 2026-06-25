#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  AetherX Local Final — Android Build Script"
echo "=========================================="

# 1. Prepare the static local fallback asset. The debug APK itself is native.
echo ""
echo "🔨  Step 1/5: Preparing local final marker..."
node scripts/prepare-android-local.mjs --prepare

# 2. Add Android platform if missing
echo ""
echo "📱  Step 2/5: Checking Android platform..."
if [ ! -d "android" ]; then
  echo "    Android platform not found. Adding it now..."
  bunx cap add android
else
  echo "    Android platform already present."
fi

# 3. Sync Capacitor metadata, then lock Android back to native local-final mode.
echo ""
echo "🔄  Step 3/5: Syncing Capacitor + Android..."
bunx cap sync android

# 4. Enforce native MainActivity and package id.
echo ""
echo "🧩  Step 4/5: Locking native local-final Android project..."
node scripts/lock-android-no-browser.mjs
echo "    ✓ package/applicationId: com.aetherx.localfinal"
echo "    ✓ app_name: AetherX Local Final"
echo "    ✓ pantalla nativa local sin navegador externo."

# 5. Open Android Studio
echo ""
echo "🚀  Step 5/5: Listo. Para compilar el APK ejecuta:"
echo ""
echo "    cd android"
echo "    ./gradlew clean assembleDebug      # Linux/macOS"
echo "    .\\gradlew.bat clean assembleDebug  # Windows"
echo ""
echo "    El APK quedará en android/app/build/outputs/apk/debug/app-aetherx-localfinal-debug.apk"
echo ""
echo "    O abre Android Studio con: bunx cap open android"
echo "=========================================="
