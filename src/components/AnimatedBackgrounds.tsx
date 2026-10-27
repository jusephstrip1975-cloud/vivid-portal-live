import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Capacitor } from "@capacitor/core";
import { Download, Maximize, Pause, Play, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ANIMATED_BACKGROUNDS, type AnimatedBackground } from "@/lib/animated-backgrounds";
import { canActivateBackground, shouldPlayBackground, type BackgroundPlatform } from "@/lib/animated-background-policy";
import { checkWallpaperCompatibility, resolveDownloadUrl, saveWallpaperToDevice, type WallpaperTarget } from "@/lib/native-wallpaper";

export function AnimatedBackgrounds() {
  const [selected, setSelected] = useState<AnimatedBackground | null>(null);
  return (
    <section className="px-6 py-6" aria-labelledby="animated-backgrounds-title">
      <h2 id="animated-backgrounds-title" className="font-display text-lg font-bold text-foreground">FONDOS 3D ANIMADOS</h2>
      <div className="mt-4 grid grid-cols-3 gap-3">
        {ANIMATED_BACKGROUNDS.map((background) => (
          <article key={background.id} className="min-w-0">
            <Button variant="ghost" className="relative h-auto w-full overflow-hidden rounded-lg p-0 aspect-[9/16]" onClick={() => setSelected(background)} aria-label={`Vista previa: ${background.title}`}>
              <img src={resolveDownloadUrl(background.image)} alt={background.title} loading="lazy" decoding="async" className="size-full object-cover" />
              <Maximize className="absolute bottom-2 right-2 text-foreground" />
            </Button>
            <h3 className="mt-2 text-xs font-semibold text-foreground">{background.title}</h3>
            <p className="mt-1 text-[10px] leading-relaxed text-muted-foreground">{background.status}</p>
            <Button variant="link" className="mt-1 h-auto whitespace-normal px-0 text-[10px]" onClick={() => setSelected(background)}>VISTA PREVIA</Button>
          </article>
        ))}
      </div>
      {selected && <BackgroundPreview key={selected.id} background={selected} onClose={() => setSelected(null)} />}
    </section>
  );
}

