import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.aetherx.wallpapers",
  appName: "AetherX",
  webDir: "dist/client/",
  backgroundColor: "#02040a",
  android: {
    backgroundColor: "#02040a",
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
    },
  },
};

export default config;
