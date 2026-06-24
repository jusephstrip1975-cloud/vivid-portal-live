#!/usr/bin/env node
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const distClient = join(root, "dist", "client");
const publicDist = join(root, "public", "dist");

function cleanPublicDist() {
  rmSync(publicDist, { recursive: true, force: true });
}

function findAsset(prefix, suffix) {
  const assetsDir = join(distClient, "assets");
  const match = readdirSync(assetsDir).find(
    (name) => name.startsWith(prefix) && name.endsWith(suffix),
  );
  if (!match) throw new Error(`Missing ${prefix}*${suffix} in dist/client/assets`);
  return `./assets/${match}`;
}

function writeLocalIndex() {
  const entry = findAsset("index-", ".js");
  const styles = findAsset("styles-", ".css");
  const html = `<!doctype html>
<html lang="es" class="dark">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover, maximum-scale=1" />
    <meta name="theme-color" content="#02040a" />
    <title>AetherX</title>
    <link rel="stylesheet" href="${styles}" />
    <script type="module" crossorigin src="${entry}"></script>
    <style>
      html, body { margin: 0; min-height: 100%; background: #02040a; color: #f8fafc; }
      body::before { content: "AetherX"; position: fixed; inset: 0; display: grid; place-items: center; font: 800 22px system-ui, sans-serif; letter-spacing: .18em; color: rgba(248,250,252,.72); background: radial-gradient(circle at 50% 10%, rgba(14,165,233,.22), transparent 42%), #02040a; z-index: -1; }
    </style>
  </head>
  <body>
    <script>console.info("[AetherX Android] Booting from packaged Capacitor local assets", location.href);</script>
  </body>
</html>
`;
  writeFileSync(join(distClient, "index.html"), html, "utf8");
}

function prepare() {
  if (!existsSync(distClient)) {
    throw new Error("dist/client does not exist. Run `bun run build` before Capacitor sync.");
  }
  writeLocalIndex();
  cleanPublicDist();
  mkdirSync(publicDist, { recursive: true });
  cpSync(distClient, publicDist, { recursive: true });
  console.log("Prepared local Android bundle at public/dist with local index.html");
}

const args = new Set(process.argv.slice(2));
if (args.has("--clean")) cleanPublicDist();
if (args.has("--prepare") || (!args.has("--clean") && args.size === 0)) prepare();