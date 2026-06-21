import { createFileRoute, Link } from "@tanstack/react-router";
import {
  ArrowLeft,
  Battery,
  Bluetooth,
  ChevronRight,
  Cog,
  Image as ImageIcon,
  Phone,
  Search,
  Signal,
  Wifi,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { LiveMedia } from "@/components/LiveMedia";
import { useAppState } from "@/lib/app-state";
import { getWallpaper, WALLPAPERS } from "@/lib/wallpapers";

export const Route = createFileRoute("/device")({
  head: () => ({
    meta: [
      { title: "Simulador Samsung Galaxy S25 Ultra · AetherX" },
      {
        name: "description",
        content:
          "Previsualiza AetherX dentro de un Samsung Galaxy S25 Ultra simulado. Navega por Ajustes y prueba los fondos animados como en un móvil real.",
      },
    ],
  }),
  component: DeviceSimulator,
});

type Screen =
  | { kind: "home" }
  | { kind: "lock" }
  | { kind: "app" }
  | { kind: "settings" }
  | { kind: "wallpaper-settings" }
  | { kind: "wallpaper-gallery" };

function DeviceSimulator() {
  const [screen, setScreen] = useState<Screen>({ kind: "lock" });
  const [time, setTime] = useState(() => new Date());
  const { appliedId, apply } = useAppState();

  useEffect(() => {
    const t = setInterval(() => setTime(new Date()), 30_000);
    return () => clearInterval(t);
  }, []);

  const wp = useMemo(
    () => (appliedId ? getWallpaper(appliedId) : null) ?? WALLPAPERS[0],
    [appliedId],
  );

  const hh = time.getHours().toString().padStart(2, "0");
  const mm = time.getMinutes().toString().padStart(2, "0");
  const dateStr = time.toLocaleDateString("es-ES", {
    weekday: "short",
    day: "numeric",
    month: "short",
  });

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#0a0a14] via-[#101025] to-[#1a0a25] p-4 py-10 text-white sm:p-10">
      {/* Page header */}
      <div className="mx-auto mb-8 flex max-w-6xl flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
            Simulador
          </p>
          <h1 className="mt-1 text-2xl font-bold text-display">Samsung Galaxy S25 Ultra</h1>
          <p className="mt-1 text-xs text-white/55">
            Navega como en un móvil real: bloqueo · inicio · ajustes · fondos
          </p>
        </div>
        <Link
          to="/"
          className="glass-card flex items-center gap-2 rounded-full px-4 py-2 text-xs font-bold uppercase tracking-[0.2em]"
        >
          <ArrowLeft className="size-4" /> Volver a la app
        </Link>
      </div>

      <div className="mx-auto flex max-w-6xl flex-col items-center gap-10 lg:flex-row lg:items-start lg:justify-center">
        {/* Phone frame */}
        <PhoneFrame>
          <ScreenRouter
            screen={screen}
            setScreen={setScreen}
            wp={wp}
            hh={hh}
            mm={mm}
            dateStr={dateStr}
            applyWp={apply}
          />
        </PhoneFrame>

        {/* Side controls */}
        <aside className="w-full max-w-sm space-y-4">
          <div className="glass-card rounded-2xl p-5">
            <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
              Atajos del simulador
            </p>
            <div className="mt-3 grid grid-cols-2 gap-2">
              <SimButton onClick={() => setScreen({ kind: "lock" })} active={screen.kind === "lock"}>
                Pantalla bloqueo
              </SimButton>
              <SimButton onClick={() => setScreen({ kind: "home" })} active={screen.kind === "home"}>
                Pantalla inicio
              </SimButton>
              <SimButton onClick={() => setScreen({ kind: "settings" })} active={screen.kind === "settings"}>
                Ajustes
              </SimButton>
              <SimButton
                onClick={() => setScreen({ kind: "wallpaper-settings" })}
                active={screen.kind === "wallpaper-settings"}
              >
                Fondos
              </SimButton>
              <SimButton onClick={() => setScreen({ kind: "app" })} active={screen.kind === "app"}>
                Abrir AetherX
              </SimButton>
            </div>
          </div>

          <div className="glass-card rounded-2xl p-5 text-xs text-white/65">
            <p className="font-bold uppercase tracking-[0.2em] text-white/90">Fondo aplicado</p>
            <p className="mt-2 text-white/55">{wp.title}</p>
            <p className="text-[11px] text-white/40">{wp.subtitle}</p>
            <p className="mt-3 text-[11px] text-white/50">
              Cambia el fondo desde <strong className="text-white">Ajustes → Fondos</strong> dentro
              del simulador. Tu selección se sincroniza con la app.
            </p>
          </div>
        </aside>
      </div>
    </div>
  );
}

