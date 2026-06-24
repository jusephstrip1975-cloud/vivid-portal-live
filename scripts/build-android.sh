#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  AetherX — Android Build Script"
echo "=========================================="

# 1. Build the web app
echo ""
echo "🔨  Step 1/5: Building web app..."
bun run build

# 2. Add Android platform if missing
echo ""
echo "📱  Step 2/5: Checking Android platform..."
if [ ! -d "android" ]; then
  echo "    Android platform not found. Adding it now..."
  bunx cap add android
else
  echo "    Android platform already present."
fi

# 3. Sync Capacitor with Android
echo ""
echo "🔄  Step 3/5: Syncing Capacitor + Android..."
bunx cap sync android

# 4. Sobrescribir MainActivity y strings con la plantilla AetherX
echo ""
echo "🧩  Step 4/5: Inyectando MainActivity personalizado..."
MAIN_ACTIVITY_DIR="android/app/src/main/java/com/aetherx/wallpapers"
STRINGS_FILE="android/app/src/main/res/values/strings.xml"
mkdir -p "$MAIN_ACTIVITY_DIR"
cp android-template/MainActivity.java "$MAIN_ACTIVITY_DIR/MainActivity.java"
if [ -f "android-template/strings.xml" ]; then
  cp android-template/strings.xml "$STRINGS_FILE"
fi
echo "    ✓ MainActivity.java actualizado."
echo "    ✓ strings.xml actualizado."

# 5. Open Android Studio
echo ""
echo "🚀  Step 5/5: Listo. Para compilar el APK ejecuta:"
echo ""
echo "    cd android"
echo "    ./gradlew clean assembleDebug      # Linux/macOS"
echo "    .\\gradlew.bat clean assembleDebug  # Windows"
echo ""
echo "    El APK quedará en android/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "    O abre Android Studio con: bunx cap open android"
echo "=========================================="
