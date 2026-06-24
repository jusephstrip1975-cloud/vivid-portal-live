import type { CapacitorConfig } from "@capacitor/cli";

/**
 * Capacitor config — APK local.
 *
 * Android arranca desde `public/dist/index.html`, empaquetado dentro del APK.
 * No hay configuración remota de servidor, no hay carga remota inicial y MainActivity no fuerza
 * ninguna URL web remota.
 */
const config: CapacitorConfig = {
  appId: "com.aetherx.wallpapers",
  appName: "AetherX",
  webDir: "public/dist",
  backgroundColor: "#02040a",
  android: {
    backgroundColor: "#02040a",
    allowMixedContent: false,
    webContentsDebuggingEnabled: true,
    captureInput: true,
  },
  ios: {
    backgroundColor: "#02040a",
    contentInset: "always",
  },
  plugins: {
    SplashScreen: {
      backgroundColor: "#02040a",
      showSpinner: false,
      launchAutoHide: true,
      launchShowDuration: 1500,
    },
  },
};

export default config;
