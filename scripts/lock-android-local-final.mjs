#!/usr/bin/env node
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

const root = process.env.CAPACITOR_ROOT_DIR || process.cwd();
const androidRoot = join(root, "android");
const appRoot = join(androidRoot, "app");
const mainActivityPath = join(appRoot, "src", "main", "java", "com", "aetherx", "localfinal", "MainActivity.java");
const oldMainActivityDir = join(appRoot, "src", "main", "java", "com", "aetherx", "wallpapers");
const manifestPath = join(appRoot, "src", "main", "AndroidManifest.xml");
const stringsPath = join(appRoot, "src", "main", "res", "values", "strings.xml");
const configXmlPath = join(appRoot, "src", "main", "res", "xml", "config.xml");
const pluginsJsonPath = join(appRoot, "src", "main", "assets", "capacitor.plugins.json");
const configJsonPath = join(appRoot, "src", "main", "assets", "capacitor.config.json");
const settingsPath = join(androidRoot, "settings.gradle");
const capacitorSettingsPath = join(androidRoot, "capacitor.settings.gradle");
const capacitorBuildPath = join(appRoot, "capacitor.build.gradle");
const templateMainActivity = join(root, "android-template", "MainActivity.java");
const templateStrings = join(root, "android-template", "strings.xml");

function fail(message) {
  throw new Error(`[AetherX Local Final] ${message}`);
}

function read(path) {
  if (!existsSync(path)) fail(`Missing required file: ${path}`);
  return readFileSync(path, "utf8");
}

function write(path, content) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content, "utf8");
}

function copyTemplate(source, destination) {
  if (!existsSync(source)) fail(`Missing template: ${source}`);
  mkdirSync(dirname(destination), { recursive: true });
  copyFileSync(source, destination);
}

function count(text, regex) {
  return Array.from(text.matchAll(regex)).length;
}

copyTemplate(templateMainActivity, mainActivityPath);
copyTemplate(templateStrings, stringsPath);
rmSync(oldMainActivityDir, { recursive: true, force: true });
write(settingsPath, "include ':app'\n");
write(capacitorSettingsPath, "// AetherX Local Final native debug build: no plugin modules are included.\n");
write(capacitorBuildPath, "// AetherX Local Final native debug build: intentionally empty.\n");
write(configXmlPath, "<?xml version='1.0' encoding='utf-8'?>\n<widget version=\"1.0.0\" xmlns=\"http://www.w3.org/ns/widgets\" xmlns:cdv=\"http://cordova.apache.org/ns/1.0\">\n</widget>\n");
write(pluginsJsonPath, "[]\n");
write(
  configJsonPath,
  JSON.stringify(
    {
      appId: "com.aetherx.localfinal",
      appName: "AetherX Local Final",
      webDir: "public/dist",
      backgroundColor: "#000000",
      android: {
        backgroundColor: "#000000",
        allowMixedContent: false,
        webContentsDebuggingEnabled: false,
        captureInput: false,
      },
      ios: { backgroundColor: "#000000", contentInset: "always" },
    },
    null,
    2,
  ) + "\n",
);

const manifest = read(manifestPath).replace(/<!--([\s\S]*?)-->/g, "");
const mainActivity = read(mainActivityPath);
const strings = read(stringsPath);
const pluginsJson = read(pluginsJsonPath);
const configJson = read(configJsonPath);

if (count(manifest, /<activity\b/g) !== 1) fail("AndroidManifest.xml must declare exactly one Activity.");
if (!manifest.includes('android:name="com.aetherx.localfinal.MainActivity"')) {
  fail("MainActivity must use com.aetherx.localfinal.MainActivity.");
}
if (count(manifest, /android\.intent\.action\.MAIN/g) !== 1) fail("Exactly one MAIN action is required.");
if (count(manifest, /android\.intent\.category\.LAUNCHER/g) !== 1) fail("Exactly one LAUNCHER category is required.");
if (!mainActivity.includes("AETHERX APP LOCAL FUNCIONANDO")) fail("MainActivity must render the local-final text.");
if (!strings.includes("AetherX Local Final") || !strings.includes("com.aetherx.localfinal")) {
  fail("strings.xml must contain the new app name and package id.");
}
if (pluginsJson.trim() !== "[]") fail("No Capacitor plugins may be registered for this APK.");

const oldPackage = ["com", "aetherx", "wallpapers"].join(".");
const oldDomain = ["aetherx", "org"].join(".");
const forbiddenTokens = [
  "activity" + "-alias",
  "android.intent.action." + "VIEW",
  "android.intent.category." + "BROW" + "SABLE",
  "android:" + "scheme=",
  "android:" + "host=",
  "android:" + "autoVerify=",
  oldDomain,
  oldPackage,
  "Brow" + "ser" + ".open",
  "Brow" + "ser" + "Plugin",
  "@capacitor/" + "brow" + "ser",
  "Custom" + "Tabs" + "Intent",
  "Intent." + "ACTION" + "_VIEW",
  "ACTION" + "_VIEW",
  "start" + "Activity(",
  "start" + "Activity" + "ForResult(",
  "Web" + "View",
  "Bridge" + "Activity",
  "load" + "Url(",
];

for (const token of forbiddenTokens) {
  if (manifest.includes(token)) fail(`Forbidden manifest token found: ${token}`);
  if (mainActivity.includes(token)) fail(`Forbidden MainActivity token found: ${token}`);
  if (pluginsJson.includes(token)) fail(`Forbidden plugin token found: ${token}`);
  if (configJson.includes(token)) fail(`Forbidden config token found: ${token}`);
}

console.log("[AetherX Local Final] Native package com.aetherx.localfinal locked: no plugins, no remote startup, no external route.");