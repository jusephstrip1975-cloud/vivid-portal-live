import { useEffect, useState } from "react";
import { X, Download, Smartphone, CheckCircle2 } from "lucide-react";
import logoAsset from "@/assets/aetherx-logo-v2.png";

const STORAGE_KEY = "aetherx-download-prompt-dismissed";
const GITHUB_OWNER_REPO = "jusephstrip1975-cloud/vivid-portal-live";
const APK_VERSION = "3.2.6";
// GitHub sirve el APK con Content-Disposition: attachment. Sin query params
// para no romper la descarga directa; el cache-buster va como fragment (#v=...).
const ANDROID_APK_URL = `https://github.com/${GITHUB_OWNER_REPO}/releases/latest/download/aetherx-latest.apk#v=${APK_VERSION}`;

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
  const [downloaded, setDownloaded] = useState(false);

  function handleDownloadClick() {
    // Fuerza descarga directa sin cambiar de pestaña. GitHub sirve
    // Content-Disposition: attachment, así que el navegador guarda el APK.
    const a = document.createElement("a");
    a.href = ANDROID_APK_URL;
    a.download = "aetherx-latest.apk";
    a.rel = "noopener";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setDownloaded(true);
  }

  useEffect(() => {
    if (isCapacitor()) return;
    if (isStandalone()) return;
    try {
      if (sessionStorage.getItem(STORAGE_KEY) === "1") return;
    } catch {}
    const t = setTimeout(() => setOpen(true), 800);
    return () => clearTimeout(t);
  }, []);

  // Lock body scroll and compensate for scrollbar width to prevent layout shift
  useEffect(() => {
    if (!open) return;
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    const prevOverflow = document.body.style.overflow;
    const prevPaddingRight = document.body.style.paddingRight;
    document.body.style.overflow = "hidden";
    if (scrollbarWidth > 0) {
      document.body.style.paddingRight = `${scrollbarWidth}px`;
    }
    return () => {
      document.body.style.overflow = prevOverflow;
      document.body.style.paddingRight = prevPaddingRight;
    };
  }, [open]);

  function close() {
    setOpen(false);
    try {
      sessionStorage.setItem(STORAGE_KEY, "1");
    } catch {}
  }

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/85 backdrop-blur-md px-4"
      onClick={close}
    >
      <div
        className="relative w-full max-w-sm rounded-3xl p-7 text-center shadow-[0_20px_80px_rgba(212,175,55,0.25)]"
        style={{
          background:
            "linear-gradient(160deg, #0a1a3a 0%, #050d24 55%, #0a1a3a 100%)",
          border: "1px solid rgba(212,175,55,0.35)",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={close}
          className="absolute right-4 top-4 rounded-full p-1.5 text-white/60 hover:bg-white/10 hover:text-white transition"
          aria-label="Cerrar"
        >
          <X className="h-4 w-4" />
        </button>

        <div className="mx-auto h-20 w-20 rounded-2xl overflow-hidden ring-1 ring-[#d4af37]/50 shadow-[0_8px_30px_rgba(212,175,55,0.35)]">
          <img
            src={logoAsset}
            alt="AETHERX"
            width={80}
            height={80}
            className="h-full w-full object-cover"
          />
        </div>

        <h2
          className="mt-5 text-xl font-bold tracking-[0.2em] text-white"
          style={{ fontFamily: "inherit" }}
        >
          AETHERX
        </h2>
        <div
          className="mx-auto mt-1 h-px w-16"
          style={{
            background:
              "linear-gradient(90deg, transparent, #d4af37, transparent)",
          }}
        />
        <p className="mt-4 text-sm text-white/70 leading-relaxed">
          Descarga la app oficial para aplicar fondos animados 3D en tu pantalla de inicio y bloqueo.
        </p>

        <button
          onClick={handleDownloadClick}
          className="mt-6 flex w-full items-center justify-center gap-2.5 rounded-full px-6 py-3.5 text-sm font-bold uppercase tracking-[0.2em] transition active:scale-[0.98]"
          style={{
            background:
              "linear-gradient(135deg, #f4d160 0%, #d4af37 50%, #b8892b 100%)",
            color: "#050d24",
            boxShadow: "0 8px 24px rgba(212,175,55,0.35)",
          }}
        >
          <Download className="h-4 w-4" />
          {downloaded ? "Descargando..." : "Descargar Android"}
        </button>

        <div className="mt-4 flex items-center justify-center gap-2 text-[10px] uppercase tracking-[0.2em] text-white/50">
          <Smartphone className="h-3 w-3" />
          APK v{APK_VERSION} · Android 8.0+
        </div>

        {downloaded && (
          <div
            className="mt-5 rounded-2xl p-4 text-left text-[11px] leading-relaxed text-white/80"
            style={{
              background: "rgba(212,175,55,0.08)",
              border: "1px solid rgba(212,175,55,0.25)",
            }}
          >
            <div className="mb-2 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.2em] text-[#f4d160]">
              <CheckCircle2 className="h-3 w-3" />
              Para ver el icono en tu móvil
            </div>
            <ol className="ml-4 list-decimal space-y-1.5">
              <li>Abre la notificación de descarga o el gestor de archivos.</li>
              <li>Pulsa <b>aetherx-latest.apk</b> e <b>Instalar</b> (si pide "permitir esta fuente", acéptalo).</li>
              <li>Si Chrome avisa "posible archivo dañino", pulsa <b>Descargar de todos modos</b> — es normal en APKs fuera de Play Store.</li>
              <li>Tras instalar, el icono <b>AETHERX</b> aparecerá en tu pantalla de inicio.</li>
            </ol>
          </div>
        )}

        <button
          onClick={close}
          className="mt-4 text-[10px] font-bold uppercase tracking-[0.25em] text-white/40 hover:text-white/70 transition"
        >
          {downloaded ? "Cerrar" : "Continuar en el navegador"}
        </button>
      </div>
    </div>
  );
}
