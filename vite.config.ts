// @lovable.dev/vite-tanstack-config already includes the following — do NOT add them manually
// or the app will break with duplicate plugins:
//   - tanstackStart, viteReact, tailwindcss, tsConfigPaths, nitro (build-only using cloudflare as a default target),
//     componentTagger (dev-only), VITE_* env injection, @ path alias, React/TanStack dedupe,
//     error logger plugins, and sandbox detection (port/host/strictPort).
// You can pass additional config via defineConfig({ vite: { ... }, etc... }) if needed.
import { defineConfig } from "@lovable.dev/vite-tanstack-config";

// AETHERX build identity: surfaced into the JS bundle via VITE_* env so the
// in-app diagnostic panel can prove the installed APK matches this bundle.
// The android workflow exports VITE_AETHERX_BUILD_{VERSION,TIMESTAMP,ID}.
// For local dev we synthesise something so import.meta.env always has a value.
const now = new Date().toISOString();
process.env.VITE_AETHERX_BUILD_VERSION ||= process.env.AETHERX_BUILD_VERSION || "dev";
process.env.VITE_AETHERX_BUILD_TIMESTAMP ||= process.env.AETHERX_BUILD_TIMESTAMP || now;
process.env.VITE_AETHERX_BUILD_ID ||=
  process.env.AETHERX_BUILD_ID ||
  `dev-${now.replace(/\D/g, "").slice(0, 12)}`;

export default defineConfig({
  tanstackStart: {
    // Redirect TanStack Start's bundled server entry to src/server.ts (our SSR error wrapper).
    // nitro/vite builds from this
    server: { entry: "server" },
  },
});