function SimButton({
  children,
  onClick,
  active,
}: {
  children: React.ReactNode;
  onClick: () => void;
  active?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-xl px-3 py-2.5 text-[11px] font-bold uppercase tracking-[0.14em] transition ${
        active
          ? "bg-electric-blue text-space-black"
          : "bg-white/5 text-white/70 hover:bg-white/10"
      }`}
    >
      {children}
    </button>
  );
}

/* ------------------------------------------------------------------ */
/* Phone hardware frame: S25 Ultra-style bezel, punch-hole, buttons */
/* ------------------------------------------------------------------ */

function PhoneFrame({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative">
      {/* Hardware buttons */}
      <div className="absolute -right-[5px] top-[140px] h-16 w-[5px] rounded-r-md bg-[#1c1c1e]" />
      <div className="absolute -left-[5px] top-[180px] h-24 w-[5px] rounded-l-md bg-[#1c1c1e]" />
      <div className="absolute -left-[5px] top-[290px] h-12 w-[5px] rounded-l-md bg-[#1c1c1e]" />

      {/* Outer titanium bezel */}
      <div
        className="relative rounded-[58px] p-[4px] shadow-2xl"
        style={{
          background:
            "linear-gradient(135deg, #5a5d63 0%, #2c2e33 25%, #1a1b1f 50%, #2c2e33 75%, #5a5d63 100%)",
        }}
      >
        {/* Inner glass */}
        <div className="relative overflow-hidden rounded-[54px] bg-black">
          {/* Screen */}
          <div className="relative h-[760px] w-[360px] overflow-hidden bg-black">
            {children}
            {/* Punch hole */}
            <div className="pointer-events-none absolute left-1/2 top-2.5 size-3 -translate-x-1/2 rounded-full bg-black ring-1 ring-[#1c1c1e]">
              <span className="absolute inset-0.5 rounded-full bg-gradient-to-br from-[#0a1525] to-black" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Screen Router                                                       */
/* ------------------------------------------------------------------ */

function ScreenRouter({
  screen,
  setScreen,
  wp,
  hh,
  mm,
  dateStr,
  applyWp,
}: {
  screen: Screen;
  setScreen: (s: Screen) => void;
  wp: ReturnType<typeof getWallpaper> extends infer T ? NonNullable<T> : never;
  hh: string;
  mm: string;
  dateStr: string;
  applyWp: (id: string) => void;
}) {
  if (screen.kind === "lock") {
    return <LockScreen wp={wp} hh={hh} mm={mm} dateStr={dateStr} onUnlock={() => setScreen({ kind: "home" })} />;
  }
  if (screen.kind === "home") {
    return (
      <HomeScreen
        wp={wp}
        hh={hh}
        mm={mm}
        onOpenSettings={() => setScreen({ kind: "settings" })}
        onOpenApp={() => setScreen({ kind: "app" })}
      />
    );
  }
  if (screen.kind === "settings") {
    return (
      <SettingsScreen
        onBack={() => setScreen({ kind: "home" })}
        onOpenWallpaper={() => setScreen({ kind: "wallpaper-settings" })}
      />
    );
  }
  if (screen.kind === "wallpaper-settings") {
    return (
      <WallpaperSettingsScreen
        wp={wp}
        onBack={() => setScreen({ kind: "settings" })}
        onChange={() => setScreen({ kind: "wallpaper-gallery" })}
      />
    );
  }
  if (screen.kind === "wallpaper-gallery") {
    return (
      <WallpaperGalleryScreen
        currentId={wp.id}
        onBack={() => setScreen({ kind: "wallpaper-settings" })}
        onPick={(id) => {
          applyWp(id);
          setScreen({ kind: "wallpaper-settings" });
        }}
      />
    );
  }
  return (
    <AppFrame onClose={() => setScreen({ kind: "home" })} />
  );
}

/* ------------------------------------------------------------------ */
/* Status bar                                                          */
/* ------------------------------------------------------------------ */

function StatusBar({ time, dark = false }: { time: string; dark?: boolean }) {
  const tone = dark ? "text-white" : "text-white";
  return (
    <div className={`absolute inset-x-0 top-0 z-30 flex items-center justify-between px-6 pt-2.5 text-[11px] font-semibold ${tone}`}>
      <span>{time}</span>
      <div className="flex items-center gap-1.5 opacity-90">
        <Signal className="size-3" />
        <Wifi className="size-3" />
        <span className="rounded-[3px] border border-current px-1 text-[8px] leading-[10px]">82</span>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Lock screen                                                         */
/* ------------------------------------------------------------------ */

function LockScreen({
  wp,
  hh,
  mm,
  dateStr,
  onUnlock,
}: {
  wp: NonNullable<ReturnType<typeof getWallpaper>>;
  hh: string;
  mm: string;
  dateStr: string;
  onUnlock: () => void;
}) {
  return (
    <div className="absolute inset-0">
      <LiveMedia src={wp.video} poster={wp.src} alt={wp.title} className="size-full object-cover" />
      <div className="absolute inset-0 bg-gradient-to-b from-black/40 via-transparent to-black/70" />
      <StatusBar time={`${hh}:${mm}`} />
      <div className="absolute inset-x-0 top-20 text-center">
        <p className="text-[60px] font-light leading-none text-white drop-shadow-lg">
          {hh}:{mm}
        </p>
        <p className="mt-2 text-xs uppercase tracking-[0.3em] text-white/80">{dateStr}</p>
      </div>
      <button
        type="button"
        onClick={onUnlock}
        className="absolute inset-x-6 bottom-10 rounded-2xl bg-white/15 py-3 text-xs font-bold uppercase tracking-[0.25em] text-white backdrop-blur-md"
      >
        Desbloquear
      </button>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Home (OneUI-like)                                                   */
/* ------------------------------------------------------------------ */

function HomeScreen({
  wp,
  hh,
  mm,
  onOpenSettings,
  onOpenApp,
}: {
  wp: NonNullable<ReturnType<typeof getWallpaper>>;
  hh: string;
  mm: string;
  onOpenSettings: () => void;
  onOpenApp: () => void;
}) {
  return (
    <div className="absolute inset-0">
      <LiveMedia src={wp.video} poster={wp.src} alt={wp.title} className="size-full object-cover" />
      <div className="absolute inset-0 bg-gradient-to-b from-black/20 via-transparent to-black/60" />
      <StatusBar time={`${hh}:${mm}`} />

      {/* Clock widget */}
      <div className="absolute left-6 top-12 text-white drop-shadow-lg">
        <p className="text-[40px] font-light leading-none">
          {hh}:{mm}
        </p>
      </div>

      {/* App grid */}
      <div className="absolute inset-x-0 bottom-28 px-6">
        <div className="grid grid-cols-4 gap-y-5">
          <AppIcon label="Teléfono" color="#22c55e" icon={<Phone className="size-6 text-white" />} />
          <AppIcon label="Mensajes" color="#3b82f6" icon={<span className="text-base">💬</span>} />
          <AppIcon label="Galería" color="#f59e0b" icon={<ImageIcon className="size-6 text-white" />} />
          <AppIcon label="Cámara" color="#1f2937" icon={<span className="text-base">📷</span>} />
          <AppIcon label="Ajustes" color="#6b7280" icon={<Cog className="size-6 text-white" />} onClick={onOpenSettings} />
          <AppIcon
            label="AetherX"
            gradient="linear-gradient(135deg,#3b82f6,#a855f7)"
            icon={<span className="text-base font-black italic text-white">X</span>}
            onClick={onOpenApp}
          />
          <AppIcon label="Internet" color="#0ea5e9" icon={<span className="text-base">🌐</span>} />
          <AppIcon label="Play" color="#16a34a" icon={<span className="text-base">▶</span>} />
        </div>
      </div>

      {/* Dock */}
      <div className="absolute inset-x-4 bottom-10">
        <div className="flex items-center justify-around rounded-3xl bg-black/45 px-4 py-3 backdrop-blur-xl">
          <DockIcon color="#22c55e" icon={<Phone className="size-5 text-white" />} />
          <DockIcon color="#3b82f6" icon={<span className="text-sm">💬</span>} />
          <DockIcon color="#f59e0b" icon={<ImageIcon className="size-5 text-white" />} />
          <DockIcon color="#6b7280" icon={<Cog className="size-5 text-white" />} onClick={onOpenSettings} />
        </div>
      </div>

      {/* Home indicator */}
      <div className="absolute inset-x-0 bottom-2 flex justify-center">
        <span className="h-1 w-24 rounded-full bg-white/70" />
      </div>
    </div>
  );
}

function AppIcon({
  label,
  color,
  gradient,
  icon,
  onClick,
}: {
  label: string;
  color?: string;
  gradient?: string;
  icon: React.ReactNode;
  onClick?: () => void;
}) {
  return (
    <button type="button" onClick={onClick} className="flex flex-col items-center">
      <span
        className="flex size-12 items-center justify-center rounded-2xl shadow-lg"
        style={{ background: gradient ?? color }}
      >
        {icon}
      </span>
      <span className="mt-1.5 text-[10px] font-medium text-white drop-shadow">{label}</span>
    </button>
  );
}

function DockIcon({
  color,
  icon,
  onClick,
}: {
  color: string;
  icon: React.ReactNode;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex size-11 items-center justify-center rounded-2xl"
      style={{ background: color }}
    >
      {icon}
    </button>
  );
}

/* ------------------------------------------------------------------ */
/* Settings screen (OneUI)                                             */
/* ------------------------------------------------------------------ */

function SettingsScreen({
  onBack,
  onOpenWallpaper,
}: {
  onBack: () => void;
  onOpenWallpaper: () => void;
}) {
  return (
    <div className="absolute inset-0 overflow-y-auto bg-[#0b0b0b] text-white">
      <StatusBar time="" />
      <div className="px-5 pt-12">
        <button type="button" onClick={onBack} className="mb-3 flex size-9 items-center justify-center rounded-full bg-white/5">
          <ArrowLeft className="size-4" />
        </button>
        <h2 className="text-2xl font-semibold text-[#cfe7ff]">Ajustes</h2>
        <div className="mt-4 flex items-center gap-3 rounded-2xl bg-[#1a1a1a] px-4 py-3">
          <Search className="size-4 text-white/50" />
          <span className="text-sm text-white/40">Buscar</span>
        </div>
      </div>
      <div className="mt-5 space-y-1 px-2">
        <SettingItem icon={<Wifi className="size-5 text-electric-blue" />} title="Conexiones" subtitle="Wi-Fi, Bluetooth, Modo avión" />
        <SettingItem icon={<Bluetooth className="size-5 text-electric-blue" />} title="Sonido y vibración" />
        <SettingItem icon={<Battery className="size-5 text-electric-blue" />} title="Batería" subtitle="82% · Ahorro inteligente" />
        <SettingItem
          icon={<ImageIcon className="size-5 text-electric-blue" />}
          title="Fondo de pantalla y estilo"
          subtitle="Inicio · Bloqueo · Always On"
          onClick={onOpenWallpaper}
          highlight
        />
        <SettingItem icon={<Cog className="size-5 text-electric-blue" />} title="Pantalla" subtitle="Brillo, modo oscuro" />
      </div>
    </div>
  );
}

function SettingItem({
  icon,
  title,
  subtitle,
  onClick,
  highlight,
}: {
  icon: React.ReactNode;
  title: string;
  subtitle?: string;
  onClick?: () => void;
  highlight?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-left transition ${
        highlight ? "bg-electric-blue/10 ring-1 ring-electric-blue/30" : "hover:bg-white/5"
      }`}
    >
      <span className="flex size-9 items-center justify-center rounded-xl bg-white/5">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium text-white">{title}</span>
        {subtitle && <span className="block text-[11px] text-white/45">{subtitle}</span>}
      </span>
      <ChevronRight className="size-4 text-white/40" />
    </button>
  );
}

