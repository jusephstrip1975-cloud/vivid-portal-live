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
  applyHome(): Promise<{ applied: boolean; verified: boolean }>;
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
      const saved = await saveVideoWithWebViewFallback(
        LiveWallpaper,
        url,
        `aetherx-${Date.now()}.mp4`,
      );

      if (target === "home" || target === "both") {
        await LiveWallpaper.applyHome();
      }

      if (target === "lock" || target === "both") {
        await LiveWallpaper.openPicker({ target });
      }

      return {
        ok: true,
        reason: saved.galleryUri
          ? "android-home-applied-video-saved-and-picker-opened"
          : "android-home-applied",
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
    await saveVideoWithWebViewFallback(LiveWallpaper, url, fileName);
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

async function saveVideoWithWebViewFallback(
  LiveWallpaper: LiveWallpaperPlugin,
  url: string,
  fileName: string,
): Promise<{ path: string; bytes: number; galleryUri?: string }> {
  const absoluteUrl = new URL(
    url,
    typeof window === "undefined" ? undefined : window.location.origin,
  ).toString();

  const shouldUseNativeDownload = /^https?:\/\//i.test(absoluteUrl) &&
    !/^https?:\/\/(localhost|127\.0\.0\.1|10\.0\.2\.2)([:/]|$)/i.test(absoluteUrl);

  if (shouldUseNativeDownload) {
    try {
      return await LiveWallpaper.saveVideoFromUrl({ url: absoluteUrl, fileName });
    } catch (err) {
      console.warn("Native video download failed; retrying through WebView", err);
    }
  }

  const res = await fetch(url);
  if (!res.ok) throw new Error(`video-fetch-failed-${res.status}`);
  const blob = await res.blob();
  const base64 = await blobToBase64(blob);
  return LiveWallpaper.saveVideo({ base64, fileName });
}
