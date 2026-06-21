export interface AetherXLiveWallpaperPlugin {
  saveVideoFromUrl(options: {
    url: string;
    fileName?: string;
  }): Promise<{ path: string; bytes: number; galleryUri?: string }>;
  saveVideo(options: {
    base64: string;
    fileName?: string;
  }): Promise<{ path: string; bytes: number; galleryUri?: string }>;
  pickVideoFromDevice(): Promise<{ path: string; bytes: number; sourceUri: string; galleryUri?: string }>;
  applyHome(): Promise<{ applied: boolean; verified: boolean }>;
  applyLock(): Promise<{ applied: boolean }>;
  applyBoth(): Promise<{ applied: boolean; homeVerified: boolean; lockApplied: boolean }>;
  openPicker(): Promise<{ opened: boolean }>;
  isAvailable(): Promise<{ available: boolean; hasVideo: boolean }>;
}
export {};
