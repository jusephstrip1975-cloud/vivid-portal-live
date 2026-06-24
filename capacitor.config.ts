import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.aetherx.wallpapers",
  appName: "AetherX",
  webDir: "public",
  backgroundColor: "#02040a",
  server: {
    url: "https://vivid-portal-live.lovable.app",
    cleartext: false,
    androidScheme: "https",
    allowNavigation: [
      "*.lovable.app",
      "vivid-portal-live.lovable.app",
      "*.supabase.co",
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
      backgroundColor: "#02040a",
      showSpinner: false,
      launchAutoHide: true,
      launchShowDuration: 1500,
    },
  },
};

export default config;
