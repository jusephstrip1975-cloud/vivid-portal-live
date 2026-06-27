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
    wallpaperId?: string;
  }): Promise<{ path: string; bytes: number; galleryUri?: string }>;
  saveVideoFromUrlAndOpenPicker(options: {
    url: string;
    fileName?: string;
    wallpaperId?: string;
  }): Promise<{ path: string; bytes: number; openedPicker?: boolean; needsConfirmation?: boolean }>;
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
  checkStorage(): Promise<{ ok: boolean; freeMb: number; requiredMb: number; message: string }>;
  getStatus(): Promise<WallpaperDiagnostic>;
}

export interface WallpaperDiagnostic {
  finalPath?: string;
  fileExists?: boolean;
  fileSize?: number;
  canRead?: boolean;
  KEY_VIDEO_PATH?: string | null;
  lastDownloadUrl?: string | null;
  lastDownloadBytes?: number;
  lastError?: string | null;
  openPickerCalled?: boolean;
  pluginAvailable?: boolean;
  currentAction?: string | null;
  lastStep?: string | null;
  lastExceptionStacktrace?: string | null;
  parentDir?: string | null;
  parentExists?: boolean;
  parentWritable?: boolean;
  autoRecovered?: boolean;
  // Build info (native side)
  appVersion?: string;
  versionCode?: number;
  buildVersion?: string;
  buildTimestamp?: string;
  buildId?: string;
  packageName?: string;
  // Signature
  apkSignatureSha256?: string;
  signatureValid?: boolean;
  installSource?: string;
  // Service
  serviceRunning?: boolean;
  lastServiceError?: string | null;
  // Build info (JS side, from Vite env)
  jsBuildId?: string;
  jsBuildTimestamp?: string;
  jsBuildVersion?: string;
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

export async function getWallpaperDiagnostic(): Promise<WallpaperDiagnostic | null> {
  if (!(await isNative())) return null;
  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    if (Capacitor.getPlatform() !== "android") return null;
    const registeredWallpaper = registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
    const capPlugins = (Capacitor as unknown as { Plugins?: Record<string, unknown> }).Plugins ?? {};
    const pluginAvailable = Boolean(
      (capPlugins["AetherXLiveWallpaper"] as Partial<LiveWallpaperPlugin> | undefined)?.saveVideoFromUrl,
    );
    console.info("[AetherX] PLUGIN_AVAILABLE=" + pluginAvailable);
    const LiveWallpaper =
      (capPlugins["AetherXLiveWallpaper"] as LiveWallpaperPlugin | undefined) ?? registeredWallpaper;
    const status = await LiveWallpaper.getStatus();
    return { pluginAvailable, ...status };
  } catch (err) {
    console.warn("getWallpaperDiagnostic failed", err);
    return {
      pluginAvailable: false,
      lastError: err instanceof Error ? err.message : String(err),
      lastExceptionStacktrace: err instanceof Error ? err.stack ?? null : null,
      openPickerCalled: false,
    };
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
    try {
      const storage = await LiveWallpaper.checkStorage();
      console.info("[AetherX] FREE_SPACE_MB", storage.freeMb, "stage=pickDeviceVideo ok=", storage.ok);
      if (!storage.ok) {
        console.warn("[AetherX] DOWNLOAD_ABORTED_LOW_STORAGE pickDeviceVideo", storage);
        return { ok: false, reason: storage.message || "Espacio insuficiente para procesar wallpapers 3D" };
      }
    } catch (err) {
      console.warn("checkStorage failed (continuing)", err);
    }
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

/** Aplica el último vídeo guardado en almacenamiento externo privado persistente al destino indicado. */
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
        return { ok: false, reason: String(err) };
      }
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
        console.warn("applyBoth failed", err);
        return { ok: false, reason: String(err) };
      }
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
      console.warn("applyHome failed", err);
      return { ok: false, reason: String(err) };
    }
    return { ok: false, reason: "CURRENT_MP4_NOT_READY" };
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
  wallpaperId?: string,
): Promise<SaveResult> {
  if (!(await isNative())) {
    return { ok: false, reason: "web" };
  }

  try {
    const { Capacitor, registerPlugin } = await import("@capacitor/core");
    const platform = Capacitor.getPlatform();

    if (platform === "android") {
      registerPlugin<LiveWallpaperPlugin>("AetherXLiveWallpaper");
      const capacitorWithPlugins = Capacitor as typeof Capacitor & {
        Plugins?: Record<string, LiveWallpaperPlugin>;
      };
      const plugins = capacitorWithPlugins.Plugins;
      const LiveWallpaper = plugins?.AetherXLiveWallpaper;
      const hasNativeSaveMethod = typeof LiveWallpaper?.saveVideoFromUrl === "function";
      console.log("PLUGIN_DIRECT_AVAILABLE", Boolean(LiveWallpaper));
      console.log("PLUGIN_SAVE_METHOD_AVAILABLE", hasNativeSaveMethod);
      console.log("PLUGIN_NAMES", Object.keys(capacitorWithPlugins.Plugins ?? {}));
      if (!LiveWallpaper || !hasNativeSaveMethod) {
        console.error("PLUGIN_BRIDGE_MISSING", {
          hasPluginsObject: Boolean(plugins),
          pluginNames: Object.keys(plugins ?? {}),
        });
        return { ok: false, reason: "PLUGIN_BRIDGE_MISSING" };
      }
      try {
        const storage = await LiveWallpaper.checkStorage();
        console.info("[AetherX] FREE_SPACE_MB", storage.freeMb, "requiredMb", storage.requiredMb, "ok", storage.ok);
        if (!storage.ok) {
          console.warn("[AetherX] DOWNLOAD_ABORTED_LOW_STORAGE", storage);
          return { ok: false, reason: storage.message || "Espacio insuficiente para procesar wallpapers 3D" };
        }
      } catch (err) {
        console.warn("checkStorage failed (continuing)", err);
      }
      const resolvedUrl = resolveDownloadUrl(videoUrl);
      console.log("VIDEO_URL", resolvedUrl);
      if (!resolvedUrl || !resolvedUrl.trim()) {
        console.error("VIDEO_URL_INVALID", { wallpaperId, fileName, videoUrl, resolvedUrl });
        return { ok: false, reason: "missing-url" };
      }

      console.log("CALLING_PLUGIN");
      const saved = await LiveWallpaper.saveVideoFromUrl({
        url: resolvedUrl,
        fileName,
        wallpaperId,
      });
      console.log("PLUGIN_RESULT", saved);

      if (saved.path) {
        const previewUrl = Capacitor.convertFileSrc(saved.path);
        void runInternalSpeedProbe(previewUrl, saved.path);
      }
      const pickerResult =
        target === "lock"
          ? await LiveWallpaper.applyLock()
          : target === "both"
            ? await LiveWallpaper.applyBoth()
            : await LiveWallpaper.applyHome();
      console.log("PLUGIN_APPLY_RESULT", pickerResult);

      if (pickerResult.openedPicker || pickerResult.needsConfirmation || pickerResult.applied) {
        return { ok: true, reason: "android-live-picker-opened", needsPicker: true };
      }
      return { ok: false, reason: "CURRENT_MP4_NOT_READY" };
    }

    if (platform === "ios") {
      const { Filesystem, Directory } = await import("@capacitor/filesystem");
      const res = await fetch(videoUrl);
      const blob = await res.blob();
      const base64 = await blobToBase64(blob);
      await Filesystem.writeFile({
        path: fileName,
        data: base64,
        directory: Directory.Documents,
      });
      return { ok: true, reason: "ios-saved" };
    }

    return { ok: false, reason: "unsupported-platform" };
  } catch (err) {
    console.error("saveWallpaperToDevice failed", err);
    return { ok: false, reason: normalizeWallpaperError(err) };
  }
}

