/**
 * Native wallpaper bridge — simplified.
 *
 * Único objetivo: descargar el fondo 3D (vídeo + póster) a la galería del
 * teléfono. Después el usuario lo aplica desde Ajustes > Fondo de pantalla
 * del propio Android/iOS. Es el flujo más sencillo, compatible con Play
 * Store, y no requiere permisos especiales ni servicios de live wallpaper.
 */

interface SaveResult {
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
 * Descarga el vídeo 3D al almacenamiento del teléfono (galería en Android,
 * carpeta Cache en iOS). Devuelve { ok: true } cuando el archivo queda
 * guardado y disponible para que el usuario lo elija manualmente desde los
 * ajustes del sistema.
 */
export async function saveWallpaperToDevice(
  url: string,
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
      await saveVideoWithWebViewFallback(LiveWallpaper, url, fileName);
      return { ok: true, reason: "android-saved-to-gallery" };
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
      const res = await fetch(url);
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

async function saveVideoWithWebViewFallback(
  LiveWallpaper: LiveWallpaperPlugin,
  url: string,
  fileName: string,
): Promise<{ path: string; bytes: number; galleryUri?: string }> {
  const absoluteUrl = new URL(
    url,
    typeof window === "undefined" ? undefined : window.location.origin,
  ).toString();

  const shouldUseNativeDownload =
    /^https?:\/\//i.test(absoluteUrl) &&
    !/^https?:\/\/(localhost|127\.0\.0\.1|10\.0\.2\.2)([:/]|$)/i.test(absoluteUrl);

  if (shouldUseNativeDownload) {
    try {
      return await LiveWallpaper.saveVideoFromUrl({ url: absoluteUrl, fileName });
    } catch (err) {
      console.warn("Native download failed; retrying through WebView", err);
    }
  }

  const res = await fetch(url);
  if (!res.ok) throw new Error(`video-fetch-failed-${res.status}`);
  const blob = await res.blob();
  const base64 = await blobToBase64(blob);
  return LiveWallpaper.saveVideo({ base64, fileName });
}
