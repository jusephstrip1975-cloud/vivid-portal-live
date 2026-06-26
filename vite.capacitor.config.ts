import { resolve } from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";

const aetherxBuildVersion = process.env.AETHERX_BUILD_VERSION ?? new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);

export default defineConfig({
  appType: "spa",
  base: "./",
  publicDir: false,
  define: {
    __AETHERX_BUILD_VERSION__: JSON.stringify(aetherxBuildVersion),
  },
  plugins: [
    {
      name: "aetherx-build-version-html",
      transformIndexHtml(html) {
        return html.replaceAll("__AETHERX_BUILD_VERSION__", aetherxBuildVersion);
      },
    },
    react(),
    tailwindcss(),
    tsconfigPaths(),
  ],
  build: {
    outDir: "public/dist",
    emptyOutDir: true,
    sourcemap: false,
    rollupOptions: {
      input: {
        index: resolve(__dirname, "src/capacitor-index.html"),
      },
    },
  },
});