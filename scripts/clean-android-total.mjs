#!/usr/bin/env node
import { rmSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const targets = [
  "android/.gradle",
  "android/app/build",
  "android/build",
  "node_modules/.cache",
  "public/dist",
  "dist",
  ".capacitor",
  "android/app/src/main/assets/public",
];

for (const target of targets) {
  const absolute = join(root, target);
  rmSync(absolute, { recursive: true, force: true });
  console.log(`[clean-android-total] removed ${target}`);
}

console.log("[clean-android-total] TOTAL_ANDROID_CLEAN_OK");