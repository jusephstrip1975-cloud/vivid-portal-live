export interface AetherXLiveWallpaperPlugin {
  saveVideoFromUrl(options: {
    url: string;
    fileName?: string;
  }): Promise<{ path: string; bytes: number }>;
  saveVideo(options: {
    base64: string;
    fileName?: string;
  }): Promise<{ path: string; bytes: number }>;
  openPicker(options?: { target?: "home" | "lock" | "both" }): Promise<{ opened: boolean }>;
  isAvailable(): Promise<{ available: boolean; hasVideo: boolean }>;
}
