import { useAppState } from "@/lib/app-state";
import { getWallpaper } from "@/lib/wallpapers";

/**
 * Subtle ambient halo that reflects the currently "applied" wallpaper
 * across every screen of the app. Persists until the user removes the
 * wallpaper (or uninstalls / clears the app data).
 */
export function AppliedHalo() {
  const { appliedId } = useAppState();
  const wp = appliedId ? getWallpaper(appliedId) : null;
  if (!wp) return null;

  return (
    <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <img
        src={wp.src}
        alt=""
        aria-hidden="true"
        className="absolute inset-0 size-full object-cover opacity-25 blur-3xl scale-110 animate-drift"
      />
      <div className="absolute inset-0 bg-space-black/70" />
    </div>
  );
}
