#!/usr/bin/env node
// Modo TEST mínimo: genera una sola pantalla negra estática en public/dist.
// Sin Vite, sin React, sin TanStack, sin router, sin plugins, sin red.
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const publicDist = join(root, "public", "dist");

const HTML = `<!doctype html>
<html lang="es">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
    <title>AetherX</title>
    <style>
      html, body { margin:0; padding:0; width:100%; height:100%; background:#000; color:#fff;
        font-family: system-ui, -apple-system, "Segoe UI", sans-serif; }
      body { display:flex; align-items:center; justify-content:center; text-align:center;
        padding:24px; box-sizing:border-box; }
      h1 { font-size:32px; font-weight:800; letter-spacing:.04em; line-height:1.3; margin:0; }
      small { display:block; margin-top:16px; font-size:12px; opacity:.5; }
    </style>
  </head>
  <body>
    <div>
      <h1>AETHERX APP LOCAL FUNCIONANDO</h1>
      <small>APP LOCAL AETHERX VERSION FINAL</small>
    </div>
  </body>
</html>
`;

function cleanPublicDist() {
  rmSync(publicDist, { recursive: true, force: true });
}

function prepare() {
  cleanPublicDist();
  mkdirSync(publicDist, { recursive: true });
  writeFileSync(join(publicDist, "index.html"), HTML, "utf8");
  console.log("Prepared minimal local test screen at public/dist/index.html");
}

const args = new Set(process.argv.slice(2));
if (args.has("--clean")) cleanPublicDist();
if (args.has("--prepare") || (!args.has("--clean") && args.size === 0)) prepare();