/* ------------------------------------------------------------------ */
/* Wallpaper settings (Samsung style)                                  */
/* ------------------------------------------------------------------ */

function WallpaperSettingsScreen({
  wp,
  onBack,
  onChange,
}: {
  wp: NonNullable<ReturnType<typeof getWallpaper>>;
  onBack: () => void;
  onChange: () => void;
}) {
  return (
    <div className="absolute inset-0 overflow-y-auto bg-[#0b0b0b] text-white">
      <StatusBar time="" />
      <div className="px-5 pt-12">
        <button type="button" onClick={onBack} className="mb-3 flex size-9 items-center justify-center rounded-full bg-white/5">
          <ArrowLeft className="size-4" />
        </button>
        <h2 className="text-xl font-semibold text-[#cfe7ff]">Fondo de pantalla y estilo</h2>
      </div>

      <div className="mt-5 px-5">
        <div className="grid grid-cols-2 gap-3">
          <MiniPreview label="Bloqueo" wp={wp} showClock />
          <MiniPreview label="Inicio" wp={wp} />
        </div>

        <button
          type="button"
          onClick={onChange}
          className="mt-5 w-full rounded-2xl bg-electric-blue py-4 text-xs font-bold uppercase tracking-[0.2em] text-space-black"
        >
          Cambiar fondos de pantalla
        </button>

        <div className="mt-3 rounded-2xl bg-[#1a1a1a] p-4">
          <p className="text-sm font-medium">Paleta de colores</p>
          <p className="mt-1 text-[11px] text-white/50">Basada en los colores del fondo</p>
          <div className="mt-3 flex gap-2">
            {["#3b82f6", "#a855f7", "#22c55e", "#f59e0b", "#ef4444"].map((c) => (
              <span key={c} className="size-7 rounded-full ring-2 ring-white/10" style={{ background: c }} />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function MiniPreview({
  label,
  wp,
  showClock,
}: {
  label: string;
  wp: NonNullable<ReturnType<typeof getWallpaper>>;
  showClock?: boolean;
}) {
  return (
    <div>
      <div className="relative aspect-[9/19] overflow-hidden rounded-2xl ring-1 ring-white/10">
        <LiveMedia src={wp.video} poster={wp.src} alt={wp.title} className="size-full object-cover" />
        {showClock && (
          <div className="absolute inset-x-0 top-6 text-center">
            <p className="text-2xl font-light text-white drop-shadow">5:26</p>
            <p className="text-[8px] uppercase tracking-widest text-white/80">dom, 21 jun</p>
          </div>
        )}
      </div>
      <p className="mt-1.5 text-center text-[11px] text-white/70">{label}</p>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Wallpaper gallery picker                                            */
/* ------------------------------------------------------------------ */

function WallpaperGalleryScreen({
  currentId,
  onBack,
  onPick,
}: {
  currentId: string;
  onBack: () => void;
  onPick: (id: string) => void;
}) {
  return (
    <div className="absolute inset-0 overflow-y-auto bg-[#0b0b0b] text-white">
      <StatusBar time="" />
      <div className="px-5 pt-12">
        <button type="button" onClick={onBack} className="mb-3 flex size-9 items-center justify-center rounded-full bg-white/5">
          <ArrowLeft className="size-4" />
        </button>
        <h2 className="text-xl font-semibold text-[#cfe7ff]">Galería AetherX</h2>
        <p className="mt-1 text-[11px] text-white/50">Toca un fondo para aplicarlo en Inicio y Bloqueo</p>
      </div>

      <div className="mt-4 grid grid-cols-3 gap-2 px-3 pb-6">
        {WALLPAPERS.slice(0, 24).map((w) => (
          <button
            key={w.id}
            type="button"
            onClick={() => onPick(w.id)}
            className={`relative aspect-[9/16] overflow-hidden rounded-xl ${
              currentId === w.id ? "ring-2 ring-electric-blue" : "ring-1 ring-white/10"
            }`}
          >
            <LiveMedia src={w.video} poster={w.src} alt={w.title} className="size-full object-cover" />
            {currentId === w.id && (
              <span className="absolute inset-x-0 bottom-0 bg-electric-blue py-0.5 text-center text-[8px] font-bold uppercase tracking-wider text-space-black">
                Aplicado
              </span>
            )}
          </button>
        ))}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* AetherX app inside the device (iframe)                              */
/* ------------------------------------------------------------------ */

function AppFrame({ onClose }: { onClose: () => void }) {
  return (
    <div className="absolute inset-0 bg-space-black">
      <iframe
        title="AetherX dentro del dispositivo"
        src="/"
        className="absolute inset-0 size-full border-0"
      />
      <button
        type="button"
        onClick={onClose}
        className="absolute bottom-2 left-1/2 z-20 -translate-x-1/2 rounded-full bg-white/80 px-4 py-1.5 text-[10px] font-bold uppercase tracking-widest text-black shadow"
      >
        ← Inicio
      </button>
    </div>
  );
}
