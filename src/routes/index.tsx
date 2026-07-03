import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowUpRight, Check, FolderOpen, Lock, Sparkles, X } from "lucide-react";
import { useEffect, useState } from "react";
import { CATEGORIES, WALLPAPERS } from "@/lib/wallpapers";
import { WallpaperTile } from "@/components/WallpaperTile";
import { LiveMedia } from "@/components/LiveMedia";
import aetherxLogo from "@/assets/aetherx-logo-v2.png";
import {
  applyPickedVideo,
  getSamsungDiagnostics,
  getWallpaperFitMode,
  isNative,
  pickDeviceVideo,
  setWallpaperFitMode,
  type FitMode,
  type PickedDeviceVideo,
} from "@/lib/native-wallpaper";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "AetherX — Fondos animados premium en 4K" },
      {
        name: "description",
        content:
          "Descubre fondos de pantalla animados ultra realistas: universo, océano, cyberpunk y más. Cinematic Reality en tu móvil.",
      },
      { property: "og:title", content: "AetherX — Cinematic Reality" },
      {
        property: "og:description",
        content:
          "Tu pantalla se convierte en una ventana viva. Fondos 4K en movimiento, sonidos ambientales y parallax 3D.",
      },
    ],
  }),
  component: HomePage,
});

function HomePage() {
  const hero = WALLPAPERS[0];

  const [pickState, setPickState] = useState<"idle" | "loading" | "applying">("idle");
  const [pickToast, setPickToast] = useState<string | null>(null);
  const [preview, setPreview] = useState<PickedDeviceVideo | null>(null);
  const [diag, setDiag] = useState<string | null>(null);
  const [diagFields, setDiagFields] = useState<Record<string, string> | null>(null);
  const [fitMode, setFitModeState] = useState<FitMode>("cover");
  const [visibleWallpapers, setVisibleWallpapers] = useState(18);

  useEffect(() => {
    getWallpaperFitMode().then(setFitModeState).catch(() => {});
  }, []);

  const oddWallpapers = WALLPAPERS.filter((_, i) => i % 2 === 1).slice(0, Math.ceil(visibleWallpapers / 2));
  const evenWallpapers = WALLPAPERS.filter((_, i) => i % 2 === 0).slice(0, Math.floor(visibleWallpapers / 2));
  const hasMoreWallpapers = visibleWallpapers < WALLPAPERS.length;

  async function handleFitModeChange(mode: FitMode) {
    setFitModeState(mode);
    await setWallpaperFitMode(mode);
    showToast(`Modo: ${mode === "cover" ? "Recortar (Cover)" : mode === "stretch" ? "Estirar" : "Ajustar (Contain)"}`);
  }

  function showToast(msg: string, ms = 2600) {
    setPickToast(msg);
    setTimeout(() => setPickToast(null), ms);
  }

  function parseDiag(text: string): Record<string, string> {
    const out: Record<string, string> = {};
    for (const line of text.split("\n")) {
      const idx = line.indexOf(":");
      if (idx <= 0) continue;
      const key = line.slice(0, idx).trim();
      const val = line.slice(idx + 1).trim();
      if (key && /^[A-Z_]+$/.test(key)) out[key] = val;
    }
    return out;
  }

  async function handleCopyDiagnostic() {
    const text = await getSamsungDiagnostics();
    setDiag(text);
    setDiagFields(parseDiag(text));
    try {
      await navigator.clipboard.writeText(text);
      showToast("✓ Diagnóstico copiado al portapapeles");
    } catch {
      showToast("Diagnóstico generado (copia manual abajo)");
    }
  }

  async function handlePickDeviceVideo() {
    if (!(await isNative())) {
      showToast("Solo disponible en la app de Android");
      return;
    }
    setPickState("loading");
    const result = await pickDeviceVideo();
    setPickState("idle");
    if (!result.ok) {
      if (result.reason !== "cancelled") {
        showToast("No se pudo abrir el explorador de archivos");
      }
      return;
    }
    setPreview(result.video);
  }

  async function handleConfirmApply() {
    setPickState("applying");
    const result = await applyPickedVideo("both");
    setPickState("idle");
    if (!result.ok) {
      showToast("No se pudo aplicar el fondo");
      return;
    }
    setPreview(null);
    showToast(
      "✓ SAMSUNG ABRIRÁ SU SELECTOR · PULSA «APLICAR EN INICIO Y BLOQUEO»",
      6000,
    );
  }



  return (
    <div className="relative">
      {/* decorative glows */}
      <div className="pointer-events-none absolute -top-32 -right-32 size-72 rounded-full bg-electric-blue/20 blur-[100px]" />
      <div className="pointer-events-none absolute top-96 -left-32 size-72 rounded-full bg-galaxy-purple/20 blur-[100px]" />

      {/* Header */}
      <header className="glass-nav sticky top-0 z-40 flex items-center justify-between px-6 py-5">
        <div className="flex items-center gap-3">
          <img
            src={aetherxLogo}
            alt="AETHERX"
            className="size-11 rounded-xl shadow-lg shadow-electric-blue/20 ring-1 ring-white/10"
          />
          <div>
            <h1 className="text-xl font-bold uppercase italic tracking-tight text-display">
              Aether<span className="text-electric-blue">X</span>
            </h1>
            <p className="text-[10px] font-semibold uppercase tracking-[0.3em] text-white/40">
              Cinematic Reality
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/admin"
            className="glass-card flex size-10 items-center justify-center rounded-full ring-1 ring-electric-blue/40"
            aria-label="Panel de administración"
            title="Panel de admin"
          >
            <Lock className="size-4 text-electric-blue" />
          </Link>
          <Link
            to="/device"
            className="glass-card flex items-center gap-1.5 rounded-full px-3 py-2 text-[10px] font-bold uppercase tracking-[0.18em] text-electric-blue"
            aria-label="Simulador de móvil"
          >
            S25 Ultra
          </Link>
          <Link
            to="/profile"
            className="glass-card flex size-10 items-center justify-center rounded-full"
            aria-label="Perfil"
          >
            <span className="size-2 rounded-full bg-electric-blue animate-shimmer" />
          </Link>
        </div>
      </header>

      {/* Hero Spotlight */}
      <section className="px-6 py-4">
        <Link
          to="/wallpaper/$id"
          params={{ id: hero.id }}
          className="group relative block aspect-[4/5] overflow-hidden rounded-[32px] outline outline-1 -outline-offset-1 outline-white/10"
        >
          <LiveMedia
            src={hero.video}
            poster={hero.src}
            alt={hero.title}
            className="size-full object-cover transition duration-[1500ms] group-hover:scale-105"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-space-black via-space-black/30 to-transparent" />

          <div className="absolute left-7 top-7 flex items-center gap-2">
            <span className="rounded-full border border-electric-blue/40 bg-electric-blue/15 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.2em] text-electric-blue">
              Nuevo lanzamiento
            </span>
          </div>

          <div className="absolute inset-x-7 bottom-7 flex items-end justify-between gap-4">
            <div>
              <h2 className="text-3xl font-bold leading-tight text-display text-ice-white">
                {hero.title}
              </h2>
              <p className="mt-1 text-sm text-white/65">{hero.subtitle}</p>
            </div>
            <span
              className="flex size-12 shrink-0 items-center justify-center rounded-full bg-ice-white text-space-black shadow-2xl shadow-electric-blue/30"
              aria-hidden="true"
            >
              <ArrowUpRight className="size-5" />
            </span>
          </div>
        </Link>
      </section>

      {/* Pick from device */}
      <section className="px-6 pt-2">
        <button
          type="button"
          onClick={handlePickDeviceVideo}
          disabled={pickState === "loading"}
          className="glass-card flex w-full items-center gap-4 rounded-2xl p-4 text-left transition active:scale-[0.99] disabled:opacity-60"
        >
          <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-tr from-electric-blue to-galaxy-purple">
            <FolderOpen className="size-5 text-white" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-bold text-display">
              {pickState === "loading" ? "Abriendo explorador..." : "Elegir vídeo del dispositivo"}
            </span>
            <span className="mt-0.5 block text-[11px] text-white/55">
              MP4 · MOV · MKV · WEBM · AVI · de Download, DCIM, WhatsApp, Telegram…
            </span>
          </span>
          <ArrowUpRight className="size-4 text-white/40" />
        </button>
        {pickToast && (
          <div className="glass-nav fixed left-1/2 top-6 z-50 -translate-x-1/2 rounded-full px-5 py-3 text-xs font-bold uppercase tracking-[0.2em] text-electric-blue shadow-2xl">
            {pickToast}
          </div>
        )}
      </section>

      {/* Fit mode selector */}
      <section className="px-6 pt-3">
        <div className="rounded-2xl border border-white/10 bg-black/40 p-3">
          <div className="mb-2 text-[10px] font-bold uppercase tracking-[0.22em] text-electric-blue/90">
            Ajuste del vídeo en el wallpaper
          </div>
          <div className="grid grid-cols-3 gap-2">
            {(["cover", "stretch", "contain"] as const).map((m) => {
              const active = fitMode === m;
              const label = m === "cover" ? "Recortar" : m === "stretch" ? "Estirar" : "Ajustar";
              const hint = m === "cover" ? "Sin deformar" : m === "stretch" ? "Rellena todo" : "Barras negras";
              return (
                <button
                  key={m}
                  type="button"
                  onClick={() => handleFitModeChange(m)}
                  className={`rounded-xl border px-2 py-2 text-center transition ${
                    active
                      ? "border-electric-blue/70 bg-electric-blue/15 text-electric-blue"
                      : "border-white/10 bg-white/[0.03] text-white/70 hover:text-white"
                  }`}
                >
                  <div className="text-[11px] font-bold uppercase tracking-[0.16em]">{label}</div>
                  <div className="mt-0.5 text-[9px] uppercase tracking-[0.14em] opacity-70">{hint}</div>
                </button>
              );
            })}
          </div>
        </div>
      </section>

      {/* Samsung diagnostic */}
      <section className="px-6 pt-3">
        <button
          type="button"
          onClick={handleCopyDiagnostic}
          className="w-full rounded-2xl border border-yellow-400/40 bg-yellow-400/10 px-4 py-3 text-left text-xs font-bold uppercase tracking-[0.18em] text-yellow-300 transition active:scale-[0.99]"
        >
          📋 Copiar diagnóstico Samsung
        </button>
        {diagFields && (() => {
          const step = diagFields.LAST_WALLPAPER_STEP ?? "(none)";
          const nativeEx = diagFields.LAST_NATIVE_EXCEPTION ?? "(none)";
          const sentDirect = step.includes("LIVE_COMPONENT_SENT");
          const fellBack = step.includes("SAMSUNG_PICKER_OPENED");
          const changeFailed = nativeEx.includes("CHANGE_LIVE_WALLPAPER");
          const branch = sentDirect && !fellBack
            ? { tag: "RAMA 1 · LIVE_COMPONENT_SENT (preview directa AetherX)", cls: "border-emerald-400/50 bg-emerald-400/10 text-emerald-300" }
            : fellBack || changeFailed
              ? { tag: "RAMA 2 · SAMSUNG_PICKER_OPENED + CHANGE_LIVE_WALLPAPER exception (Samsung rechazó el intent directo)", cls: "border-red-400/50 bg-red-400/10 text-red-300" }
              : { tag: "Sin datos · pulsa 'Abrir Live Wallpaper' antes de leer el diagnóstico", cls: "border-white/20 bg-white/5 text-white/60" };
          const rows: Array<[string, string]> = [
            ["LAST_FRONTEND_STEP", diagFields.LAST_FRONTEND_STEP ?? "(none)"],
            ["LAST_PLUGIN_ENTERED", diagFields.LAST_PLUGIN_ENTERED ?? "(none)"],
            ["PLUGIN_JS_ERROR", diagFields.PLUGIN_JS_ERROR ?? "(none)"],
            ["LAST_WALLPAPER_STEP", step],
            ["LAST_NATIVE_EXCEPTION", nativeEx],
            ["LAST_SERVICE_EVENT", diagFields.LAST_SERVICE_EVENT ?? "(none)"],
            ["LAST_ENGINE_EVENT", diagFields.LAST_ENGINE_EVENT ?? "(none)"],
            ["LAST_SURFACE_EVENT", diagFields.LAST_SURFACE_EVENT ?? "(none)"],
            ["RAW_VIDEO_FOUND", diagFields.RAW_VIDEO_FOUND ?? "(none)"],
            ["RAW_VIDEO_OPEN_OK", diagFields.RAW_VIDEO_OPEN_OK ?? "(none)"],
            ["RAW_VIDEO_OPEN_FAIL", diagFields.RAW_VIDEO_OPEN_FAIL ?? "(none)"],
            ["SERVICE_RUNNING", String(diagFields.SERVICE_RUNNING ?? "(none)")],
          ];
          return (
            <div className="mt-3 space-y-3">
              <div className={`rounded-2xl border px-4 py-3 text-[11px] font-bold uppercase tracking-[0.16em] ${branch.cls}`}>
                {branch.tag}
              </div>
              <div className="rounded-2xl border border-white/10 bg-black/60 p-3 text-[11px] leading-relaxed">
                {rows.map(([k, v]) => (
                  <div key={k} className="flex flex-col gap-0.5 border-b border-white/5 py-1.5 last:border-b-0">
                    <span className="text-[9px] font-bold uppercase tracking-[0.2em] text-electric-blue/80">{k}</span>
                    <span className="break-words text-white/85">{v}</span>
                  </div>
                ))}
              </div>
            </div>
          );
        })()}
        {diag && (
          <pre className="mt-3 max-h-72 overflow-auto rounded-2xl border border-white/10 bg-black/60 p-3 text-[10px] leading-relaxed text-white/80 whitespace-pre-wrap break-words">
{diag}
          </pre>
        )}
      </section>






      {/* Categories */}
      <section className="py-4">
        <div className="no-scrollbar flex gap-3 overflow-x-auto px-6">
          {CATEGORIES.filter((c) => c.id !== "todos").map((c, i) => {
            const previewWp = WALLPAPERS.find((w) => w.category === c.id) ?? WALLPAPERS[0];
            const is3D = String(c.id).startsWith("3d-");
            return (
                <Link
                  key={c.id}
                  to="/explore"
                  search={{ cat: c.id }}
                  className="flex-none text-center"
                >
                  <div
                    className={`relative size-16 overflow-hidden rounded-2xl glass-card transition ${
                      i === 0 || is3D ? "ring-1 ring-electric-blue/40" : ""
                    }`}
                  >
                    <img
                      src={previewWp.src}
                      alt={c.label}
                      loading="lazy"
                      decoding="async"
                      className="absolute inset-0 size-full object-cover"
                    />
                    <span className="absolute inset-0 bg-gradient-to-t from-space-black/70 via-space-black/5 to-transparent" />
                    {is3D && (
                      <span className="absolute bottom-1 left-1 rounded-full bg-electric-blue/90 px-1.5 py-0.5 text-[8px] font-black text-space-black">
                        3D
                      </span>
                    )}
                  </div>
                  <span
                    className={`mt-2 block max-w-20 text-[11px] font-semibold leading-tight ${
                      i === 0 || is3D ? "text-electric-blue" : "text-white/45"
                    }`}
                  >
                    {c.label}
                  </span>
                </Link>
            );
          })}
        </div>
      </section>

      {/* Todos los fondos 3D — grid completo, scroll infinito */}
      <section className="px-6 pt-6">
        <div className="mb-5">
          <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-white/40">
            Todos los universos
          </p>
          <h3 className="mt-1 text-lg font-bold text-display">
            Pantallas 3D · desliza para ver más
          </h3>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-4">
            {oddWallpapers.map((wp, i) => (
              <WallpaperTile key={wp.id} wp={wp} index={i * 2 + 1} />
            ))}
          </div>
          <div className="space-y-4 pt-8">
            {evenWallpapers.map((wp, i) => (
              <WallpaperTile key={wp.id} wp={wp} index={i * 2} />
            ))}
          </div>
        </div>
        {hasMoreWallpapers && (
          <button
            type="button"
            onClick={() => setVisibleWallpapers((count) => Math.min(count + 18, WALLPAPERS.length))}
            className="mt-6 w-full rounded-2xl border border-electric-blue/30 bg-electric-blue/10 px-4 py-3 text-xs font-bold uppercase tracking-[0.18em] text-electric-blue transition active:scale-[0.99]"
          >
            Ver más pantallas 3D
          </button>
        )}
      </section>


      {/* Premium teaser */}
      <section className="px-6 pt-10">
        <Link
          to="/premium"
          className="relative block overflow-hidden rounded-[28px] glass-card p-6"
        >
          <div className="pointer-events-none absolute -right-12 -top-12 size-48 rounded-full bg-galaxy-purple/30 blur-3xl" />
          <div className="relative flex items-start gap-4">
            <span className="flex size-12 items-center justify-center rounded-2xl bg-gradient-to-tr from-galaxy-purple to-electric-blue">
              <Sparkles className="size-5 text-white" />
            </span>
            <div className="flex-1">
              <p className="text-[10px] font-bold uppercase tracking-[0.25em] text-galaxy-purple">
                AetherX Pro
              </p>
              <h4 className="mt-1 text-base font-bold text-display">
                Desbloquea toda la biblioteca 4K
              </h4>
              <p className="mt-1 text-xs text-white/55">
                Nuevos mundos cada semana · sonido espacial · sin anuncios
              </p>
            </div>
            <ArrowUpRight className="size-5 text-white/40" />
          </div>
        </Link>
      </section>

      {preview && (
        <div className="fixed inset-0 z-[60] flex items-end justify-center bg-space-black/80 backdrop-blur-md sm:items-center">
          <div className="glass-card relative w-full max-w-md overflow-hidden rounded-t-[32px] sm:rounded-[32px]">
            <button
              type="button"
              onClick={() => pickState !== "applying" && setPreview(null)}
              className="absolute right-4 top-4 z-10 flex size-9 items-center justify-center rounded-full bg-space-black/60 backdrop-blur"
              aria-label="Cerrar previsualización"
            >
              <X className="size-4 text-white" />
            </button>
            <div className="relative aspect-[9/16] max-h-[65vh] w-full overflow-hidden bg-black">
              <video
                src={preview.previewUrl}
                autoPlay
                loop
                muted
                playsInline
                className="size-full object-cover"
              />
              <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-space-black to-transparent" />
              <div className="absolute left-4 top-4 flex items-center gap-1.5 rounded-full bg-space-black/60 px-3 py-1 backdrop-blur-md">
                <span className="size-1.5 rounded-full bg-electric-blue animate-shimmer" />
                <span className="text-[10px] font-bold uppercase tracking-[0.25em] text-electric-blue">
                  Previsualización
                </span>
              </div>
            </div>
            <div className="space-y-3 p-5">
              <div>
                <p className="text-[10px] font-bold uppercase tracking-[0.25em] text-white/40">
                  Vídeo del dispositivo
                </p>
                <p className="mt-1 truncate text-sm text-white/70">
                  {(preview.bytes / (1024 * 1024)).toFixed(1)} MB · Listo para aplicar
                </p>
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setPreview(null)}
                  disabled={pickState === "applying"}
                  className="flex-1 rounded-2xl border border-white/10 py-3 text-xs font-bold uppercase tracking-[0.18em] text-white/70 disabled:opacity-50"
                >
                  Cancelar
                </button>
                <button
                  type="button"
                  onClick={handleConfirmApply}
                  disabled={pickState === "applying"}
                  className="flex flex-[1.4] items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-electric-blue to-galaxy-purple py-3 text-xs font-bold uppercase tracking-[0.18em] text-white shadow-lg shadow-electric-blue/30 disabled:opacity-60"
                >
                  {pickState === "applying" ? (
                    "Aplicando..."
                  ) : (
                    <>
                      <Check className="size-4" strokeWidth={3} />
                      Aplicar como fondo 3D
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>

  );
}
