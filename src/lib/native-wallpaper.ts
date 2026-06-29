/** Native wallpaper bridge — saves the MP4 and opens Android's live wallpaper selector. */

interface SaveResult {
  ok: boolean;
  reason?: string;
  needsPicker?: boolean;
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
  applyHome(): Promise<{ applied: boolean; verified?: boolean; openedPicker?: boolean; needsConfirmation?: boolean }>;
  applyLock(): Promise<{ applied: boolean; openedPicker?: boolean; needsConfirmation?: boolean }>;
  applyBoth(): Promise<{ applied: boolean; homeVerified: boolean; lockApplied: boolean; openedPicker?: boolean; needsConfirmation?: boolean }>;
  openPicker(): Promise<{ opened: boolean }>;
  pickVideoFromDevice(): Promise<{ path: string; bytes: number; sourceUri: string; galleryUri?: string }>;
  checkCompatibility(): Promise<CompatibilityResult>;
  getDiagnostics(): Promise<Record<string, unknown>>;
  recordFrontendStep(options: { step: string; error?: string }): Promise<{ ok: boolean }>;
}

/** Persist a step from the WebView into native SharedPreferences for diagnostics. */
export async function recordFrontendStep(step: string, error?: string): Promise<void> {
  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    if (!Capacitor.isNativePlatform() || Capacitor.getPlatform() !== "android") return;
    if (!Capacitor.isPluginAvailable("AetherXLiveWallpaper")) {
      console.warn("[AetherX] PLUGIN_NOT_FOUND");
      return;
    }
    const LiveWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
    await LiveWallpaper.recordFrontendStep({ step, error });
  } catch (err) {
    console.warn("recordFrontendStep failed", err);
  }
}

/** True if the AetherXLiveWallpaper native plugin is registered in this WebView. */
export async function isLiveWallpaperPluginAvailable(): Promise<boolean> {
  try {
    const { Capacitor } = await import("@capacitor/core");
    return Capacitor.isNativePlatform() && Capacitor.isPluginAvailable("AetherXLiveWallpaper");
  } catch {
    return false;
  }
}

export async function getSamsungDiagnostics(): Promise<string> {
  if (!(await isNative())) return "DIAGNOSTIC unavailable: not native";
  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    if (Capacitor.getPlatform() !== "android") return "DIAGNOSTIC unavailable: not android";
    const LiveWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
    const data = await LiveWallpaper.getDiagnostics();
    const lines = ["=== AETHERX SAMSUNG DIAGNOSTIC ==="];
    for (const [k, v] of Object.entries(data)) lines.push(`${k}: ${v}`);
    lines.push("=== ADB COMMAND ===");
    lines.push("adb logcat -c && adb logcat -v time | grep -iE \"AetherXLiveWP|WallpaperService|WallpaperManager|MediaPlayer|MediaCodec|Surface|WindowManager|AndroidRuntime|SecurityException|IllegalStateException|setWallpaper|bindWallpaper\"");
    return lines.join("\n");
  } catch (err) {
    return "DIAGNOSTIC error: " + String(err);
  }
}


export interface CompatibilityResult {
  canApplyHome: boolean;
  canApplyLock: boolean;
  liveWallpaperSupported: boolean;
  wallpaperSupported: boolean;
  setWallpaperAllowed: boolean;
  serviceRegistered: boolean;
  hasVideo: boolean;
  isSamsung: boolean;
  manufacturer: string;
  sdk: number;
  reason: string;
  message: string;
}

export async function checkWallpaperCompatibility(): Promise<CompatibilityResult | null> {
  if (!(await isNative())) return null;
  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    if (Capacitor.getPlatform() !== "android") return null;
    const LiveWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
    return await LiveWallpaper.checkCompatibility();
  } catch (err) {
    console.warn("checkWallpaperCompatibility failed", err);
    return null;
  }
}

export type WallpaperTarget = "home" | "lock" | "both";

export interface PickedDeviceVideo {
  path: string;
  bytes: number;
  sourceUri: string;
  galleryUri?: string;
  /** URL usable directamente en un <video> dentro del WebView. */
  previewUrl: string;
}

/** Abre el explorador y copia el vídeo elegido; NO lo aplica todavía. */
export async function pickDeviceVideo(): Promise<
  { ok: true; video: PickedDeviceVideo } | { ok: false; reason: string }
