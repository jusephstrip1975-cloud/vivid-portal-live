import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { Capacitor } from "@capacitor/core";
import { CATEGORIES, WALLPAPERS, type Wallpaper, type Category } from "@/lib/wallpapers";
import { catalogSchema, CATALOG_CACHE_KEY, type RemoteCatalog } from "@/lib/catalog-policy";

const fallback = { wallpapers: WALLPAPERS, categories: CATEGORIES };
type Catalog = typeof fallback;
const CatalogContext = createContext<Catalog & { updating: boolean; message: string | null; refresh: () => Promise<void> }>({
  ...fallback, updating: false, message: null, refresh: async () => {},
});

function toCatalog(data: RemoteCatalog): Catalog {
  return { wallpapers: data.wallpapers as Wallpaper[], categories: data.categories as { id: Category | "todos"; label: string }[] };
}

export async function fetchPublishedCatalog(): Promise<RemoteCatalog> {
  const origin = typeof window === "undefined" || Capacitor.isNativePlatform() ? "https://aetherx.org" : "";
  const response = await fetch(`${origin}/api/public/catalog?refresh=${Date.now()}`, {
    cache: "no-store", credentials: "omit", signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error("Catalog unavailable");
  return catalogSchema.parse(await response.json());
}

export function CatalogProvider({ children }: { children: ReactNode }) {
  const [catalog, setCatalog] = useState<Catalog>(fallback);
  const [updating, setUpdating] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const inFlight = useRef(false);
  const refresh = useCallback(async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setUpdating(true);
    setMessage(null);
    try {
      const data = await fetchPublishedCatalog();
      setCatalog(toCatalog(data));
      try { localStorage.setItem(CATALOG_CACHE_KEY, JSON.stringify(data)); } catch { /* Storage may be full. */ }
      setMessage("Fondos actualizados");
    } catch {
      setMessage("No se pudo actualizar. Conservamos tus fondos; vuelve a intentarlo con conexión.");
    } finally {
      inFlight.current = false;
      setUpdating(false);
    }
  }, []);

  useEffect(() => {
    try {
      const cached = localStorage.getItem(CATALOG_CACHE_KEY);
      if (cached) setCatalog(toCatalog(catalogSchema.parse(JSON.parse(cached))));
    } catch { /* Ignore invalid cached catalogs. */ }
    void refresh();
    const onVisible = () => { if (document.visibilityState === "visible") void refresh(); };
    const onOnline = () => void refresh();
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("online", onOnline);
    return () => {
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("online", onOnline);
    };
  }, [refresh]);

  return <CatalogContext.Provider value={{ ...catalog, updating, message, refresh }}>{children}</CatalogContext.Provider>;
}

export const useCatalog = () => useContext(CatalogContext);