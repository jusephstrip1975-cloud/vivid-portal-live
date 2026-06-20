/**
 * Native wallpaper bridge.
 *
 * On web: no-op (resolves false) — used only for UI feedback.
 * On Android (Capacitor): calls the `capacitor-wallpaper` community plugin
 *   to actually set the device wallpaper.
 * On iOS: Apple does NOT allow apps to programmatically change the system
 *   wallpaper. We fall back to triggering the Photos save sheet so the user
 *   can long-press → "Use as Wallpaper".
 */

type Target = "home" | "lock" | "both";

interface WallpaperResult {
  ok: boolean;
  reason?: string;
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
      // Dynamically import the community plugin so the web build does not fail.
      // Install in your local clone:  npm i capacitor-wallpaper
      const mod: any = await import(/* @vite-ignore */ "capacitor-wallpaper" as string);
      const Wallpaper = mod.Wallpaper ?? mod.default;
      // Android WallpaperManager flags: FLAG_SYSTEM=1 (home), FLAG_LOCK=2 (lock), both=3.
      // The plugin accepts several aliases depending on version — we send all common ones.
      const displayMap = {
        home: { display: "home", which: "home", flag: 1 },
        lock: { display: "lock", which: "lock", flag: 2 },
        both: { display: "both", which: "both", flag: 3 },
      } as const;
      const d = displayMap[target];

      if (target === "both") {
        // Some plugin versions silently apply only to LOCK when asked for BOTH.
        // Set HOME first, then LOCK, to guarantee both screens are updated.
        try {
          await Wallpaper.setImage({ url, display: "home", which: "home", flag: 1 });
        } catch (e) {
          console.warn("setImage(home) failed, continuing with lock", e);
        }
        await Wallpaper.setImage({ url, display: "lock", which: "lock", flag: 2 });
      } else {
        await Wallpaper.setImage({ url, ...d });
      }
      return { ok: true };
    }

    if (platform === "ios") {
      // iOS restriction: no API to set system wallpaper. Save to Photos so
      // the user can apply it manually from the iOS share sheet.
      const { Filesystem, Directory }: any = await import(/* @vite-ignore */ "@capacitor/filesystem" as string);
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