> {
  if (!(await isNative())) return { ok: false, reason: "web" };
  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    if (Capacitor.getPlatform() !== "android") {
      return { ok: false, reason: "unsupported-platform" };
    }
    const LiveWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
    const picked = await LiveWallpaper.pickVideoFromDevice();
    const previewUrl = Capacitor.convertFileSrc(picked.path);
    return { ok: true, video: { ...picked, previewUrl } };
  } catch (err) {
    const reason = String(err);
    if (reason.includes("pick-video-cancelled")) return { ok: false, reason: "cancelled" };
    console.error("pickDeviceVideo failed", err);
    return { ok: false, reason };
  }
}

/** Aplica el último vídeo guardado en filesDir al destino indicado. */
export async function applyPickedVideo(
  target: WallpaperTarget = "home",
): Promise<SaveResult> {
  if (!(await isNative())) return { ok: false, reason: "web" };
  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    if (Capacitor.getPlatform() !== "android") return { ok: false, reason: "unsupported-platform" };
    const LiveWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");

    if (target === "lock") {
      try {
        const res = await LiveWallpaper.applyLock();
        if (res.applied) return { ok: true, reason: "android-lock-applied" };
        if (res.openedPicker || res.needsConfirmation) {
          return { ok: true, reason: "android-live-picker-opened", needsPicker: true };
        }
      } catch (err) {
        console.warn("applyLock failed", err);
      }
      await LiveWallpaper.openPicker();
      return { ok: true, reason: "android-live-picker-opened", needsPicker: true };
    }

    if (target === "both") {
      try {
        const res = await LiveWallpaper.applyBoth();
        if (res.applied && res.homeVerified) {
          return { ok: true, reason: "android-both-applied" };
        }
        if (res.openedPicker || res.needsConfirmation) {
          return { ok: true, reason: "android-live-picker-opened", needsPicker: true };
        }
      } catch (err) {
        console.warn("applyBoth failed; opening picker", err);
      }
      await LiveWallpaper.openPicker();
      return { ok: true, reason: "android-live-picker-opened", needsPicker: true };
    }

    try {
      const applied = await LiveWallpaper.applyHome();
      if (applied.applied && applied.verified) {
        return { ok: true, reason: "android-home-applied" };
      }
      if (applied.openedPicker || applied.needsConfirmation) {
        return { ok: true, reason: "android-live-picker-opened", needsPicker: true };
      }
    } catch (err) {
      console.warn("applyHome failed; opening picker", err);
    }
    await LiveWallpaper.openPicker();
    return { ok: true, reason: "android-live-picker-opened", needsPicker: true };
  } catch (err) {
    console.error("applyPickedVideo failed", err);
    return { ok: false, reason: String(err) };
  }
}

/** Compatibilidad: pick + apply en un solo paso (sin preview). */
export async function pickAndApplyDeviceVideo(
  target: WallpaperTarget = "home",
): Promise<SaveResult> {
  const picked = await pickDeviceVideo();
  if (!picked.ok) return { ok: false, reason: picked.reason };
  return applyPickedVideo(target);
}

const PUBLISHED_ASSET_ORIGIN = "https://aetherx.org";
const PREVIEW_ASSET_ORIGIN = "https://id-preview--86067037-aec8-403d-b7be-5af9e39ce44c.lovable.app";

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
  target: WallpaperTarget = "home",
): Promise<SaveResult> {
  if (!(await isNative())) {
    return { ok: false, reason: "web" };
  }

  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    const platform = Capacitor.getPlatform();

    if (platform === "android") {
      const LiveWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
      await LiveWallpaper.saveVideoFromUrl({ url: resolveDownloadUrl(videoUrl), fileName });
      return applyPickedVideo(target);
    }

    if (platform === "ios") {
      const { Filesystem, Directory } = await import("@capacitor/filesystem");
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

export function resolveDownloadUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) return url;

  const browserOrigin = typeof window !== "undefined" ? window.location.origin : "";
  const capacitorBridge =
    typeof window !== "undefined"
      ? (window as Window & { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor
      : undefined;
  const isNativeWebView = capacitorBridge?.isNativePlatform?.() === true;
  const canServeAssets =
    !isNativeWebView &&
    /^https?:\/\//i.test(browserOrigin) &&
    !browserOrigin.includes("localhost") &&
    !browserOrigin.includes("127.0.0.1");
  const isLocalWebPreview =
    !isNativeWebView &&
    typeof window !== "undefined" &&
    (browserOrigin.includes("localhost") || browserOrigin.includes("127.0.0.1"));
  const origin = canServeAssets
    ? browserOrigin
    : isLocalWebPreview
      ? PREVIEW_ASSET_ORIGIN
      : PUBLISHED_ASSET_ORIGIN;

  if (url.startsWith("/")) return `${origin}${url}`;
  return `${origin}/${url}`;
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
