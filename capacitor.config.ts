import type { CapacitorConfig } from "@capacitor/cli";

// AetherX usa TanStack Start (SSR). El build NO produce un index.html estático,
// por lo que Capacitor no puede servir el frontend desde dist/client/.
// Solución: el WebView carga la URL publicada (Cloudflare Worker SSR).
// Los plugins nativos (Live Wallpaper, Filesystem) siguen funcionando porque
// Capacitor inyecta el bridge en cualquier origen permitido.

const PUBLISHED_URL = "https://vivid-portal-live.lovable.app";

const config: CapacitorConfig = {
  appId: "com.aetherx.wallpapers",
  appName: "AetherX",
  // webDir solo se usa como fallback offline. Mantenemos dist/client por compatibilidad
  // con `cap sync`, que exige que el directorio exista y contenga al menos un index.html.
  webDir: "dist/client",
  backgroundColor: "#02040a",
  server: {
    // Carga la app desde la URL publicada (tiene SSR completo).
    url: PUBLISHED_URL,
    cleartext: false,
    androidScheme: "https",
    allowNavigation: [
      "vivid-portal-live.lovable.app",
      "*.lovable.app",
      "*.lovable.dev",
    ],
  },
  android: {
    backgroundColor: "#02040a",
    allowMixedContent: false,
    webContentsDebuggingEnabled: true,
  },
  ios: {
    backgroundColor: "#02040a",
    contentInset: "always",
  },
  plugins: {
    SplashScreen: {
      // Sin logo nativo: arranque 100% negro y el logo "AetherX · La disciplina
      // lo es todo" aparece dentro de la app a 1s (componente BootIntro).
      launchShowDuration: 0,
      launchAutoHide: true,
      backgroundColor: "#000000",
      showSpinner: false,
      androidSplashResourceName: "splash_black",
    },
  },
};

export default config;
