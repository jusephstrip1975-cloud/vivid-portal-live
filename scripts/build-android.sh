#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  AetherX — Android Build Script"
echo "=========================================="

# 1. Build the web app (TanStack Start -> dist/client + dist/server)
echo ""
echo "🔨  Step 1/5: Building web app..."
bun run build

# 2. Generate a fallback index.html in dist/client/
#    TanStack Start is SSR-only and does not emit a static index.html.
#    Capacitor requires one in webDir for `cap sync` to succeed.
echo ""
echo "📝  Step 2/5: Generating Capacitor fallback index.html..."
node scripts/generate-capacitor-index.mjs

# 3. Add Android platform if missing
echo ""
echo "📱  Step 3/5: Checking Android platform..."
if [ ! -d "android" ]; then
  echo "    Android platform not found. Adding it now..."
  bunx cap add android
else
  echo "    Android platform already present."
fi

# 4. Sync Capacitor with Android
echo ""
echo "🔄  Step 4/5: Syncing Capacitor + Android..."
bunx cap sync android

# 5. Open Android Studio (optional, comment out in CI)
echo ""
echo "🚀  Step 5/5: Opening Android Studio..."
bunx cap open android || true

echo ""
echo "=========================================="
echo "  Done!"
echo "=========================================="
