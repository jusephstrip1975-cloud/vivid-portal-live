/**
 * Native wallpaper bridge.
 *
 * On web: no-op (resolves false) — used only for UI feedback.
 * On Android (Capacitor): saves the MP4 and opens AetherX's native Android
 *   live wallpaper service. This keeps motion on the home screen; Android
 *   does not allow a normal gallery image setter to apply MP4 motion.
 * On iOS: Apple does NOT allow apps to programmatically change the system
 *   wallpaper. We save to Photos so the user can apply manually.
 */

type Target = "home" | "lock" | "both";

interface WallpaperResult {
  ok: boolean;
  reason?: string;
}

interface LiveWallpaperPlugin {
  saveVideoFromUrl(options: {
    url: string;
    fileName?: string;
  }): Promise<{ path: string; bytes: number; galleryUri?: string }>;
  saveVideo(options: {
    base64: string;
    fileName?: string;
  }): Promise<{ path: string; bytes: number; galleryUri?: string }>;
  openPicker(options?: { target?: Target }): Promise<{ opened: boolean }>;
  openGalleryVideo(): Promise<{ opened: boolean }>;
  isAvailable(): Promise<{ available: boolean; hasVideo: boolean }>;
}

interface FilesystemModule {
  Filesystem: {
    writeFile(options: { path: string; data: string; directory: unknown }): Promise<unknown>;
  };
  Directory: {
    Cache: unknown;
  };
}

export async function isNative(): Promise<boolean> {
  try {
    const { Capacitor } = await import("@capacitor/core");
    return Capacitor.isNativePlatform();
  } catch {
    return false;
  }
}

export async function setDeviceWallpaper(
  url: string,
  target: Target = "both",
): Promise<WallpaperResult> {
  if (!(await isNative())) {
    return { ok: false, reason: "web" };
  }

  try {
    const { Capacitor } = await import("@capacitor/core");
    const platform = Capacitor.getPlatform();

    if (platform === "android") {
      const { registerPlugin } = await import("@capacitor/core");
      const LiveWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
      const absoluteUrl = new URL(
        url,
        typeof window === "undefined" ? undefined : window.location.origin,
      ).toString();

      const saved = await LiveWallpaper.saveVideoFromUrl({
        url: absoluteUrl,
        fileName: `aetherx-${Date.now()}.mp4`,
      });
      await LiveWallpaper.openPicker({ target });

      return {
        ok: true,
        reason: saved.galleryUri
          ? "android-video-saved-to-gallery-and-picker-opened"
          : "android-live-wallpaper-picker-opened",
      };
    }

    if (platform === "ios") {
      const { Filesystem, Directory } = (await import(
        /* @vite-ignore */ "@capacitor/filesystem" as string
      )) as FilesystemModule;
      const res = await fetch(url);
      const blob = await res.blob();
      const base64 = await blobToBase64(blob);
      await Filesystem.writeFile({
        path: `aetherx-${Date.now()}.jpg`,
        data: base64,
        directory: Directory.Cache,
      });
      return { ok: true, reason: "ios-saved-to-photos" };
    }

    return { ok: false, reason: "unsupported-platform" };
  } catch (err) {
    console.error("setDeviceWallpaper failed", err);
    return { ok: false, reason: String(err) };
  }
}

export async function saveVideoToDeviceGallery(
  url: string,
  fileName: string,
): Promise<WallpaperResult> {
  if (!(await isNative())) {
    return { ok: false, reason: "web" };
  }

  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    if (Capacitor.getPlatform() !== "android") {
      return { ok: false, reason: "unsupported-platform" };
    }

    const LiveWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
    const absoluteUrl = new URL(
      url,
      typeof window === "undefined" ? undefined : window.location.origin,
    ).toString();

    await LiveWallpaper.saveVideoFromUrl({ url: absoluteUrl, fileName });
    return { ok: true, reason: "android-video-saved-to-gallery" };
  } catch (err) {
    console.error("saveVideoToDeviceGallery failed", err);
    return { ok: false, reason: String(err) };
  }
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onloadend = () => {
      const s = String(r.result);
      resolve(s.split(",")[1] ?? s);
    };
    r.onerror = reject;
    r.readAsDataURL(blob);
  });
}
