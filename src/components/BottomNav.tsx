import { Link, useRouterState } from "@tanstack/react-router";
import { Compass, Heart, Home, Sparkles, User } from "lucide-react";
import type { ComponentType } from "react";

type Item = {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
};

const items: Item[] = [
  { to: "/", label: "Inicio", icon: Home },
  { to: "/explore", label: "Explorar", icon: Compass },
  { to: "/favorites", label: "Favoritos", icon: Heart },
  { to: "/premium", label: "Premium", icon: Sparkles },
  { to: "/profile", label: "Perfil", icon: User },
];

export function BottomNav() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  return (
    <nav
      aria-label="Navegación principal"
      className="glass-nav fixed bottom-4 left-4 right-4 z-50 mx-auto flex h-18 max-w-md items-center justify-around rounded-3xl px-3 py-3 shadow-2xl shadow-black/40"
    >
      {items.map((item) => {
        const active =
          item.to === "/" ? pathname === "/" : pathname.startsWith(item.to);
        const Icon = item.icon;
        const isPremium = item.to === "/premium";
        return (
          <Link
            key={item.to}
            to={item.to}
            className="group flex flex-1 flex-col items-center gap-1"
          >
            <span
              className={
                isPremium
                  ? "flex size-10 items-center justify-center rounded-full bg-gradient-to-tr from-galaxy-purple to-electric-blue shadow-lg shadow-galaxy-purple/40"
                  : `flex size-10 items-center justify-center rounded-full transition ${
                      active
                        ? "bg-electric-blue/15 text-electric-blue"
                        : "text-white/45 group-hover:text-white/80"
                    }`
              }
            >
              <Icon className={isPremium ? "size-4 text-white" : "size-5"} />
            </span>
            <span
              className={`text-[9px] font-bold uppercase tracking-[0.12em] ${
                active
                  ? "text-electric-blue"
                  : isPremium
                    ? "text-white/80"
                    : "text-white/40"
              }`}
            >
              {item.label}
            </span>
          </Link>
        );
      })}
    </nav>
  );
}
