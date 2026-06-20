/** Native wallpaper bridge — saves the MP4 and opens Android's live wallpaper selector. */

interface SaveResult {
  ok: boolean;
  reason?: string;
}

interface LiveWallpaperPlugin {
  saveVideoFromUrl(options: {
    url: string;
    fileName?: string;
  }): Promise<{ path: string; bytes: number; galleryUri?: string }>;
  applyHome(): Promise<{ applied: boolean; verified?: boolean }>;
  openPicker(): Promise<{ opened: boolean }>;
}

export async function isNative(): Promise<boolean> {
  try {
    const { Capacitor } = await import("@capacitor/core");
    return Capacitor.isNativePlatform();
  } catch {
    return false;
  }
}

export async function saveWallpaperToDevice(
  videoUrl: string,
  fileName: string,
): Promise<SaveResult> {
  if (!(await isNative())) {
    return { ok: false, reason: "web" };
  }

  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    const platform = Capacitor.getPlatform();

    if (platform === "android") {
      const LiveWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
      const absoluteUrl = new URL(
        videoUrl,
        typeof window === "undefined" ? undefined : window.location.origin,
      ).toString();
      await LiveWallpaper.saveVideoFromUrl({ url: absoluteUrl, fileName });
      try {
        await LiveWallpaper.applyHome();
        return { ok: true, reason: "android-live-wallpaper-applied" };
      } catch {
        await LiveWallpaper.openPicker();
        return { ok: true, reason: "android-live-picker-opened" };
      }
    }

    if (platform === "ios") {
      const { Filesystem, Directory } = (await import(
        /* @vite-ignore */ "@capacitor/filesystem" as string
      )) as {
        Filesystem: {
          writeFile(o: { path: string; data: string; directory: unknown }): Promise<unknown>;
        };
        Directory: { Cache: unknown };
      };
      const res = await fetch(videoUrl);
      const blob = await res.blob();
      const base64 = await blobToBase64(blob);
      await Filesystem.writeFile({
        path: fileName,
        data: base64,
        directory: Directory.Cache,
      });
      return { ok: true, reason: "ios-saved" };
    }

    return { ok: false, reason: "unsupported-platform" };
  } catch (err) {
    console.error("saveWallpaperToDevice failed", err);
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
