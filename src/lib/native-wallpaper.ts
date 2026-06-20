/**
 * Native wallpaper bridge.
 *
 * On web: no-op (resolves false) — used only for UI feedback.
 * On Android (Capacitor): downloads the asset to local storage and calls the
 *   `capacitor-wallpaper` community plugin. Tries multiple call shapes /
 *   argument keys because the home-screen vs lock-screen flags differ
 *   between plugin versions and Android OEMs (MIUI, OneUI, ColorOS often
 *   silently ignore FLAG_SYSTEM unless the image is a local file).
 * On iOS: Apple does NOT allow apps to programmatically change the system
 *   wallpaper. We save to Photos so the user can apply manually.
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

/**
 * Download a remote URL into the app's cache directory and return a local
 * file:// URI. Many Android wallpaper plugins refuse remote URLs.
 */
async function downloadToLocal(url: string): Promise<{ path: string; base64: string }> {
  const { Filesystem, Directory }: any = await import(
    /* @vite-ignore */ "@capacitor/filesystem" as string
  );
  const res = await fetch(url);
  const blob = await res.blob();
  const base64 = await blobToBase64(blob);
  const filename = `wp-${Date.now()}.jpg`;
  const written = await Filesystem.writeFile({
    path: filename,
    data: base64,
    directory: Directory.Cache,
  });
  return { path: written.uri as string, base64 };
}

/**
 * Try every known call signature for a given Android wallpaper flag.
 * Returns true on first success, false if all attempts threw.
 */
async function trySetAndroid(
  Wallpaper: any,
  localUri: string,
  base64: string,
  flag: 1 | 2,
): Promise<boolean> {
  const displayWord = flag === 1 ? "home" : "lock";
  const attempts: Array<Record<string, unknown>> = [
    { url: localUri, display: displayWord, which: displayWord, flag },
    { path: localUri, display: displayWord, flag },
    { url: localUri, flag },
    { base64, display: displayWord, flag },
    { url: localUri }, // last resort — plugin default (usually both/home)
  ];
  for (const args of attempts) {
    try {
      await Wallpaper.setImage(args);
      return true;
    } catch (e) {
      console.warn(`Wallpaper.setImage failed (${displayWord})`, args, e);
    }
  }
  return false;
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
      const mod: any = await import(/* @vite-ignore */ "capacitor-wallpaper" as string);
      const Wallpaper = mod.Wallpaper ?? mod.default;

      // Always materialize the image locally first — fixes silent failures
      // when the plugin only accepts file:// URIs.
      const { path: localUri, base64 } = await downloadToLocal(url);

      let homeOk = true;
      let lockOk = true;

      if (target === "home" || target === "both") {
        homeOk = await trySetAndroid(Wallpaper, localUri, base64, 1);
      }
      if (target === "lock" || target === "both") {
        lockOk = await trySetAndroid(Wallpaper, localUri, base64, 2);
      }

      if (!homeOk && !lockOk) {
        return { ok: false, reason: "android-plugin-rejected-all-signatures" };
      }
      if (target === "both" && (!homeOk || !lockOk)) {
        return {
          ok: true,
          reason: !homeOk ? "lock-only-home-failed" : "home-only-lock-failed",
        };
      }
      return { ok: true };
    }

    if (platform === "ios") {
      const { Filesystem, Directory }: any = await import(
        /* @vite-ignore */ "@capacitor/filesystem" as string
      );
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
