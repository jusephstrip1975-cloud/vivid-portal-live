import { useEffect, useRef, useState } from "react";
import { resolveDownloadUrl } from "@/lib/native-wallpaper";

interface Props {
  src: string;
  poster: string;
  alt: string;
  className?: string;
  /**
   * Fuerza la reproducción del vídeo aunque estemos en WebView nativo.
   * Se usa en la pantalla de detalle; en grids se mantiene apagado por
   * rendimiento (pósteres estáticos).
   */
  forceVideo?: boolean;
}

const isNativeWebView = () => {
  if (typeof window === "undefined") return false;
  const bridge = (window as Window & { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor;
  return bridge?.isNativePlatform?.() === true;
};

// Detecta equipos con poca RAM/CPU (Android de gama baja) para desactivar previews.
const isLowEndDevice = () => {
  if (typeof navigator === "undefined") return false;
  const nav = navigator as Navigator & { deviceMemory?: number; connection?: { saveData?: boolean; effectiveType?: string } };
  if (nav.deviceMemory && nav.deviceMemory <= 4) return true;
  if (nav.hardwareConcurrency && nav.hardwareConcurrency <= 4) return true;
  const c = nav.connection;
  if (c?.saveData) return true;
  if (c?.effectiveType && /2g|3g/.test(c.effectiveType)) return true;
  return false;
};

// --- Concurrencia global de vídeos ---
// Solo permitimos un puñado de <video> reproduciéndose a la vez para no
// saturar el decodificador de Android (esa era la causa principal del lag).
const MAX_CONCURRENT = 3;
const activeVideos = new Set<HTMLVideoElement>();
const waiters: Array<() => void> = [];

function acquireSlot(video: HTMLVideoElement): boolean {
  if (activeVideos.has(video)) return true;
  if (activeVideos.size < MAX_CONCURRENT) {
    activeVideos.add(video);
    return true;
  }
  return false;
}

function releaseSlot(video: HTMLVideoElement) {
  if (activeVideos.delete(video)) {
    const next = waiters.shift();
    if (next) next();
  }
}

function waitForSlot(cb: () => void) {
  waiters.push(cb);
}

export function LiveMedia({ src, poster, alt, className = "", forceVideo = false }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [active, setActive] = useState(false);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);
  const videoSrc = resolveDownloadUrl(src);

  // En WebView nativo (Android app) o dispositivos de gama baja mostramos
  // solo el póster estático en los grids. La pantalla de detalle usa
  // `forceVideo` para reproducir el vídeo cuando el usuario elige un fondo.
  const [disableVideo] = useState(() => {
    if (forceVideo) return false;
    if (typeof window === "undefined") return true;
    return isNativeWebView() || isLowEndDevice();
  });

  useEffect(() => {
    if (disableVideo) return;
    const el = wrapRef.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) setActive(e.isIntersecting);
      },
      { rootMargin: "150px 0px", threshold: 0.25 },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [disableVideo]);

  useEffect(() => {
    if (disableVideo) return;
    const v = videoRef.current;
    if (!v) return;
    v.muted = true;
    v.defaultMuted = true;

    let released = false;
    const release = () => {
      if (released) return;
      released = true;
      try { v.pause(); } catch { /* noop */ }
      releaseSlot(v);
    };

    const tryPlay = () => {
      if (failed) return;
      if (!acquireSlot(v)) {
        waitForSlot(() => {
          if (!released && active && document.visibilityState === "visible") tryPlay();
        });
        return;
      }
      v.play().catch(() => {
        const retry = () => {
          v.play().catch(() => {});
          window.removeEventListener("touchstart", retry);
          window.removeEventListener("click", retry);
        };
        window.addEventListener("touchstart", retry, { once: true, passive: true });
        window.addEventListener("click", retry, { once: true });
      });
    };

    if (active && document.visibilityState === "visible" && !failed) {
      tryPlay();
    } else {
      release();
    }

    return release;
  }, [active, failed, disableVideo]);

  useEffect(() => {
    setReady(false);
    setFailed(false);
  }, [src, poster]);

  useEffect(() => {
    if (disableVideo) return;
    const onVis = () => {
      const v = videoRef.current;
      if (!v) return;
      if (document.visibilityState === "visible" && active) {
        if (!failed) v.play().catch(() => {});
      } else {
        v.pause();
      }
    };
    document.addEventListener("visibilitychange", onVis);
    return () => document.removeEventListener("visibilitychange", onVis);
  }, [active, failed, disableVideo]);

  return (
    <div ref={wrapRef} className={`relative overflow-hidden ${className}`}>
      <img
        src={poster}
        alt={alt}
        loading="lazy"
        decoding="async"
        className="absolute inset-0 size-full object-cover"
      />
      {!disableVideo && active && !failed && (
        <video
          key={videoSrc}
          ref={videoRef}
          src={videoSrc}
          poster={poster}
          aria-hidden
          autoPlay
          loop
          muted
          playsInline
          {...({ "webkit-playsinline": "true", "x5-playsinline": "true" } as Record<string, string>)}
          preload="metadata"
          disablePictureInPicture
          controls={false}
          onCanPlay={() => setReady(true)}
          onLoadedData={() => setReady(true)}
          onPlaying={() => setReady(true)}
          onError={() => {
            setFailed(true);
            setReady(false);
            console.warn("[AetherX] VIDEO_PREVIEW_ERROR", videoSrc);
          }}
          className={`absolute inset-0 size-full object-cover transition-opacity duration-500 ${
            ready ? "opacity-100" : "opacity-0"
          }`}
        />
      )}
    </div>
  );
}
