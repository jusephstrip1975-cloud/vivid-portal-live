import { createFileRoute, Link, notFound, useRouter } from "@tanstack/react-router";
import { ArrowLeft, Check, Download, Heart, Share2, Volume2 } from "lucide-react";
import { useState } from "react";
import { useAppState } from "@/lib/app-state";
import { getWallpaper } from "@/lib/wallpapers";
import { LiveMedia } from "@/components/LiveMedia";

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
        <h1 className="mt-2 text-2xl font-bold text-display">
          Fondo no encontrado
        </h1>
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
  const [justApplied, setJustApplied] = useState(false);
  const [downloadState, setDownloadState] = useState<"idle" | "downloading" | "done">("idle");
  const [target, setTarget] = useState<"home" | "lock" | "both">("both");

  const isApplied = appliedId === wp.id;
  const fav = isFavorite(wp.id);

  async function handleApply() {
    apply(wp.id);
    setJustApplied(true);
    setTimeout(() => setJustApplied(false), 2200);
    // If running inside the Capacitor Android build, use the animated file instead of the static poster.
    const { setDeviceWallpaper } = await import("@/lib/native-wallpaper");
    setDeviceWallpaper(wp.video, target).catch(() => {});
  }

  async function handleDownload() {
    const fileName = `aetherx-${wp.id}-animado.mp4`;

    try {
      setDownloadState("downloading");
      const res = await fetch(wp.video, { mode: "cors" });
      if (!res.ok) throw new Error("No se pudo descargar el vídeo");
      const blob = await res.blob();

      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = fileName;
      a.type = "video/mp4";
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setDownloadState("done");
      setTimeout(() => setDownloadState("idle"), 1800);
    } catch {
      window.open(wp.video, "_blank", "noopener,noreferrer");
      setDownloadState("idle");
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
              <h1 className="text-2xl font-bold leading-tight text-display">
                {wp.title}
              </h1>
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

          <div className="mt-5 grid grid-cols-[1fr_auto] gap-3">
            <button
              type="button"
              onClick={handleApply}
              className={`relative flex items-center justify-center gap-2 rounded-2xl py-4 text-sm font-bold uppercase tracking-[0.18em] transition ${
                isApplied
                  ? "bg-electric-blue/15 text-electric-blue ring-1 ring-electric-blue/40"
                  : "bg-gradient-to-r from-electric-blue to-galaxy-purple text-white shadow-lg shadow-electric-blue/30"
              }`}
            >
              {isApplied ? (
                <>
                  <Check className="size-4" strokeWidth={3} />
                  Aplicado
                </>
              ) : (
                "Aplicar ahora"
              )}
            </button>
            <button
              type="button"
              onClick={handleDownload}
              disabled={downloadState === "downloading"}
              className="flex min-w-14 items-center justify-center rounded-2xl border border-white/12 bg-white/5 px-5 text-white transition hover:bg-white/10 disabled:opacity-60"
              aria-label="Descargar vídeo animado"
            >
              {downloadState === "done" ? <Check className="size-5" /> : <Download className="size-5" />}
            </button>

          </div>

          {wp.sound && (
            <div className="mt-4 flex items-center gap-2 text-[11px] text-white/45">
              <Volume2 className="size-3.5 text-electric-blue" />
              Incluye audio ambiental opcional
            </div>
          )}
        </div>

        {/* Note about web limitation */}
        <p className="mt-4 px-2 text-center text-[10px] leading-relaxed text-white/35">
          Toca <span className="text-electric-blue">Descargar</span> para guardar el archivo <strong>MP4 animado</strong> en tu móvil.
          Luego ábrelo desde Fotos / Galería y elige <strong>"Usar como fondo de pantalla"</strong> si tu móvil admite fondos animados.
          Por seguridad, iOS y los navegadores no permiten cambiar el fondo automáticamente.
        </p>


        {justApplied && (
          <div className="glass-nav fixed left-1/2 top-6 z-50 -translate-x-1/2 rounded-full px-5 py-3 text-xs font-bold uppercase tracking-[0.2em] text-electric-blue shadow-2xl">
            ✓ Fondo aplicado
          </div>
        )}
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[9px] font-bold uppercase tracking-[0.25em] text-white/40">
        {label}
      </dt>
      <dd className="mt-1 text-sm font-semibold text-ice-white text-display">
        {value}
      </dd>
    </div>
  );
}
