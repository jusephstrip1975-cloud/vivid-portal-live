#!/usr/bin/env node
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const assetsDir = join(root, "android", "app", "src", "main", "assets", "public");
const indexPath = join(assetsDir, "index.html");

function fail(message) {
  throw new Error(`[verify-capacitor-assets] ${message}`);
}

if (!existsSync(assetsDir) || !statSync(assetsDir).isDirectory()) {
  fail(`missing assets dir: ${assetsDir}`);
}
if (!existsSync(indexPath)) {
  fail(`missing packaged index.html: ${indexPath}`);
}

const indexHtml = readFileSync(indexPath, "utf8");
if (!indexHtml.includes("NEW_BUILD_LOADED_OK")) {
  fail("packaged index.html does not contain NEW_BUILD_LOADED_OK");
}
if (!indexHtml.includes("APP_BUILD_VERSION=")) {
  fail("packaged index.html does not contain APP_BUILD_VERSION log marker");
}
if (indexHtml.includes("__AETHERX_BUILD_VERSION__")) {
  fail("build version placeholder was not replaced");
}
if (indexHtml.includes("APP LOCAL AETHERX VERSION FINAL")) {
  fail("old local-final marker is still packaged");
}

const files = readdirSync(assetsDir);
console.log(`[verify-capacitor-assets] ASSETS_PUBLIC_OK files=${files.join(",")}`);
console.log("[verify-capacitor-assets] NEW_BUILD_LOADED_OK marker confirmed in android/app/src/main/assets/public/index.html");