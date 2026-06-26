#!/usr/bin/env node
/**
 * Post-`cap sync` verification for the AetherX live-wallpaper build.
 * Does NOT overwrite Capacitor-generated files; only checks invariants.
 */
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.env.CAPACITOR_ROOT_DIR || process.cwd();
const appRoot = join(root, "android", "app");
const manifestPath = join(appRoot, "src", "main", "AndroidManifest.xml");
const mainActivityPath = join(appRoot, "src", "main", "java", "com", "aetherx", "livewallpaper", "MainActivity.java");
const pluginPath = join(appRoot, "src", "main", "java", "com", "aetherx", "livewallpaper", "wallpaper", "AetherXLiveWallpaperPlugin.java");
const servicePath = join(appRoot, "src", "main", "java", "com", "aetherx", "livewallpaper", "wallpaper", "AetherXLiveWallpaperService.java");
const wallpaperXml = join(appRoot, "src", "main", "res", "xml", "aetherx_wallpaper.xml");

function fail(msg) { throw new Error(`[AetherX Lock] ${msg}`); }
function read(p) {
  if (!existsSync(p)) fail(`Missing required file: ${p}`);
  return readFileSync(p, "utf8");
}

const manifest = read(manifestPath);
read(mainActivityPath);
read(pluginPath);
read(servicePath);
read(wallpaperXml);

if (!manifest.includes("com.aetherx.livewallpaper.MainActivity")) {
  fail("AndroidManifest.xml must declare com.aetherx.livewallpaper.MainActivity");
}
if (!manifest.includes("com.aetherx.livewallpaper.wallpaper.AetherXLiveWallpaperService")) {
  fail("AndroidManifest.xml must declare AetherXLiveWallpaperService");
}
if (!manifest.includes("android.permission.BIND_WALLPAPER")) {
  fail("Live wallpaper service must require android.permission.BIND_WALLPAPER");
}
if (!manifest.includes("android.service.wallpaper.WallpaperService")) {
  fail("Live wallpaper service must declare WallpaperService intent-filter");
}

console.log("[AetherX Lock] Live wallpaper wiring verified.");
