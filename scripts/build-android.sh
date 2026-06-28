#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  AetherX Local Final — Android Build Script"
echo "=========================================="

# 1. Build the full AetherX SPA (galería 3D + rutas) into public/dist
echo ""
echo "🔨  Step 1/5: Building full AetherX SPA for Capacitor (hash router, offline)..."
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
node scripts/lock-android-livewallpaper.mjs
echo "    ✓ package/applicationId: com.aetherx.livewallpaper"
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
echo "    El APK quedará en android/app/build/outputs/apk/debug/AetherX-release-signed-debug.apk"
echo ""
echo "    O abre Android Studio con: bunx cap open android"
echo "=========================================="
