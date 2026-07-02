import { createFileRoute, Link, notFound, useRouter } from "@tanstack/react-router";
import { ArrowLeft, Check, Heart, Home, Layers, Lock, Share2, Volume2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useAppState } from "@/lib/app-state";
import { getWallpaper } from "@/lib/wallpapers";
import { LiveMedia } from "@/components/LiveMedia";
import {
  checkWallpaperCompatibility,
  isLiveWallpaperPluginAvailable,
  isNative,
  recordFrontendStep,
  saveWallpaperToDevice,
  type WallpaperTarget,
} from "@/lib/native-wallpaper";

const ANDROID_APK_URL =
  "https://github.com/jusephstrip1975-cloud/vivid-portal-live/releases/latest/download/aetherx-latest.apk";

export const Route = createFileRoute("/wallpaper/$id")({
  loader: ({ params }) => {
    const wp = getWallpaper(params.id);
    if (!wp) throw notFound();
    return wp;
  },
  head: ({ loaderData }) => ({
    meta: loaderData
      ? [
          { title: `${loaderData.title} · AetherX` },
          { name: "description", content: loaderData.subtitle },
          { property: "og:title", content: `${loaderData.title} · AetherX` },
          { property: "og:description", content: loaderData.subtitle },
          { property: "og:image", content: loaderData.src },
          { name: "twitter:image", content: loaderData.src },
        ]
      : [],
  }),
  notFoundComponent: () => (
    <div className="flex min-h-screen items-center justify-center p-8 text-center">
      <div>
        <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
          Sin señal
        </p>
        <h1 className="mt-2 text-2xl font-bold text-display">Fondo no encontrado</h1>
        <Link
          to="/explore"
          search={{ cat: "todos" }}
          className="mt-5 inline-flex rounded-full bg-electric-blue px-5 py-2.5 text-xs font-bold uppercase tracking-[0.2em] text-space-black"
        >
          Volver a explorar
        </Link>
      </div>
    </div>
  ),
  component: WallpaperDetail,
});

