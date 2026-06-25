import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.aetherx.localfinal",
  appName: "AetherX Local Final",
  webDir: "public/dist",
  backgroundColor: "#000000",
  android: {
    backgroundColor: "#000000",
    allowMixedContent: true,
    webContentsDebuggingEnabled: true,
  },
  ios: {
    backgroundColor: "#000000",
    contentInset: "always",
  },
};

export default config;
