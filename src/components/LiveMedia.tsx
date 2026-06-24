import { useEffect, useRef, useState } from "react";

interface Props {
  src: string;
  poster: string;
  alt: string;
  className?: string;
}

/**
 * Preview en tiempo real con bucle suave + compatibilidad móvil:
 * - El vídeo solo se monta cuando entra en viewport.
 * - Limitador global: máx N vídeos reproduciéndose a la vez (en móvil los
 *   decoders de hardware fallan si hay >3-4 vídeos simultáneos).
 * - Atributos playsInline + webkit-playsinline para iOS.
 * - Tras el primer tap del usuario reintenta autoplay (desbloquea iOS Low Power).
 */

// ---- Limitador global de vídeos concurrentes ----
const isMobile =
  typeof navigator !== "undefined" &&
  (/Mobi|Android|iPhone|iPad|iPod/i.test(navigator.userAgent) ||
    (typeof window !== "undefined" && window.matchMedia?.("(pointer: coarse)").matches));

const MAX_CONCURRENT = isMobile ? 3 : 8;
const playing = new Set<HTMLVideoElement>();
const waiters: Array<() => void> = [];

function acquireSlot(v: HTMLVideoElement): boolean {
  if (playing.has(v)) return true;
  if (playing.size < MAX_CONCURRENT) {
    playing.add(v);
    return true;
  }
  return false;
}

function releaseSlot(v: HTMLVideoElement) {
  if (playing.delete(v)) {
    const next = waiters.shift();
    if (next) next();
  }
}

function waitSlot(v: HTMLVideoElement): Promise<void> {
  return new Promise((resolve) => {
    const tryAcquire = () => {
      if (acquireSlot(v)) resolve();
      else waiters.push(tryAcquire);
    };
    tryAcquire();
  });
}

// Tap-to-unlock global (iOS): tras la primera interacción, dispara play en
// todos los vídeos visibles.
let userInteracted = false;
const interactionListeners: Array<() => void> = [];
function onFirstInteraction() {
  if (userInteracted) return;
  userInteracted = true;
  for (const cb of interactionListeners.splice(0)) cb();
}
if (typeof window !== "undefined") {
  const opts = { once: true, passive: true } as AddEventListenerOptions;
  window.addEventListener("touchstart", onFirstInteraction, opts);
  window.addEventListener("click", onFirstInteraction, opts);
  window.addEventListener("keydown", onFirstInteraction, opts);
}

export function LiveMedia({ src, poster, alt, className = "" }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [active, setActive] = useState(false);
  const [ready, setReady] = useState(false);

  // Visibilidad en viewport
  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) setActive(e.isIntersecting);
      },
      { rootMargin: "150px 0px", threshold: 0.1 },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  // Gestiona play/pause con limitador global
  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    let cancelled = false;

    const tryPlay = async () => {
      if (cancelled) return;
      if (document.visibilityState !== "visible") return;
      await waitSlot(v);
      if (cancelled) {
        releaseSlot(v);
        return;
      }
      try {
        await v.play();
      } catch {
        // Autoplay bloqueado: reintenta tras primera interacción
        if (!userInteracted) {
          interactionListeners.push(() => {
            v.play().catch(() => {});
          });
        }
      }
    };

    if (active) {
      tryPlay();
    } else {
      v.pause();
      releaseSlot(v);
      setReady(false);
    }

    return () => {
      cancelled = true;
      v.pause();
      releaseSlot(v);
    };
  }, [active]);

  // Pausa al ocultar pestaña
  useEffect(() => {
    const onVis = () => {
      const v = videoRef.current;
      if (!v) return;
      if (document.visibilityState === "visible" && active) {
        waitSlot(v).then(() => v.play().catch(() => {}));
      } else {
        v.pause();
        releaseSlot(v);
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
          src={src}
          poster={poster}
          aria-hidden
          autoPlay
          loop
          muted
          defaultMuted
          playsInline
          // @ts-expect-error iOS-specific attribute
          webkit-playsinline="true"
          x5-playsinline="true"
          preload="metadata"
          disablePictureInPicture
          disableRemotePlayback
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
