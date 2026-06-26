#!/usr/bin/env node
/**
 * Build the FULL AetherX SPA (galería 3D + todas las rutas) en public/dist
 * para que Capacitor lo empaquete dentro del APK. Usa vite.capacitor.config.ts
 * con hash router (sin SSR) para ejecutarse 100% offline dentro del WebView.
 */
import { rmSync, existsSync, renameSync, readdirSync, statSync, rmdirSync } from "node:fs";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();
const publicDist = join(root, "public", "dist");

function clean() {
  rmSync(publicDist, { recursive: true, force: true });
  console.log("[prepare-android-local] cleaned public/dist");
}

function ensureIndexHtml() {
  const target = join(publicDist, "index.html");
  if (existsSync(target)) return;
  // Vite emits HTML at its source path relative to project root.
  const candidates = [
    join(publicDist, "src", "capacitor-index.html"),
    join(publicDist, "capacitor-index.html"),
  ];
  for (const c of candidates) {
    if (existsSync(c)) {
      renameSync(c, target);
      console.log(`[prepare-android-local] renamed ${c} -> index.html`);
      // cleanup empty src dir if present
      const srcDir = join(publicDist, "src");
      if (existsSync(srcDir) && statSync(srcDir).isDirectory() && readdirSync(srcDir).length === 0) {
        rmdirSync(srcDir);
      }
      return;
    }
  }
  throw new Error("[prepare-android-local] index.html not found in public/dist after build");
}

function prepare() {
  clean();
  console.log("[prepare-android-local] building full SPA via vite.capacitor.config.ts ...");
  const runner = "npx";
  const res = spawnSync(runner, ["vite", "build", "--config", "vite.capacitor.config.ts"], {
    stdio: "inherit",
    cwd: root,
    env: process.env,
  });
  if (res.status !== 0) {
    console.error("[prepare-android-local] vite build failed");
    process.exit(res.status ?? 1);
  }
  ensureIndexHtml();
  console.log("[prepare-android-local] SPA built into public/dist (index.html OK)");
}

const args = new Set(process.argv.slice(2));
if (args.has("--clean")) clean();
if (args.has("--prepare") || (!args.has("--clean") && args.size === 0)) prepare();
