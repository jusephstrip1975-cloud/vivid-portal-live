export interface AetherXLiveWallpaperPlugin {
  saveVideoFromUrl(options: {
    url: string;
    fileName?: string;
  }): Promise<{ path: string; bytes: number; galleryUri?: string }>;
  saveVideo(options: {
    base64: string;
    fileName?: string;
  }): Promise<{ path: string; bytes: number; galleryUri?: string }>;
  applyHome(): Promise<{ applied: boolean; verified: boolean }>;
  openPicker(): Promise<{ opened: boolean }>;
  isAvailable(): Promise<{ available: boolean; hasVideo: boolean }>;
}
