import type { CapacitorConfig } from "@capacitor/cli";

/**
 * Capacitor config — la app carga la web en vivo desde https://aetherx.org
 * DENTRO del WebView nativo (no abre Chrome externo).
 *
 * Reglas clave:
 *  - NO usamos server.url para evitar que Capacitor intente abrir la web
 *    antes de que MainActivity instale el WebViewClient protegido.
 *  - server.androidScheme = "https" para evitar mixed-content y permitir cookies seguras.
 *  - allowNavigation incluye el dominio raíz, www y subdominios + previews de Lovable
 *    para que cualquier redirección quede dentro del WebView en lugar de abrirse
 *    en Chrome externo.
 *  - cleartext: false (todo es HTTPS).
 *  - allowMixedContent: false (no mezclamos http en https).
 *
 * MainActivity carga https://aetherx.org explícitamente dentro del WebView
 * y bloquea cualquier esquema externo para evitar Chrome fuera de la app.
 */
const config: CapacitorConfig = {
  appId: "com.aetherx.wallpapers",
  appName: "AetherX",
  webDir: "public",
  backgroundColor: "#02040a",
  server: {
    cleartext: false,
    androidScheme: "https",
    allowNavigation: [
      "aetherx.org",
      "*.aetherx.org",
      "www.aetherx.org",
      "vivid-portal-live.lovable.app",
      "*.lovable.app",
      "*.lovableproject.com",
      "*.supabase.co",
      "*.supabase.in",
    ],
  },
  android: {
    backgroundColor: "#02040a",
    allowMixedContent: false,
    webContentsDebuggingEnabled: true,
    // Mantiene el WebView gestionado por Capacitor — no lanza Chrome externo.
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
