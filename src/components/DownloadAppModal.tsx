import { useEffect, useState } from "react";
import { Apple, Smartphone, X, Download, Share, Plus } from "lucide-react";

const STORAGE_KEY = "aetherx-download-prompt-dismissed";
// APK servido desde el propio dominio. Sube el archivo firmado a
// public/downloads/aetherx-latest.apk y el botón lo descargará directo.
const ANDROID_APK_URL = "/downloads/aetherx-latest.apk";

function detectOS(): "android" | "ios" | "other" {
  if (typeof navigator === "undefined") return "other";
  const ua = navigator.userAgent || "";
  if (/android/i.test(ua)) return "android";
  if (/iPad|iPhone|iPod/i.test(ua)) return "ios";
  // iPadOS 13+ reports as Mac; detect touch
  if (/Macintosh/.test(ua) && typeof navigator.maxTouchPoints === "number" && navigator.maxTouchPoints > 1) {
    return "ios";
  }
  return "other";
}

function isCapacitor(): boolean {
  if (typeof window === "undefined") return false;
  const w = window as Window & { Capacitor?: { isNativePlatform?: () => boolean } };
  return w.Capacitor?.isNativePlatform?.() === true;
}

function isStandalone(): boolean {
  if (typeof window === "undefined") return false;
  const nav = navigator as Navigator & { standalone?: boolean };
  return (
    nav.standalone === true ||
    window.matchMedia?.("(display-mode: standalone)")?.matches === true
  );
}

export function DownloadAppModal() {
  const [open, setOpen] = useState(false);
  const [showIosGuide, setShowIosGuide] = useState(false);

  useEffect(() => {
    if (isCapacitor()) return;
    if (isStandalone()) return;
    try {
      if (sessionStorage.getItem(STORAGE_KEY) === "1") return;
    } catch {}
    const t = setTimeout(() => setOpen(true), 800);
    return () => clearTimeout(t);
  }, []);

  function close() {
    setOpen(false);
    setShowIosGuide(false);
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

        {!showIosGuide ? (
          <>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-electric-blue/15 text-electric-blue">
              <Download className="h-6 w-6" />
            </div>
            <h2 className="mt-4 text-lg font-bold text-display text-ice-white">
              Descargar AETHERX
            </h2>
            <p className="mt-2 text-sm text-white/60">
              Elige tu sistema para instalar la app y aplicar fondos animados en tu pantalla.
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
                onClick={() => setShowIosGuide(true)}
                className={`flex w-full items-center justify-center gap-2.5 rounded-full px-5 py-3.5 text-sm font-bold uppercase tracking-[0.15em] transition ${
                  os === "ios"
                    ? "bg-electric-blue text-space-black hover:bg-ocean-cyan"
                    : "border border-white/15 text-white hover:bg-white/5"
                }`}
              >
                <Apple className="h-4 w-4" />
                iPhone / iPad
              </button>
            </div>

            <button
              onClick={close}
              className="mt-5 text-[10px] font-bold uppercase tracking-[0.25em] text-white/40 hover:text-white/70 transition"
            >
              Continuar en el navegador
            </button>
          </>
        ) : (
          <>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-electric-blue/15 text-electric-blue">
              <Apple className="h-6 w-6" />
            </div>
            <h2 className="mt-4 text-lg font-bold text-display text-ice-white">
              Instalar en iPhone / iPad
            </h2>
            <p className="mt-2 text-sm text-white/60">
              Añade AETHERX a tu pantalla de inicio para abrirlo como una app nativa desde Safari.
            </p>

            <ol className="mt-5 space-y-3 text-left">
              <li className="flex items-start gap-3 rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-electric-blue/15 text-[11px] font-black text-electric-blue">
                  1
                </span>
                <span className="text-xs text-white/75">
                  Abre esta web en <b className="text-white">Safari</b> (no Chrome ni la app de Instagram).
                </span>
              </li>
              <li className="flex items-start gap-3 rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-electric-blue/15 text-electric-blue">
                  <Share className="h-3.5 w-3.5" />
                </span>
                <span className="text-xs text-white/75">
                  Pulsa el botón <b className="text-white">Compartir</b> en la barra inferior.
                </span>
              </li>
              <li className="flex items-start gap-3 rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-electric-blue/15 text-electric-blue">
                  <Plus className="h-3.5 w-3.5" />
                </span>
                <span className="text-xs text-white/75">
                  Elige <b className="text-white">"Añadir a pantalla de inicio"</b> y confirma.
                </span>
              </li>
            </ol>

            <p className="mt-4 text-[10px] uppercase tracking-[0.2em] text-white/40">
              La app en la App Store llegará próximamente
            </p>

            <div className="mt-5 flex gap-2">
              <button
                onClick={() => setShowIosGuide(false)}
                className="flex-1 rounded-full border border-white/15 px-4 py-2.5 text-[11px] font-bold uppercase tracking-[0.18em] text-white/70 hover:bg-white/5 transition"
              >
                Atrás
              </button>
              <button
                onClick={close}
                className="flex-1 rounded-full bg-ice-white px-4 py-2.5 text-[11px] font-bold uppercase tracking-[0.18em] text-space-black hover:bg-electric-blue transition"
              >
                Entendido
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

