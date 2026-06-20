/**
 * Native wallpaper bridge — simplified.
 *
 * Único objetivo: descargar la IMAGEN del fondo (póster JPG) a la galería del
 * teléfono. Después el usuario la aplica desde Ajustes > Fondo de pantalla
 * del propio Android/iOS, eligiéndola del álbum AetherX. Es el flujo más
 * sencillo y 100% compatible con Play Store. Android no permite usar vídeos
 * MP4 como fondo desde esa pantalla, por eso descargamos la imagen estática.
 */

interface SaveResult {
  ok: boolean;
  reason?: string;
}

interface LiveWallpaperPlugin {
  saveImageFromUrl(options: {
    url: string;
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

export async function saveWallpaperToDevice(
  imageUrl: string,
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
        imageUrl,
        typeof window === "undefined" ? undefined : window.location.origin,
      ).toString();
      await LiveWallpaper.saveImageFromUrl({ url: absoluteUrl, fileName });
      return { ok: true, reason: "android-image-saved" };
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
      const res = await fetch(imageUrl);
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
