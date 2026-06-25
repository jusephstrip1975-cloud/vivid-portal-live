#!/usr/bin/env node
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

const root = process.env.CAPACITOR_ROOT_DIR || process.cwd();
const androidRoot = join(root, "android");
const manifestPath = join(androidRoot, "app", "src", "main", "AndroidManifest.xml");
const configXmlPath = join(androidRoot, "app", "src", "main", "res", "xml", "config.xml");
const mainActivityPath = join(
  androidRoot,
  "app",
  "src",
  "main",
  "java",
  "com",
  "aetherx",
  "wallpapers",
  "MainActivity.java",
);
const stringsPath = join(androidRoot, "app", "src", "main", "res", "values", "strings.xml");
const pluginsJsonPath = join(androidRoot, "app", "src", "main", "assets", "capacitor.plugins.json");
const templateMainActivity = join(root, "android-template", "MainActivity.java");
const templateStrings = join(root, "android-template", "strings.xml");

function fail(message) {
  throw new Error(`[AetherX Android lock] ${message}`);
}

function read(path) {
  if (!existsSync(path)) fail(`Missing required file: ${path}`);
  return readFileSync(path, "utf8");
}

function copyTemplate(source, destination) {
  if (!existsSync(source)) fail(`Missing template: ${source}`);
  mkdirSync(dirname(destination), { recursive: true });
  copyFileSync(source, destination);
}

function count(text, regex) {
  return Array.from(text.matchAll(regex)).length;
}

function assertNoBrowserRoutes() {
  const manifest = read(manifestPath).replace(/<!--([\s\S]*?)-->/g, "");
  const mainActivity = read(mainActivityPath);
  const configXml = read(configXmlPath);
  const pluginsJson = existsSync(pluginsJsonPath) ? readFileSync(pluginsJsonPath, "utf8") : "";
  const packageJson = read(join(root, "package.json"));

  if (count(manifest, /<activity\b/g) !== 1) {
    fail("AndroidManifest.xml must declare exactly one Activity.");
  }
  if (!manifest.includes('android:name="com.aetherx.wallpapers.MainActivity"')) {
    fail("MainActivity must be declared with its fully-qualified class name.");
  }
  if (count(manifest, /android\.intent\.action\.MAIN/g) !== 1) {
    fail("AndroidManifest.xml must contain exactly one MAIN action.");
  }
  if (count(manifest, /android\.intent\.category\.LAUNCHER/g) !== 1) {
    fail("AndroidManifest.xml must contain exactly one LAUNCHER category.");
  }

  const forbidden = [
    "<activity-alias",
    "android.intent.action.VIEW",
    "android.intent.category.BROWSABLE",
    "android:scheme=",
    "android:host=",
    "android:autoVerify=",
    "<allow-intent",
    "<allow-navigation",
    "<access",
    "@capacitor/browser",
    "capacitor-browser",
    "BrowserPlugin",
    "Browser.open",
    "CustomTabsIntent",
    "loadUrl(\"http",
  ];

  for (const token of forbidden) {
    if (manifest.includes(token)) fail(`Forbidden manifest browser/deep-link token found: ${token}`);
    if (configXml.includes(token)) fail(`Forbidden Cordova config token found: ${token}`);
    if (pluginsJson.includes(token)) fail(`Forbidden Capacitor plugin token found: ${token}`);
    if (packageJson.includes(token)) fail(`Forbidden package dependency/script token found: ${token}`);
    if (mainActivity.includes(token)) fail(`Forbidden MainActivity token found: ${token}`);
  }
}

copyTemplate(templateMainActivity, mainActivityPath);
copyTemplate(templateStrings, stringsPath);
mkdirSync(dirname(configXmlPath), { recursive: true });
writeFileSync(
  configXmlPath,
  "<?xml version='1.0' encoding='utf-8'?>\n" +
    '<widget version="1.0.0" xmlns="http://www.w3.org/ns/widgets" xmlns:cdv="http://cordova.apache.org/ns/1.0">\n' +
    "</widget>\n",
  "utf8",
);

assertNoBrowserRoutes();
console.log("[AetherX Android lock] MainActivity local, manifest launcher-only, no Browser/deep-link routes.");