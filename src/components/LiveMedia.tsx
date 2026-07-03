import { useEffect, useRef, useState } from "react";
import { resolveDownloadUrl } from "@/lib/native-wallpaper";

interface Props {
  src: string;
  poster: string;
  alt: string;
  className?: string;
  /**
   * Si es false, renderiza SOLO el póster (JPG). Sin vídeo, sin autoplay.
   * Úsalo para grids con muchas tarjetas — evita bloquear el hilo principal
   * en Android al decodificar decenas de MP4s a la vez.
   * Default: false (rendimiento). Ponlo a true solo en pantallas de detalle.
   */
  preview?: boolean;
}

const isNativeWebView = () => {
  if (typeof window === "undefined") return false;
  const bridge = (window as Window & { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor;
  return bridge?.isNativePlatform?.() === true;
};

/**
 * Preview en tiempo real con bucle suave.
 * - Solo carga/reproduce el vídeo cuando entra en viewport Y preview=true.
 * - En grids (preview=false) muestra únicamente el póster para no bloquear el WebView.
 */
export function LiveMedia({ src, poster, alt, className = "", preview = false }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [active, setActive] = useState(false);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);
  const videoSrc = resolveDownloadUrl(src);

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const margin = isNativeWebView() ? 700 : 300;
    const markIfVisible = () => {
      const rect = el.getBoundingClientRect();
      const height = window.innerHeight || document.documentElement.clientHeight;
      setActive(rect.top < height + margin && rect.bottom > -margin);
    };
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) setActive(e.isIntersecting);
      },
      { rootMargin: `${margin}px 0px`, threshold: 0.01 },
    );
    io.observe(el);
    markIfVisible();
    const raf = window.requestAnimationFrame(markIfVisible);
    const timer = window.setTimeout(markIfVisible, 300);
    return () => {
      io.disconnect();
      window.cancelAnimationFrame(raf);
      window.clearTimeout(timer);
    };
  }, []);

  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    v.muted = true;
    v.defaultMuted = true;
    v.setAttribute("muted", "");
    v.setAttribute("playsinline", "");
    v.setAttribute("webkit-playsinline", "true");

    const tryPlay = () => {
      if (failed) return;
      v.load();
      v.play().catch(() => {
        // Autoplay bloqueado: reintenta tras primera interacción del usuario.
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
      v.pause();
    }
  }, [active, failed]);

  useEffect(() => {
    setReady(false);
    setFailed(false);
  }, [src, poster]);

  useEffect(() => {
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
  }, [active, failed]);

  return (
    <div ref={wrapRef} className={`relative overflow-hidden ${className}`}>
      <img
        src={poster}
        alt={alt}
        loading="lazy"
        decoding="async"
        className="absolute inset-0 size-full object-cover"
      />
      {active && !failed && (
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
