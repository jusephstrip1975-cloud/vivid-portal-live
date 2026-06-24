import { resolve } from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";

export default defineConfig({
  appType: "spa",
  publicDir: false,
  plugins: [react(), tailwindcss(), tsconfigPaths()],
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