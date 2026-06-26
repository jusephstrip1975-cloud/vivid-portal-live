#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  AETHERX — Android Build Script"
echo "=========================================="

export AETHERX_BUILD_VERSION="${AETHERX_BUILD_VERSION:-$(date -u +%Y%m%d%H%M%S)}"
echo "APP_BUILD_VERSION=$AETHERX_BUILD_VERSION"

# 1. Limpieza total de caches, outputs y assets empaquetados antiguos.
echo ""
echo "🧹  Step 1/7: Limpieza total Android/Capacitor..."
node scripts/clean-android-total.mjs

# 2. Instalación limpia de dependencias por npm, como en el workflow final.
echo ""
echo "📦  Step 2/7: npm install..."
npm install

# 3. Build web normal solicitado y build local Capacitor offline.
echo ""
echo "🔨  Step 3/7: npm run build + build Capacitor local..."
npm run build
npm run build:capacitor

# 4. Add Android platform if missing
echo ""
echo "📱  Step 4/7: Checking Android platform..."
if [ ! -d "android" ]; then
  echo "    Android platform not found. Adding it now..."
  npx cap add android
else
  echo "    Android platform already present."
fi

# 5. Sync Capacitor metadata, then lock Android back to native local-final mode.
echo ""
echo "🔄  Step 5/7: Syncing Capacitor + Android..."
npx cap sync android

# 6. Enforce native MainActivity and package id.
echo ""
echo "🧩  Step 6/7: Verifying native Android project and packaged assets..."
node scripts/lock-android-local-final.mjs
node scripts/verify-capacitor-assets.mjs
echo "    ✓ applicationId: com.aetherx.livewallpaper"
echo "    ✓ app_name: AETHERX"
echo "    ✓ assets nuevos con NEW_BUILD_LOADED_OK."

# 7. Open Android Studio
echo ""
echo "🚀  Step 7/7: Listo. Para compilar el APK sin cache ejecuta:"
echo ""
echo "    cd android"
echo "    ./gradlew --stop || true"
echo "    ./gradlew clean cleanBuildCache assembleRelease bundleRelease --no-daemon --no-build-cache --rerun-tasks"
echo "    .\\gradlew.bat clean cleanBuildCache assembleRelease bundleRelease --no-daemon --no-build-cache --rerun-tasks"
echo ""
echo "    El APK quedará en android/app/build/outputs/apk/release/"
echo ""
echo "    O abre Android Studio con: bunx cap open android"
echo "=========================================="
