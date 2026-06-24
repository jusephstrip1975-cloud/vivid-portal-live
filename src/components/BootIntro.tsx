import { useEffect, useState } from "react";

const SESSION_KEY = "aetherx.intro.shown.v1";

export function BootIntro() {
  const [phase, setPhase] = useState<"black" | "logo" | "fading" | "done">(
    "done",
  );

  useEffect(() => {
    if (typeof window === "undefined") return;
    // Only on first load of the session (so navegación interna no la re-dispara)
    if (sessionStorage.getItem(SESSION_KEY)) return;
    sessionStorage.setItem(SESSION_KEY, "1");

    setPhase("black");
    const t1 = setTimeout(() => setPhase("logo"), 1000); // 1s pantalla negra
    const t2 = setTimeout(() => setPhase("fading"), 2800); // logo visible ~1.8s
    const t3 = setTimeout(() => setPhase("done"), 3400); // fade out 600ms
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
    };
  }, []);

  if (phase === "done") return null;

  const showLogo = phase === "logo" || phase === "fading";
  const fading = phase === "fading";

  return (
    <div
      aria-hidden="true"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black transition-opacity duration-700"
      style={{ opacity: fading ? 0 : 1, pointerEvents: fading ? "none" : "auto" }}
    >
      <div
        className="flex flex-col items-center gap-5 transition-all duration-1000"
        style={{
          opacity: showLogo ? 1 : 0,
          transform: showLogo ? "translateY(0) scale(1)" : "translateY(8px) scale(0.96)",
        }}
      >
        <h1 className="text-5xl font-bold uppercase italic tracking-tight text-display text-ice-white">
          Aether<span className="text-electric-blue">X</span>
        </h1>
        <p className="text-[11px] font-semibold uppercase tracking-[0.45em] text-white/70">
          La disciplina lo es todo
        </p>
      </div>
    </div>
  );
}
