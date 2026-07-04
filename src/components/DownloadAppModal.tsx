import { useEffect, useState } from "react";
import { X, Download, Smartphone, CheckCircle2, Zap, Share as ShareIcon, Mail } from "lucide-react";
import logoAsset from "@/assets/aetherx-logo-v2.png";
import { supabase } from "@/integrations/supabase/client";

const STORAGE_KEY = "aetherx-download-prompt-dismissed";
const REGISTERED_KEY = "aetherx-tester-registered";
const GITHUB_OWNER_REPO = "jusephstrip1975-cloud/vivid-portal-live";
const APK_VERSION = "3.2.7";
const ANDROID_APK_URL = `https://github.com/${GITHUB_OWNER_REPO}/releases/latest/download/aetherx-latest.apk#v=${APK_VERSION}`;
const TESTERS_GOAL = 15;

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

function isCapacitor(): boolean {
  if (typeof window === "undefined") return false;
  const w = window as Window & {
    Capacitor?: { isNativePlatform?: () => boolean; getPlatform?: () => string };
  };
  const ua = navigator.userAgent.toLowerCase();
  return (
    w.Capacitor?.isNativePlatform?.() === true ||
    w.Capacitor?.getPlatform?.() === "android" ||
    (ua.includes("android") && ua.includes("; wv"))
  );
}

function isStandalone(): boolean {
  if (typeof window === "undefined") return false;
  const nav = navigator as Navigator & { standalone?: boolean };
  return (
    nav.standalone === true ||
    window.matchMedia?.("(display-mode: standalone)")?.matches === true
  );
}

function detectPlatform(): "android" | "ios" | "desktop" {
  if (typeof navigator === "undefined") return "desktop";
  const ua = navigator.userAgent.toLowerCase();
  if (/iphone|ipad|ipod/.test(ua)) return "ios";
  if (/android/.test(ua)) return "android";
  return "desktop";
}

const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