function WallpaperDetail() {
  const wp = Route.useLoaderData();
  const router = useRouter();
  const { isFavorite, toggleFavorite, apply, appliedId } = useAppState();
  const [downloadState, setDownloadState] = useState<"idle" | "downloading" | "done">("idle");
  const [activeTarget, setActiveTarget] = useState<WallpaperTarget | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [nativeReady, setNativeReady] = useState(false);

  const isDownloaded = appliedId === wp.id;
  const fav = isFavorite(wp.id);

  useEffect(() => {
    let alive = true;
    void isNative().then((native) => {
      if (alive) setNativeReady(native);
    });
    return () => {
      alive = false;
    };
  }, []);

  const targetLabels: Record<WallpaperTarget, string> = {
    home: "Pantalla de inicio",
    lock: "Pantalla de bloqueo",
    both: "Inicio y bloqueo",
  };

  async function handleApply(target: WallpaperTarget) {
    const fileName = `aetherx-${wp.id}.mp4`;
    const buttonTag =
      target === "home" ? "BUTTON_APPLY_HOME_CLICKED"
      : target === "lock" ? "BUTTON_APPLY_LOCK_CLICKED"
      : "BUTTON_APPLY_BOTH_CLICKED";
    const frontendTag =
      target === "home" ? "FRONTEND_CALL_PLUGIN_APPLY_HOME"
      : target === "lock" ? "FRONTEND_CALL_PLUGIN_APPLY_LOCK"
      : "FRONTEND_CALL_PLUGIN_APPLY_BOTH";
    // Visible UI marker — confirms the button click reached React.
    setToast(`▶ ${buttonTag}`);
    console.info("[AetherX]", buttonTag);
    try {
      setActiveTarget(target);
      setDownloadState("downloading");

      if (await isNative()) {
        // Confirm plugin presence before doing anything else.
        const available = await isLiveWallpaperPluginAvailable();
        if (!available) {
          console.warn("[AetherX] PLUGIN_NOT_FOUND");
          await recordFrontendStep("PLUGIN_NOT_FOUND");
          setDownloadState("idle");
          setActiveTarget(null);
          setToast("⚠ PLUGIN_NOT_FOUND — el plugin nativo no está registrado");
          setTimeout(() => setToast(null), 3600);
          return;
        }
        await recordFrontendStep(frontendTag);
        // Verificación previa de compatibilidad (solo bloquea si el destino requiere Live Wallpaper)
        const compat = await checkWallpaperCompatibility();
        if (compat) {
          const needsLive = target === "home" || target === "lock" || target === "both";
          if (needsLive && !compat.canApplyHome && compat.reason !== "no-video") {
            setDownloadState("idle");
            setActiveTarget(null);
            setToast(`⚠ ${compat.message}`);
            setTimeout(() => setToast(null), 3600);
            return;
          }
        }
        const result = await saveWallpaperToDevice(wp.video, fileName, target);
        if (!result.ok) throw new Error(result.reason ?? "save-failed");
        const successMsg =
          target === "lock"
            ? "✓ Selector nativo abierto para bloqueo"
            : target === "both"
              ? "✓ Selector nativo abierto para inicio y bloqueo"
              : "✓ Selector nativo abierto para inicio";
        setToast(
          result.needsPicker
            ? target === "lock"
              ? "✓ Pulsa Aplicar y elige Pantalla de bloqueo si aparece"
              : target === "both"
                ? "✓ Pulsa Aplicar y elige Inicio y bloqueo si aparece"
                : "✓ Pulsa Aplicar y elige Pantalla de inicio"
            : successMsg,
        );
      } else {
        setDownloadState("idle");
        setActiveTarget(null);
        setToast("Instala y abre la app AETHERX para aplicarlo en pantalla de inicio");
        setTimeout(() => setToast(null), 4200);
        return;
      }

      apply(wp.id);
      setDownloadState("done");
      setTimeout(() => setToast(null), 2600);
      setTimeout(() => {
        setDownloadState("idle");
        setActiveTarget(null);
      }, 1800);
    } catch (err) {
      console.error(err);
      const msg = err instanceof Error ? `${err.name}: ${err.message}` : String(err);
      void recordFrontendStep("PLUGIN_JS_ERROR", msg);
      setDownloadState("idle");
      setActiveTarget(null);
      setToast(`✗ PLUGIN_JS_ERROR: ${msg.slice(0, 120)}`);
      setTimeout(() => setToast(null), 4000);
    }
  }

  return (
    <div className="relative min-h-screen pb-32">
      {/* Full-bleed preview */}
      <div className="relative h-[68vh] min-h-[520px] overflow-hidden">
        <LiveMedia
          src={wp.video}
          poster={wp.src}
          alt={wp.title}
          className="size-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-b from-space-black/40 via-transparent to-space-black" />

        {/* Top bar */}
        <div className="absolute inset-x-4 top-4 flex items-center justify-between">
          <button
            type="button"
            onClick={() => router.history.back()}
            className="glass-card flex size-10 items-center justify-center rounded-full"
            aria-label="Volver"
          >
            <ArrowLeft className="size-4 text-white" />
          </button>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => toggleFavorite(wp.id)}
              className="glass-card flex size-10 items-center justify-center rounded-full"
              aria-label={fav ? "Quitar de favoritos" : "Añadir a favoritos"}
            >
              <Heart
                className={`size-4 ${fav ? "fill-electric-blue text-electric-blue" : "text-white"}`}
              />
            </button>
            <button
              type="button"
              className="glass-card flex size-10 items-center justify-center rounded-full"
              aria-label="Compartir"
            >
              <Share2 className="size-4 text-white" />
            </button>
          </div>
        </div>

        {/* Live badge */}
        <div className="absolute left-6 top-20 flex items-center gap-1.5 rounded-full bg-space-black/60 px-3 py-1 backdrop-blur-md">
          <span className="size-1.5 rounded-full bg-electric-blue animate-shimmer" />
          <span className="text-[10px] font-bold uppercase tracking-[0.25em] text-electric-blue">
            Vista previa en vivo
          </span>
        </div>
      </div>

      {/* Sheet */}
      <section className="-mt-12 px-6">
        <div className="glass-card rounded-[28px] p-6">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <h1 className="text-2xl font-bold leading-tight text-display">{wp.title}</h1>
              <p className="mt-1 text-sm text-white/55">{wp.subtitle}</p>
            </div>
            <span className="shrink-0 rounded-full border border-electric-blue/40 bg-electric-blue/15 px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.18em] text-electric-blue">
              {wp.resolution}
            </span>
          </div>

          <dl className="mt-5 grid grid-cols-3 gap-3 border-y border-white/8 py-4">
            <Stat label="FPS" value={`${wp.fps}`} />
            <Stat label="Tamaño" value={`${wp.sizeMb.toFixed(1)} MB`} />
            <Stat label="Audio" value={wp.sound ? "Espacial" : "Silencio"} />
          </dl>

          <div className="mt-5 grid gap-2">
            {nativeReady ? (
              (["home", "lock", "both"] as WallpaperTarget[]).map((target) => {
                const isActive = activeTarget === target;
                const Icon = target === "home" ? Home : target === "lock" ? Lock : Layers;
                const isPrimary = target === "both";
                return (
                  <button
                    key={target}
                    type="button"
                    onClick={() => handleApply(target)}
                    disabled={downloadState === "downloading"}
                    className={`flex w-full items-center justify-center gap-2 rounded-2xl py-3.5 text-[12px] font-bold uppercase tracking-[0.18em] transition disabled:opacity-60 ${
                      isPrimary
                        ? "bg-gradient-to-r from-electric-blue to-galaxy-purple text-white shadow-lg shadow-electric-blue/30"
                        : "border border-white/15 bg-white/5 text-white hover:bg-white/10"
                    }`}
                  >
                    {isActive && downloadState === "downloading" ? (
                      "Aplicando..."
                    ) : isActive && downloadState === "done" ? (
                      <>
                        <Check className="size-4" strokeWidth={3} />
                        Aplicado
                      </>
                    ) : (
                      <>
                        <Icon className="size-4" />
                        Establecer en {targetLabels[target]}
                      </>
                    )}
                  </button>
                );
              })
            ) : (
              <>
                <a
                  href={ANDROID_APK_URL}
                  download="aetherx-latest.apk"
                  className="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-electric-blue to-galaxy-purple px-4 py-4 text-center text-[12px] font-bold uppercase tracking-[0.16em] text-white shadow-lg shadow-electric-blue/30"
                >
                  Descargar app Android
                </a>
                <p className="text-center text-[11px] leading-relaxed text-white/55">
                  En Chrome solo se descargan vídeos. Para ponerlo en pantalla de inicio o bloqueo, instala y abre AETHERX desde el icono del móvil.
                </p>
              </>
            )}
            {isDownloaded && (
              <p className="mt-1 text-center text-[10px] uppercase tracking-[0.25em] text-electric-blue/80">
                Último aplicado
              </p>
            )}
          </div>

          {wp.sound && (
            <div className="mt-4 flex items-center gap-2 text-[11px] text-white/45">
              <Volume2 className="size-3.5 text-electric-blue" />
              Incluye audio ambiental opcional
            </div>
          )}
        </div>

        {/* Instrucciones sencillas */}
        <div className="mt-5 rounded-2xl border border-white/8 bg-white/5 p-5 text-left text-xs leading-relaxed text-white/70">
          <p className="mb-2 text-[10px] font-bold uppercase tracking-[0.25em] text-electric-blue">
            Cómo ponerlo de fondo
          </p>
          <ol className="space-y-1.5 list-decimal pl-5 text-white/65">
            <li>Abre AETHERX desde el icono de la app instalada, no desde Galería/Fotos.</li>
            <li>Pulsa <strong className="text-white">Inicio y bloqueo</strong> para abrir el selector nativo.</li>
            <li>El sistema abre <strong className="text-white">AetherX Live Wallpaper</strong> para aplicarlo animado.</li>
            <li>
              En Android pulsa <strong className="text-white">Aplicar</strong> y elige el destino que tu móvil muestre.
            </li>
          </ol>
        </div>

        {toast && (
          <div className="glass-nav fixed left-1/2 top-6 z-50 -translate-x-1/2 rounded-full px-5 py-3 text-xs font-bold uppercase tracking-[0.2em] text-electric-blue shadow-2xl">
            {toast}
          </div>
        )}
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[9px] font-bold uppercase tracking-[0.25em] text-white/40">{label}</dt>
      <dd className="mt-1 text-sm font-semibold text-ice-white text-display">{value}</dd>
    </div>
  );
}
