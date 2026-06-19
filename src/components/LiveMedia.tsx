import { useEffect, useRef } from "react";

interface Props {
  src: string;
  poster: string;
  alt: string;
  className?: string;
}

/**
 * Renders a real looping video as the wallpaper preview.
 * Falls back to the poster image while the video is loading
 * or if the browser blocks autoplay.
 */
export function LiveMedia({ src, poster, alt, className = "" }: Props) {
  const ref = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const v = ref.current;
    if (!v) return;
    // Some mobile browsers need an explicit play() after mount.
    const tryPlay = () => v.play().catch(() => {});
    tryPlay();
    const onVisible = () => {
      if (document.visibilityState === "visible") tryPlay();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [src]);

  return (
    <video
      ref={ref}
      src={src}
      poster={poster}
      aria-label={alt}
      autoPlay
      loop
      muted
      playsInline
      preload="auto"
      disablePictureInPicture
      className={className}
    />
  );
}