function BackgroundPreview({ background, onClose }: { background: AnimatedBackground; onClose: () => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const [platform, setPlatform] = useState<BackgroundPlatform>("web");
  const [hasPlugin, setHasPlugin] = useState(false);
  const [target, setTarget] = useState<WallpaperTarget>("both");
  const [requested, setRequested] = useState(true);
  const [visible, setVisible] = useState(true);
  const [reduced, setReduced] = useState(true);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const playing = shouldPlayBackground(requested, visible, reduced);
  const canActivate = canActivateBackground(platform, hasPlugin, Boolean(background.video)) && ready && !error;

  useEffect(() => {
    const previous = document.activeElement;
    const scroll = document.querySelector<HTMLElement>(".aetherx-scroll");
    const previousOverflow = scroll?.style.overflowY ?? "";
    if (scroll) scroll.style.overflowY = "hidden";
    closeRef.current?.focus();
    const actualPlatform = Capacitor.getPlatform();
    setPlatform(actualPlatform === "android" || actualPlatform === "ios" ? actualPlatform : "web");
    setHasPlugin(Capacitor.isNativePlatform() && Capacitor.isPluginAvailable("AetherXLiveWallpaper"));
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const motion = () => setReduced(media.matches);
    const visibility = () => setVisible(document.visibilityState === "visible");
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
      if (event.key !== "Tab") return;
      const controls = document.querySelectorAll<HTMLElement>("[data-background-preview] button:not(:disabled), [data-background-preview] select");
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    motion(); visibility();
    media.addEventListener("change", motion);
    document.addEventListener("visibilitychange", visibility);
    document.addEventListener("keydown", keyboard);
    return () => {
      if (scroll) scroll.style.overflowY = previousOverflow;
      media.removeEventListener("change", motion);
      document.removeEventListener("visibilitychange", visibility);
      document.removeEventListener("keydown", keyboard);
      if (previous instanceof HTMLElement) previous.focus();
    };
  }, [onClose]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    if (playing && !error) video.play().catch(() => setRequested(false));
    else video.pause();
  }, [playing, ready, error]);

  async function activate() {
    if (!canActivate || !background.video) return;
    setBusy(true); setMessage("");
    try {
      const compatibility = await checkWallpaperCompatibility();
      if (compatibility && !compatibility.canApplyHome && compatibility.reason !== "no-video") throw new Error(compatibility.message);
      const result = await saveWallpaperToDevice(background.video, `${background.id}.mp4`, target);
      if (!result.ok) throw new Error(result.reason ?? "No se pudo abrir el selector Android.");
      setMessage("Selector Android abierto. Confirma Aplicar y elige el destino que permita tu móvil; todavía no se ha confirmado la aplicación.");
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "No se pudo abrir el selector Android."); }
    finally { setBusy(false); }
  }

  async function exportFile(video: boolean) {
    const source = video ? background.video : background.image;
    if (!source) return;
    setBusy(true); setMessage("");
    try {
      const response = await fetch(resolveDownloadUrl(source));
      if (!response.ok) throw new Error("No se pudo descargar el fondo.");
      const blob = await response.blob();
      const file = new File([blob], `${background.id}.${video ? "mp4" : "png"}`, { type: video ? "video/mp4" : "image/png" });
      if (navigator.canShare?.({ files: [file] })) await navigator.share({ files: [file], title: background.title });
      else {
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a"); link.href = url; link.download = file.name; link.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 60000);
      }
      setMessage("Exportación solicitada. No se ha aplicado ningún fondo del sistema.");
    } catch (cause) { if (!(cause instanceof Error && cause.name === "AbortError")) setMessage(cause instanceof Error ? cause.message : "No se pudo exportar."); }
    finally { setBusy(false); }
  }

  return createPortal(
    <div data-background-preview role="dialog" aria-modal="true" aria-labelledby="background-preview-title" className="fixed inset-0 z-[100] flex flex-col overflow-y-auto bg-background text-foreground">
      <header className="flex shrink-0 items-center justify-between gap-3 px-5 py-3">
        <h2 id="background-preview-title" className="font-display text-base font-semibold">{background.title}</h2>
        <Button ref={closeRef} size="icon" variant="ghost" onClick={onClose} aria-label="Cerrar vista previa"><X /></Button>
      </header>
      <div className="relative min-h-0 flex-1 bg-background">
        <img src={resolveDownloadUrl(background.image)} alt={background.title} className="absolute inset-0 size-full object-contain" />
        {background.video && !error && <video ref={videoRef} src={resolveDownloadUrl(background.video)} poster={resolveDownloadUrl(background.image)} loop muted playsInline preload="auto" onLoadedData={() => setReady(true)} onError={() => { setError(true); setMessage("El vídeo no pudo cargarse; se muestra la imagen original."); }} className={`absolute inset-0 size-full object-contain ${ready ? "opacity-100" : "opacity-0"}`} />}
        {background.video && !error && <Button variant="secondary" size="icon" className="absolute bottom-3 right-4" onClick={() => { setRequested(!playing); if (!playing) setReduced(false); }} aria-label={playing ? "Pausar animación" : "Reproducir animación"}>{playing ? <Pause /> : <Play />}</Button>}
      </div>
      <footer className="shrink-0 space-y-3 border-t border-border px-5 pb-6 pt-4">
        {!background.video && <p className="text-sm text-muted-foreground">Imagen sin animación disponible.</p>}
        {error && <p role="alert" className="text-sm text-destructive">Vídeo no disponible.</p>}
        {platform === "android" && hasPlugin && background.video && <div className="flex flex-wrap items-center gap-3">
          <select aria-label="Destino del fondo" value={target} onChange={(event) => setTarget(event.target.value as WallpaperTarget)} className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground">
            <option value="home">Pantalla de inicio</option><option value="lock">Pantalla de bloqueo</option><option value="both">Inicio y bloqueo</option>
          </select>
          <Button onClick={activate} disabled={!canActivate || busy}>{busy ? "Abriendo…" : "ACTIVAR FONDO"}</Button>
        </div>}
        <div className="flex flex-wrap gap-2">
          {background.video && <Button variant="outline" onClick={() => exportFile(true)} disabled={busy || error}><Download /> Exportar MP4</Button>}
          <Button variant="outline" onClick={() => exportFile(false)} disabled={busy}><Download /> Guardar imagen</Button>
        </div>
        <p className="text-xs leading-relaxed text-muted-foreground">{platform === "android" && hasPlugin ? "Android decide si permite inicio, bloqueo o ambos. Confirma el destino en su selector." : "La activación animada directa requiere la app Android. En iPhone, un MP4 no es una Live Photo: guarda la imagen y selecciónala en Ajustes → Fondo de pantalla → Añadir nuevo fondo."}</p>
        {message && <p role="status" className="text-sm text-primary">{message}</p>}
      </footer>
    </div>, document.body,
  );
}