export function DownloadAppModal() {
  const [open, setOpen] = useState(false);
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [installed, setInstalled] = useState(false);
  const [showApkGuide, setShowApkGuide] = useState(false);
  const [showIosGuide, setShowIosGuide] = useState(false);
  const [platform, setPlatform] = useState<"android" | "ios" | "desktop">("desktop");

  // Registro de tester
  const [registered, setRegistered] = useState(false);
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [testerCount, setTesterCount] = useState<number | null>(null);

  useEffect(() => {
    setPlatform(detectPlatform());
    try {
      if (localStorage.getItem(REGISTERED_KEY) === "1") setRegistered(true);
    } catch {}
  }, []);

  // Cargar contador de testers
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    (async () => {
      const { data } = await supabase.rpc("get_tester_count");
      if (!cancelled && typeof data === "number") setTesterCount(data);
    })();
    return () => {
      cancelled = true;
    };
  }, [open, registered]);

  // Capturar el prompt nativo de PWA
  useEffect(() => {
    function onBeforeInstall(e: Event) {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    }
    function onInstalled() {
      setInstalled(true);
      setDeferredPrompt(null);
    }
    window.addEventListener("beforeinstallprompt", onBeforeInstall);
    window.addEventListener("appinstalled", onInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onBeforeInstall);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, []);

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setErrorMsg(null);
    const clean = email.trim().toLowerCase();
    if (!EMAIL_RE.test(clean)) {
      setErrorMsg("Introduce un correo válido");
      return;
    }
    setSubmitting(true);
    const { error } = await supabase.from("tester_emails").insert({ email: clean });
    setSubmitting(false);
    if (error) {
      // Duplicado = correo ya registrado, no es un error real para el usuario
      if (error.code === "23505") {
        try { localStorage.setItem(REGISTERED_KEY, "1"); } catch {}
        setRegistered(true);
        return;
      }
      setErrorMsg("No se pudo registrar. Inténtalo de nuevo.");
      return;
    }
    try { localStorage.setItem(REGISTERED_KEY, "1"); } catch {}
    setRegistered(true);
  }

  async function handleInstallPwa() {
    if (deferredPrompt) {
      await deferredPrompt.prompt();
      const choice = await deferredPrompt.userChoice;
      if (choice.outcome === "accepted") setInstalled(true);
      setDeferredPrompt(null);
      return;
    }
    if (platform === "ios") {
      setShowIosGuide(true);
    } else {
      alert(
        "Abre el menú del navegador (⋮) y pulsa \"Instalar app\" o \"Añadir a pantalla de inicio\".",
      );
    }
  }

  function handleDownloadApk() {
    const a = document.createElement("a");
    a.href = ANDROID_APK_URL;
    a.download = "aetherx-latest.apk";
    a.rel = "noopener";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setShowApkGuide(true);
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

  useEffect(() => {
    if (isCapacitor()) return;
    if (!open) return;
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    const prevOverflow = document.body.style.overflow;
    const prevPaddingRight = document.body.style.paddingRight;
    document.body.style.overflow = "hidden";
    if (scrollbarWidth > 0) document.body.style.paddingRight = `${scrollbarWidth}px`;
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

  const pwaAvailable = !!deferredPrompt || platform === "ios";
  const count = testerCount ?? 0;
  const progress = Math.min(100, Math.round((count / TESTERS_GOAL) * 100));

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/85 backdrop-blur-md px-4 py-6 overflow-y-auto"
      onClick={close}
    >
      <div
        className="relative w-full max-w-sm rounded-3xl p-6 text-center shadow-[0_20px_80px_rgba(212,175,55,0.25)] my-auto"
        style={{
          background: "linear-gradient(160deg, #0a1a3a 0%, #050d24 55%, #0a1a3a 100%)",
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

        <div className="mx-auto h-16 w-16 rounded-2xl overflow-hidden ring-1 ring-[#d4af37]/50 shadow-[0_8px_30px_rgba(212,175,55,0.35)]">
          <img src={logoAsset} alt="AETHERX" width={64} height={64} className="h-full w-full object-cover" />
        </div>

        <h2 className="mt-4 text-lg font-bold tracking-[0.2em] text-white">AETHERX</h2>
        <div
          className="mx-auto mt-1 h-px w-16"
          style={{ background: "linear-gradient(90deg, transparent, #d4af37, transparent)" }}
        />

        {!registered ? (
          <RegisterStep
            email={email}
            setEmail={setEmail}
            onSubmit={handleRegister}
            submitting={submitting}
            errorMsg={errorMsg}
            count={count}
            progress={progress}
          />
        ) : installed ? (
          <>
            <p className="mt-4 text-sm text-white/80 leading-relaxed">
              ✓ App instalada. Búscala en tu pantalla de inicio.
            </p>
            <button
              onClick={close}
              className="mt-5 text-[11px] font-bold uppercase tracking-[0.25em] text-white/60 hover:text-white transition"
            >
              Cerrar
            </button>
          </>
        ) : showIosGuide ? (
          <IosInstallGuide onClose={close} />
        ) : showApkGuide ? (
          <ApkGuide onClose={close} />
        ) : (
          <>
            <div className="mt-3 flex items-center justify-center gap-1.5 rounded-full bg-emerald-500/10 border border-emerald-400/30 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.15em] text-emerald-300">
              <CheckCircle2 className="h-3 w-3" />
              Registrado — ya puedes descargar
            </div>

            <p className="mt-3 text-xs text-white/70 leading-relaxed">
              Elige cómo instalar AETHERX en tu móvil.
            </p>

            <button
              onClick={handleInstallPwa}
              disabled={!pwaAvailable && platform === "desktop"}
              className="mt-5 flex w-full items-center justify-center gap-2 rounded-full px-5 py-3.5 text-sm font-bold uppercase tracking-[0.15em] transition active:scale-[0.98] disabled:opacity-50"
              style={{
                background: "linear-gradient(135deg, #f4d160 0%, #d4af37 50%, #b8892b 100%)",
                color: "#050d24",
                boxShadow: "0 8px 24px rgba(212,175,55,0.35)",
              }}
            >
              <Zap className="h-4 w-4" />
              Instalar app (1 toque)
            </button>
            <div className="mt-1.5 text-[9px] uppercase tracking-[0.2em] text-[#f4d160]/80">
              Recomendado · sin permisos · icono al instante
            </div>

            <div className="my-4 flex items-center gap-2 text-[9px] uppercase tracking-[0.2em] text-white/30">
              <div className="h-px flex-1 bg-white/10" />
              o versión con wallpaper 3D nativo
              <div className="h-px flex-1 bg-white/10" />
            </div>

            <button
              onClick={handleDownloadApk}
              className="flex w-full items-center justify-center gap-2 rounded-full border border-white/20 bg-white/5 px-5 py-3 text-xs font-bold uppercase tracking-[0.15em] text-white transition hover:bg-white/10 active:scale-[0.98]"
            >
              <Download className="h-3.5 w-3.5" />
              Descargar APK Android
            </button>
            <div className="mt-1.5 flex items-center justify-center gap-1.5 text-[9px] uppercase tracking-[0.2em] text-white/40">
              <Smartphone className="h-2.5 w-2.5" />
              APK v{APK_VERSION} · Android 8.0+
            </div>

            <button
              onClick={close}
              className="mt-5 text-[10px] font-bold uppercase tracking-[0.25em] text-white/40 hover:text-white/70 transition"
            >
              Continuar en el navegador
            </button>
          </>
        )}
      </div>
    </div>
  );
}

function RegisterStep({
  email,
  setEmail,
  onSubmit,
  submitting,
  errorMsg,
  count,
  progress,
}: {
  email: string;
  setEmail: (v: string) => void;
  onSubmit: (e: React.FormEvent) => void;
  submitting: boolean;
  errorMsg: string | null;
  count: number;
  progress: number;
}) {
  return (
    <>
      <p className="mt-3 text-xs text-white/75 leading-relaxed">
        Déjanos tu correo para descargar AETHERX.
      </p>



      <form onSubmit={onSubmit} className="mt-4 text-left">
        <label className="mb-1.5 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.2em] text-white/70">
          <Mail className="h-3 w-3" />
          Tu correo
        </label>
        <input
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="tu@correo.com"
          className="w-full rounded-full border border-white/20 bg-white/5 px-4 py-2.5 text-sm text-white placeholder-white/30 outline-none transition focus:border-[#d4af37]/60 focus:bg-white/10"
        />
        {errorMsg && (
          <div className="mt-2 text-[11px] text-red-300">{errorMsg}</div>
        )}
        <button
          type="submit"
          disabled={submitting}
          className="mt-4 flex w-full items-center justify-center gap-2 rounded-full px-5 py-3.5 text-sm font-bold uppercase tracking-[0.15em] transition active:scale-[0.98] disabled:opacity-60"
          style={{
            background: "linear-gradient(135deg, #f4d160 0%, #d4af37 50%, #b8892b 100%)",
            color: "#050d24",
            boxShadow: "0 8px 24px rgba(212,175,55,0.35)",
          }}
        >
          {submitting ? "Validando..." : "Validar y descargar"}
        </button>
        <p className="mt-2 text-center text-[9px] uppercase tracking-[0.15em] text-white/40">
          Solo el correo · sin contraseñas · sin spam
        </p>
      </form>
    </>
  );
}

function IosInstallGuide({ onClose }: { onClose: () => void }) {
  return (
    <div className="mt-4 text-left">
      <div className="mb-2 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.2em] text-[#f4d160]">
        <ShareIcon className="h-3 w-3" />
        Instalar en iPhone / iPad
      </div>
      <ol className="ml-4 list-decimal space-y-2 text-[12px] text-white/80 leading-relaxed">
        <li>Pulsa el botón <b>Compartir</b> abajo en Safari (icono cuadrado con flecha).</li>
        <li>Desplázate y pulsa <b>"Añadir a pantalla de inicio"</b>.</li>
        <li>Confirma con <b>Añadir</b>. El icono AETHERX aparecerá en tu pantalla.</li>
      </ol>
      <button
        onClick={onClose}
        className="mt-5 w-full rounded-full bg-white/10 py-2.5 text-[11px] font-bold uppercase tracking-[0.2em] text-white hover:bg-white/15 transition"
      >
        Entendido
      </button>
    </div>
  );
}

function ApkGuide({ onClose }: { onClose: () => void }) {
  return (
    <div className="mt-4 text-left">
      <div className="mb-3 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.2em] text-[#f4d160]">
        <CheckCircle2 className="h-3 w-3" />
        Descarga iniciada — 2 pasos rápidos
      </div>
      <ol className="ml-4 list-decimal space-y-2.5 text-[12px] text-white/85 leading-relaxed">
        <li>
          Si Chrome dice <b>"posible archivo dañino"</b>, pulsa <b>Descargar de todos modos</b>.
          <div className="mt-0.5 text-[10px] text-white/50">Es normal — el APK no está en Play Store, pero es seguro.</div>
        </li>
        <li>
          Abre la notificación de descarga y pulsa <b>Instalar</b>.
          <div className="mt-0.5 text-[10px] text-white/50">Si pide "permitir esta fuente", acéptalo (una sola vez).</div>
        </li>
      </ol>
      <div className="mt-4 rounded-xl border border-[#d4af37]/25 bg-[#d4af37]/8 p-3 text-[11px] text-white/75 leading-relaxed">
        ✨ Al terminar verás el icono <b>AETHERX</b> en tu pantalla de inicio.
      </div>
      <button
        onClick={onClose}
        className="mt-4 w-full rounded-full bg-white/10 py-2.5 text-[11px] font-bold uppercase tracking-[0.2em] text-white hover:bg-white/15 transition"
      >
        Cerrar
      </button>
    </div>
  );
}
