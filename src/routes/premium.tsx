import { createFileRoute } from "@tanstack/react-router";
import { Check, Sparkles } from "lucide-react";
import { useState } from "react";

export const Route = createFileRoute("/premium")({
  head: () => ({
    meta: [
      { title: "AetherX Pro · Suscripción Orbital" },
      {
        name: "description",
        content:
          "Desbloquea toda la biblioteca 4K, fondos exclusivos, sonido espacial y nuevas escenas cada semana.",
      },
    ],
  }),
  component: PremiumPage,
});

const PLANS = [
  { id: "monthly", label: "Mensual", price: "4,99 €", note: "facturación mensual" },
  { id: "yearly", label: "Anual", price: "29,99 €", note: "ahorras 50%", best: true },
] as const;

const BENEFITS = [
  "Acceso completo a la biblioteca 4K y 8K",
  "Fondos exclusivos con parallax 3D",
  "Sonido ambiental espacial",
  "Nuevos mundos cada semana",
  "Sin anuncios de por vida",
  "Descargas ilimitadas",
];

function PremiumPage() {
  const [plan, setPlan] = useState<string>("yearly");

  return (
    <div className="relative px-6 pt-6">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[420px] -z-0 ambient-glow" />

      <header className="relative pt-6 text-center">
        <span className="inline-flex items-center gap-1.5 rounded-full border border-galaxy-purple/40 bg-galaxy-purple/15 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.25em] text-galaxy-purple">
          <Sparkles className="size-3" />
          Acceso Orbital
        </span>
        <h1 className="mt-4 text-4xl font-bold leading-[1.05] text-display">
          AetherX <span className="text-electric-blue">Pro</span>
        </h1>
        <p className="mx-auto mt-3 max-w-[32ch] text-sm text-white/60">
          El futuro de los fondos de pantalla en movimiento. Cinematográfico,
          optimizado, infinito.
        </p>
      </header>

      <section className="relative mt-8 space-y-3">
        {PLANS.map((p) => {
          const active = plan === p.id;
          return (
            <button
              key={p.id}
              type="button"
              onClick={() => setPlan(p.id)}
              className={`w-full rounded-2xl border p-5 text-left transition ${
                active
                  ? "border-electric-blue bg-electric-blue/10 shadow-lg shadow-electric-blue/20"
                  : "border-white/10 glass-card"
              }`}
            >
              <div className="flex items-center justify-between">
                <div>
                  <p className="flex items-center gap-2 text-sm font-bold text-display">
                    {p.label}
                    {p.best && (
                      <span className="rounded-full bg-galaxy-purple/30 px-2 py-0.5 text-[9px] font-bold uppercase tracking-widest text-galaxy-purple">
                        Mejor valor
                      </span>
                    )}
                  </p>
                  <p className="mt-1 text-xs text-white/45">{p.note}</p>
                </div>
                <div className="text-right">
                  <p className="text-lg font-bold text-display">{p.price}</p>
                </div>
              </div>
            </button>
          );
        })}
      </section>

      <section className="relative mt-8 glass-card rounded-3xl p-6">
        <h3 className="text-xs font-bold uppercase tracking-[0.25em] text-electric-blue">
          Incluido
        </h3>
        <ul className="mt-4 space-y-3">
          {BENEFITS.map((b) => (
            <li key={b} className="flex items-start gap-3 text-sm text-white/85">
              <span className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-electric-blue/20">
                <Check className="size-3 text-electric-blue" strokeWidth={3} />
              </span>
              {b}
            </li>
          ))}
        </ul>
      </section>

      <button
        type="button"
        className="relative mt-8 w-full rounded-2xl bg-gradient-to-r from-electric-blue to-galaxy-purple py-4 text-sm font-bold uppercase tracking-[0.2em] text-white shadow-2xl shadow-electric-blue/30"
      >
        Suscribirse · {PLANS.find((p) => p.id === plan)?.price}
      </button>
      <p className="relative mt-3 text-center text-[10px] text-white/40">
        Cancela cuando quieras. Renovación automática.
      </p>
    </div>
  );
}
