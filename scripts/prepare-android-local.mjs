#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const publicDist = join(root, "public", "dist");

function cleanPublicDist() {
  rmSync(publicDist, { recursive: true, force: true });
}

function assertLocalBundle() {
  const indexPath = join(publicDist, "index.html");
  if (!existsSync(indexPath)) {
    throw new Error("Missing public/dist/index.html after Capacitor SPA build.");
  }
  const index = readFileSync(indexPath, "utf8");
  if (!index.includes("/assets/") && !index.includes("./assets/")) {
    throw new Error("public/dist/index.html does not reference bundled local assets.");
  }
  if (/https:\/\/aetherx\.org|server\.url|loadUrl\(/i.test(index)) {
    throw new Error("public/dist/index.html contains a forbidden remote startup reference.");
  }
  if (!index.includes("aetherx-native-fallback")) {
    throw new Error("public/dist/index.html is missing the native boot fallback.");
  }
}

function normalizeHtmlOutput() {
  const nestedIndex = join(publicDist, "src", "capacitor-index.html");
  const rootIndex = join(publicDist, "index.html");
  if (existsSync(nestedIndex) && !existsSync(rootIndex)) {
    renameSync(nestedIndex, rootIndex);
    rmSync(join(publicDist, "src"), { recursive: true, force: true });
  }
  if (existsSync(rootIndex)) {
    const normalized = readFileSync(rootIndex, "utf8").replaceAll("../assets/", "./assets/");
    writeFileSync(rootIndex, normalized, "utf8");
  }
}

function prepare() {
  cleanPublicDist();
  execFileSync("bunx", ["vite", "build", "--config", "vite.capacitor.config.ts"], {
    cwd: root,
    stdio: "inherit",
  });
  normalizeHtmlOutput();
  assertLocalBundle();
  console.log("Prepared Capacitor SPA at public/dist/index.html for 100% local APK startup");
}

const args = new Set(process.argv.slice(2));
if (args.has("--clean")) cleanPublicDist();
if (args.has("--prepare") || (!args.has("--clean") && args.size === 0)) prepare();