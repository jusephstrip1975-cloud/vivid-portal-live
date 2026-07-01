import { useEffect, useState } from "react";
import { Apple, Smartphone, X, Download } from "lucide-react";

const STORAGE_KEY = "aetherx-download-prompt-dismissed";
const ANDROID_APK_URL =
  "https://github.com/lovable-labs/aetherx-live-wallpaper/releases/latest";

function detectOS(): "android" | "ios" | "other" {
  if (typeof navigator === "undefined") return "other";
  const ua = navigator.userAgent || "";
  if (/android/i.test(ua)) return "android";
  if (/iPad|iPhone|iPod/i.test(ua)) return "ios";
  return "other";
}

function isCapacitor(): boolean {
  if (typeof window === "undefined") return false;
  const w = window as Window & { Capacitor?: { isNativePlatform?: () => boolean } };
  return w.Capacitor?.isNativePlatform?.() === true;
}

export function DownloadAppModal() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (isCapacitor()) return;
    try {
      if (sessionStorage.getItem(STORAGE_KEY) === "1") return;
    } catch {}
    const t = setTimeout(() => setOpen(true), 800);
    return () => clearTimeout(t);
  }, []);

  function close() {
    setOpen(false);
    try {
      sessionStorage.setItem(STORAGE_KEY, "1");
    } catch {}
  }

  if (!open) return null;
  const os = detectOS();

  return (
    <div
      className="fixed inset-0 z-[100] flex items-end sm:items-center justify-center bg-black/80 backdrop-blur-sm px-4 pb-6 sm:pb-4"
      onClick={close}
    >
      <div
        className="glass-card relative w-full max-w-sm rounded-3xl p-6 text-center border border-white/10"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={close}
          className="absolute right-4 top-4 rounded-full p-1.5 text-white/60 hover:bg-white/10 hover:text-white transition"
          aria-label="Cerrar"
        >
          <X className="h-4 w-4" />
        </button>

        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-electric-blue/15 text-electric-blue">
          <Download className="h-6 w-6" />
        </div>
        <h2 className="mt-4 text-lg font-bold text-display text-ice-white">
          Descargar AETHERX
        </h2>
        <p className="mt-2 text-sm text-white/60">
          Elige tu sistema para instalar la app y aplicar fondos animados en tu
          pantalla.
        </p>

        <div className="mt-6 space-y-2.5">
          <a
            href={ANDROID_APK_URL}
            target="_blank"
            rel="noopener noreferrer"
            className={`flex items-center justify-center gap-2.5 rounded-full px-5 py-3.5 text-sm font-bold uppercase tracking-[0.15em] transition ${
              os === "android"
                ? "bg-electric-blue text-space-black hover:bg-ocean-cyan"
                : "bg-ice-white text-space-black hover:bg-electric-blue"
            }`}
          >
            <Smartphone className="h-4 w-4" />
            Android (APK)
          </a>
          <button
            onClick={() => {
              alert(
                "AETHERX para iOS estará disponible próximamente en la App Store. Mientras tanto, puedes añadir la web a tu pantalla de inicio desde Safari → Compartir → Añadir a pantalla de inicio.",
              );
            }}
            className={`flex w-full items-center justify-center gap-2.5 rounded-full px-5 py-3.5 text-sm font-bold uppercase tracking-[0.15em] transition ${
              os === "ios"
                ? "bg-electric-blue text-space-black hover:bg-ocean-cyan"
                : "border border-white/15 text-white hover:bg-white/5"
            }`}
          >
            <Apple className="h-4 w-4" />
            iOS (próximamente)
          </button>
        </div>

        <button
          onClick={close}
          className="mt-5 text-[10px] font-bold uppercase tracking-[0.25em] text-white/40 hover:text-white/70 transition"
        >
          Continuar en el navegador
        </button>
      </div>
    </div>
  );
}
