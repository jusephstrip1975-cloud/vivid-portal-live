import { createFileRoute, Link } from "@tanstack/react-router";
import { Heart } from "lucide-react";
import { useAppState } from "@/lib/app-state";
import { WALLPAPERS } from "@/lib/wallpapers";
import { WallpaperTile } from "@/components/WallpaperTile";

export const Route = createFileRoute("/favorites")({
  head: () => ({
    meta: [
      { title: "Mis favoritos · AetherX" },
      {
        name: "description",
        content: "Tus fondos animados guardados, listos para aplicar al instante.",
      },
    ],
  }),
  component: FavoritesPage,
});

function FavoritesPage() {
  const { favorites } = useAppState();
  const list = WALLPAPERS.filter((w) => favorites.includes(w.id));

  return (
    <div className="relative">
      <header className="glass-nav sticky top-0 z-40 px-6 py-5">
        <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
          Tu colección
        </p>
        <h1 className="mt-1 text-2xl font-bold text-display">Favoritos</h1>
      </header>

      {list.length === 0 ? (
        <div className="flex flex-col items-center justify-center px-8 py-24 text-center">
          <span className="glass-card flex size-16 items-center justify-center rounded-full">
            <Heart className="size-7 text-electric-blue" />
          </span>
          <h2 className="mt-5 text-lg font-bold text-display">
            Tu galaxia está vacía
          </h2>
          <p className="mt-2 max-w-[28ch] text-sm text-white/55">
            Toca el corazón en cualquier fondo para guardarlo aquí.
          </p>
          <Link
            to="/explore"
            search={{ cat: "todos" }}
            className="mt-6 rounded-full bg-electric-blue px-6 py-3 text-xs font-bold uppercase tracking-[0.2em] text-space-black"
          >
            Explorar fondos
          </Link>
        </div>
      ) : (
        <section className="grid grid-cols-2 gap-4 px-6 pt-6">
          {list.map((wp, i) => (
            <WallpaperTile key={wp.id} wp={wp} index={i} />
          ))}
        </section>
      )}
    </div>
  );
}
