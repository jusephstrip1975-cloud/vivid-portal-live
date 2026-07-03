import { createFileRoute, Link } from "@tanstack/react-router";
import { Battery, Bell, Download, Globe, Lock, Music, Shield } from "lucide-react";
import { useAppState } from "@/lib/app-state";
import { getWallpaper } from "@/lib/wallpapers";

export const Route = createFileRoute("/profile")({
  head: () => ({
    meta: [
      { title: "Perfil · AetherX" },
      {
        name: "description",
        content: "Tu cuenta, ajustes y fondo activo en AetherX.",
      },
    ],
  }),
  component: ProfilePage,
});

const settings = [
  { icon: Bell, label: "Notificaciones", value: "Nuevos lanzamientos" },
  { icon: Battery, label: "Optimización de batería", value: "Inteligente" },
  { icon: Music, label: "Sonidos ambientales", value: "Activado" },
  { icon: Download, label: "Calidad de descarga", value: "4K · Wi-Fi" },
  { icon: Globe, label: "Idioma", value: "Español" },
  { icon: Shield, label: "Privacidad", value: "Estándar" },
];

function ProfilePage() {
  const { favorites, appliedId, unapply } = useAppState();
  const applied = appliedId ? getWallpaper(appliedId) : null;

  return (
    <div className="relative px-6 pt-6">
      <header className="glass-nav -mx-6 px-6 py-5">
        <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
          Tu cuenta
        </p>
        <h1 className="mt-1 text-2xl font-bold text-display">Perfil</h1>
      </header>

      <section className="mt-6 glass-card flex items-center gap-4 rounded-3xl p-5">
        <span className="flex size-14 items-center justify-center rounded-2xl bg-gradient-to-tr from-galaxy-purple to-electric-blue text-lg font-bold text-white text-display">
          AX
        </span>
        <div className="flex-1">
          <p className="text-base font-bold text-display">Explorador estelar</p>
          <p className="text-xs text-white/50">Plan Free · {favorites.length} favoritos</p>
        </div>
        <Link
          to="/premium"
          className="rounded-full bg-electric-blue px-4 py-2 text-[11px] font-bold uppercase tracking-[0.15em] text-space-black"
        >
          Subir a Pro
        </Link>
      </section>

      {applied && (
        <section className="mt-4 glass-card overflow-hidden rounded-3xl">
          <div className="relative h-32">
            <img
              src={applied.src}
              alt=""
              loading="lazy"
              className="absolute inset-0 size-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-space-black via-space-black/40 to-transparent" />
            <div className="absolute inset-x-5 bottom-3">
              <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
                Fondo activo
              </p>
              <p className="text-sm font-bold text-display">{applied.title}</p>
            </div>
          </div>
          <div className="flex items-center justify-between p-4">
            <p className="text-[11px] text-white/55">
              Permanece activo hasta que lo quites.
            </p>
            <button
              type="button"
              onClick={unapply}
              className="rounded-full border border-white/15 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.18em] text-white hover:bg-white/5"
            >
              Quitar
            </button>
          </div>
        </section>
      )}

      <section className="mt-6 glass-card divide-y divide-white/5 rounded-3xl">
        {settings.map((s) => (
          <div key={s.label} className="flex items-center gap-4 px-5 py-4">
            <span className="flex size-9 items-center justify-center rounded-xl bg-white/5">
              <s.icon className="size-4 text-electric-blue" />
            </span>
            <div className="flex-1">
              <p className="text-sm font-semibold text-ice-white">{s.label}</p>
              <p className="text-[11px] text-white/45">{s.value}</p>
            </div>
          </div>
        ))}
      </section>

      <Link
        to="/admin"
        className="mt-4 glass-card flex items-center gap-4 rounded-3xl px-5 py-4 hover:bg-white/5 transition"
      >
        <span className="flex size-9 items-center justify-center rounded-xl bg-electric-blue/15">
          <Lock className="size-4 text-electric-blue" />
        </span>
        <div className="flex-1">
          <p className="text-sm font-semibold text-ice-white">Panel de administración</p>
          <p className="text-[11px] text-white/45">Ver correos de testers registrados</p>
        </div>
        <span className="text-[10px] font-bold uppercase tracking-[0.2em] text-electric-blue">
          Entrar →
        </span>
      </Link>

      <p className="mt-8 text-center text-[10px] uppercase tracking-[0.25em] text-white/30">
        AetherX v1.0 · Cinematic Reality
      </p>
    </div>
  );
}
