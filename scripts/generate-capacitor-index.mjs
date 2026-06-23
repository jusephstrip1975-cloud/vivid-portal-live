#!/usr/bin/env node
// Genera un index.html mínimo en dist/client/ para que `cap sync` no falle.
// Este HTML solo se usa como fallback offline; en runtime el WebView carga
// la URL publicada definida en capacitor.config.ts -> server.url.

import { writeFileSync, mkdirSync, existsSync } from "node:fs";
import { join } from "node:path";

const OUT_DIR = "dist/client";
const PUBLISHED_URL = "https://vivid-portal-live.lovable.app";

if (!existsSync(OUT_DIR)) {
  mkdirSync(OUT_DIR, { recursive: true });
}

const html = `<!doctype html>
<html lang="es">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
    <title>AetherX</title>
    <meta http-equiv="refresh" content="0; url=${PUBLISHED_URL}/" />
    <style>
      html, body { margin:0; padding:0; background:#02040a; color:#fff;
        font-family: -apple-system, system-ui, sans-serif;
        height:100%; display:flex; align-items:center; justify-content:center; }
      .msg { text-align:center; opacity:.7; }
    </style>
  </head>
  <body>
    <div class="msg">
      <p>Cargando AetherX…</p>
      <p><a style="color:#7c5cff" href="${PUBLISHED_URL}/">Abrir en navegador</a></p>
    </div>
    <script>window.location.replace(${JSON.stringify(PUBLISHED_URL + "/")});</script>
  </body>
</html>
`;

writeFileSync(join(OUT_DIR, "index.html"), html, "utf8");
console.log("✓ Generated dist/client/index.html (Capacitor fallback shell)");
