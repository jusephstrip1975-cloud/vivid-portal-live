import { createFileRoute } from "@tanstack/react-router";
import { z } from "zod";
import { CATEGORIES, WALLPAPERS, type Category } from "@/lib/wallpapers";
import { WallpaperTile } from "@/components/WallpaperTile";

const search = z.object({
  cat: z
    .custom<Category | "todos">(
      (value) =>
        typeof value === "string" &&
        CATEGORIES.some((category) => category.id === value),
    )
    .catch("todos"),
});

export const Route = createFileRoute("/explore")({
  validateSearch: search,
  head: () => ({
    meta: [
      { title: "Explorar fondos animados · AetherX" },
      {
        name: "description",
        content:
          "Explora más de 1,400 fondos animados 4K por categoría: universo, océano, cyberpunk, zen, naturaleza y clima.",
      },
    ],
  }),
  component: ExplorePage,
});

function ExplorePage() {
  const { cat } = Route.useSearch();
  const navigate = Route.useNavigate();
  const list = WALLPAPERS.filter((w) => cat === "todos" || w.category === cat);

  return (
    <div className="relative">
      <header className="glass-nav sticky top-0 z-40 px-6 py-5">
        <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
          Archivo cinematográfico
        </p>
        <h1 className="mt-1 text-2xl font-bold text-display">Explorar</h1>
      </header>

      <div className="no-scrollbar mt-4 flex gap-2 overflow-x-auto px-6 pb-3">
        {CATEGORIES.map((c) => {
          const active = c.id === cat;
          return (
            <button
              key={c.id}
              type="button"
              onClick={() =>
                navigate({ search: { cat: c.id as Category | "todos" } })
              }
              className={`flex-none rounded-full px-4 py-2 text-xs font-semibold transition ${
                active
                  ? "bg-ice-white text-space-black"
                  : "glass-card text-white/65 hover:text-white"
              }`}
            >
              {c.label}
            </button>
          );
        })}
      </div>

      <div className="flex items-center justify-between px-6 py-4">
        <p className="text-[10px] uppercase tracking-[0.25em] text-white/40">
          {list.length} resultados
        </p>
        <p className="text-[10px] uppercase tracking-[0.25em] text-electric-blue">
          Calidad 4K · 60fps
        </p>
      </div>

      <section className="grid grid-cols-2 gap-4 px-6">
        {list.map((wp, i) => (
          <WallpaperTile key={wp.id} wp={wp} index={i} />
        ))}
      </section>

      {list.length === 0 && (
        <div className="px-6 py-16 text-center text-sm text-white/50">
          Aún no hay fondos en esta categoría.
        </div>
      )}
    </div>
  );
}