function normalizeWallpaperError(err: unknown): string {
  const message = String(err ?? "");
  if (message.includes("descarga fallida")) return "descarga fallida";
  if (message.includes("archivo no guardado")) return "archivo no guardado";
  if (message.includes("CURRENT_MP4_NOT_READY")) return "CURRENT_MP4_NOT_READY";
  return message;
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

async function runInternalSpeedProbe(previewUrl: string, savedPath: string) {
  if (typeof document === "undefined") return;
  const video = document.createElement("video");
  video.src = previewUrl;
  video.muted = true;
  video.playsInline = true;
  video.preload = "auto";
  video.loop = true;
  video.playbackRate = 1;
  video.style.cssText = "position:fixed;width:1px;height:1px;opacity:0;pointer-events:none;left:-10px;top:-10px";
  document.body.appendChild(video);
  try {
    await new Promise<void>((resolve) => {
      const done = () => resolve();
      video.addEventListener("loadedmetadata", done, { once: true });
      window.setTimeout(done, 1500);
    });
    await video.play().catch(() => undefined);
    const start = video.currentTime;
    await new Promise((resolve) => window.setTimeout(resolve, 1000));
    const delta = video.currentTime - start;
    console.info("AETHERX_SPEED_TEST_CONVERTED_APP", {
      savedPath,
      duration: Number.isFinite(video.duration) ? video.duration : null,
      playbackRate: video.playbackRate,
      elapsedVideoSeconds: delta,
      expectedElapsedSeconds: 1,
    });
  } catch (err) {
    console.warn("AETHERX_SPEED_TEST_CONVERTED_APP failed", err);
  } finally {
    video.pause();
    video.removeAttribute("src");
    video.load();
    video.remove();
  }
}
