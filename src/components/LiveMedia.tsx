import { useEffect, useRef, useState } from "react";
import { resolveDownloadUrl } from "@/lib/native-wallpaper";

interface Props {
  src: string;
  poster: string;
  alt: string;
  className?: string;
}

const isCoarsePointer =
  typeof window !== "undefined" &&
  window.matchMedia?.("(pointer: coarse)").matches === true;

/**
 * Preview en tiempo real con bucle suave.
 * - Solo carga/reproduce el vídeo cuando entra en viewport.
 * - En móvil (pointer coarse) usa preload="none" para no saturar el decoder.
 * - Atributos playsInline + webkit-playsinline para iOS.
 * - Si autoplay es bloqueado, reintenta tras el primer toque del usuario.
 */
export function LiveMedia({ src, poster, alt, className = "" }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [active, setActive] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) setActive(e.isIntersecting);
      },
      { rootMargin: "200px 0px", threshold: 0.1 },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;

    const tryPlay = () => {
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

    if (active && document.visibilityState === "visible") {
      tryPlay();
    } else {
      v.pause();
    }
  }, [active]);

  useEffect(() => {
    const onVis = () => {
      const v = videoRef.current;
      if (!v) return;
      if (document.visibilityState === "visible" && active) {
        v.play().catch(() => {});
      } else {
        v.pause();
      }
    };
    document.addEventListener("visibilitychange", onVis);
    return () => document.removeEventListener("visibilitychange", onVis);
  }, [active]);

  return (
    <div ref={wrapRef} className={`relative overflow-hidden ${className}`}>
      <img
        src={poster}
        alt={alt}
        loading="lazy"
        decoding="async"
        className="absolute inset-0 size-full object-cover"
      />
      {active && (
        <video
          ref={videoRef}
          src={resolveDownloadUrl(src)}
          poster={poster}
          aria-hidden
          autoPlay
          loop
          muted
          playsInline
          {...({ "webkit-playsinline": "true", "x5-playsinline": "true" } as Record<string, string>)}
          preload={isCoarsePointer ? "none" : "auto"}
          disablePictureInPicture
          controls={false}
          onCanPlay={() => setReady(true)}
          onPlaying={() => setReady(true)}
          className={`absolute inset-0 size-full object-cover transition-opacity duration-500 ${
            ready ? "opacity-100" : "opacity-0"
          }`}
        />
      )}
    </div>
  );
}
