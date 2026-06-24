import { useEffect, useRef, useState } from "react";

interface Props {
  src: string;
  poster: string;
  alt: string;
  className?: string;
}

/**
 * Preview en tiempo real con bucle suave:
 * - El vídeo SOLO se carga y reproduce cuando entra en viewport (IntersectionObserver).
 * - Mientras tanto se muestra el póster en alta, sin descargar el mp4.
 * - Al estar listo, crossfade del póster al vídeo (sin "pop").
 * - Al salir de pantalla se pausa y se libera memoria de decoder.
 * Esto evita los tirones al haber decenas de tiles a la vez.
 */
export function LiveMedia({ src, poster, alt, className = "" }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [active, setActive] = useState(false);
  const [ready, setReady] = useState(false);

  // Observa visibilidad para activar/pausar el vídeo.
  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) setActive(e.isIntersecting);
      },
      { rootMargin: "200px 0px", threshold: 0.15 },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  // Reproduce / pausa según visibilidad y visibilidad de la pestaña.
  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    if (active && document.visibilityState === "visible") {
      v.play().catch(() => {});
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
      {/* Póster: siempre montado, sirve de placeholder y de fallback si autoplay falla */}
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
          src={src}
          poster={poster}
          aria-hidden
          autoPlay
          loop
          muted
          playsInline
          preload="auto"
          disablePictureInPicture
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
