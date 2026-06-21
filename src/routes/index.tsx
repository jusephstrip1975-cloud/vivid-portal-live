import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowUpRight, FolderOpen, Sparkles } from "lucide-react";
import { useState } from "react";
import { CATEGORIES, WALLPAPERS } from "@/lib/wallpapers";
import { WallpaperTile } from "@/components/WallpaperTile";
import { LiveMedia } from "@/components/LiveMedia";
import { isNative, pickAndApplyDeviceVideo } from "@/lib/native-wallpaper";

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
  const trending = WALLPAPERS.slice(1, 5);
  const [pickState, setPickState] = useState<"idle" | "loading">("idle");
  const [pickToast, setPickToast] = useState<string | null>(null);

  async function handlePickDeviceVideo() {
    if (!(await isNative())) {
      setPickToast("Solo disponible en la app de Android");
      setTimeout(() => setPickToast(null), 2400);
      return;
    }
    setPickState("loading");
    const result = await pickAndApplyDeviceVideo();
    setPickState("idle");
    if (!result.ok) {
      if (result.reason !== "cancelled") {
        setPickToast("No se pudo abrir el explorador de archivos");
        setTimeout(() => setPickToast(null), 2400);
      }
      return;
    }
    setPickToast(
      result.needsPicker
        ? "✓ Pulsa Aplicar y elige Pantalla de inicio"
        : "✓ Fondo animado aplicado",
    );
    setTimeout(() => setPickToast(null), 2800);
  }


  return (
    <div className="relative">
      {/* decorative glows */}
      <div className="pointer-events-none absolute -top-32 -right-32 size-72 rounded-full bg-electric-blue/20 blur-[100px]" />
      <div className="pointer-events-none absolute top-96 -left-32 size-72 rounded-full bg-galaxy-purple/20 blur-[100px]" />

      {/* Header */}
      <header className="glass-nav sticky top-0 z-40 flex items-center justify-between px-6 py-5">
        <div>
          <h1 className="text-xl font-bold uppercase italic tracking-tight text-display">
            Aether<span className="text-electric-blue">X</span>
          </h1>
          <p className="text-[10px] font-semibold uppercase tracking-[0.3em] text-white/40">
            Cinematic Reality
          </p>
        </div>
        <Link
          to="/profile"
          className="glass-card flex size-10 items-center justify-center rounded-full"
          aria-label="Perfil"
        >
          <span className="size-2 rounded-full bg-electric-blue animate-shimmer" />
        </Link>
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



      {/* Categories */}
      <section className="py-4">
        <div className="no-scrollbar flex gap-3 overflow-x-auto px-6">
          {CATEGORIES.filter((c) => c.id !== "todos").map((c, i) => (
            <Link
              key={c.id}
              to="/explore"
              search={{ cat: c.id }}
              className="flex-none text-center"
            >
              <div
                className={`flex size-16 items-center justify-center rounded-2xl glass-card transition ${
                  i === 0 ? "ring-1 ring-electric-blue/40" : ""
                }`}
              >
                <span
                  className={`size-6 rounded-full border-2 border-dashed ${
                    i === 0
                      ? "border-electric-blue animate-shimmer"
                      : "border-white/25"
                  }`}
                  style={{ animationDuration: "3s" }}
                />
              </div>
              <span
                className={`mt-2 block text-[11px] font-semibold ${
                  i === 0 ? "text-electric-blue" : "text-white/45"
                }`}
              >
                {c.label}
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* Trending */}
      <section className="px-6 pt-6">
        <div className="mb-5 flex items-end justify-between">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-white/40">
              En tendencia
            </p>
            <h3 className="mt-1 text-lg font-bold text-display">Universos vivos</h3>
          </div>
          <Link
            to="/explore"
            search={{ cat: "todos" }}
            className="text-xs font-bold uppercase tracking-[0.18em] text-electric-blue"
          >
            Ver todos
          </Link>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-4">
            <WallpaperTile wp={trending[0]} index={0} />
            <WallpaperTile wp={trending[2]} index={2} />
          </div>
          <div className="space-y-4 pt-8">
            <WallpaperTile wp={trending[1]} index={1} />
            <WallpaperTile wp={trending[3]} index={3} />
          </div>
        </div>
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
    </div>
  );
}
