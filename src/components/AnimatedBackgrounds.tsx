import { Link } from "@tanstack/react-router";
import { Maximize } from "lucide-react";
import { ANIMATED_BACKGROUNDS } from "@/lib/animated-backgrounds";
import { resolveDownloadUrl } from "@/lib/native-wallpaper";

// Each animated background opens the shared wallpaper detail page, so it gets
// the same "pantalla de inicio / bloqueo / ambas" native flow as the rest.
export function AnimatedBackgrounds() {
  return (
    <section className="px-6 py-6" aria-labelledby="animated-backgrounds-title">
      <h2 id="animated-backgrounds-title" className="font-display text-lg font-bold text-foreground">FONDOS 3D ANIMADOS</h2>
      <div className="mt-4 grid grid-cols-3 gap-3">
        {ANIMATED_BACKGROUNDS.map((background) => (
          <article key={background.id} className="min-w-0">
            <Link
              to="/wallpaper/$id"
              params={{ id: background.id }}
              className="relative block w-full overflow-hidden rounded-lg aspect-[9/16]"
              aria-label={`Abrir fondo: ${background.title}`}
            >
              <img src={resolveDownloadUrl(background.image)} alt={background.title} loading="lazy" decoding="async" className="size-full object-cover" />
              <Maximize className="absolute bottom-2 right-2 text-foreground" />
            </Link>
            <h3 className="mt-2 text-xs font-semibold text-foreground">{background.title}</h3>
            <p className="mt-1 text-[10px] leading-relaxed text-muted-foreground">{background.status}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
