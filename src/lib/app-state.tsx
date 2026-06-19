import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

const FAV_KEY = "aetherx.favorites.v1";
const APPLIED_KEY = "aetherx.applied.v1";

type State = {
  favorites: string[];
  appliedId: string | null;
  toggleFavorite: (id: string) => void;
  isFavorite: (id: string) => boolean;
  apply: (id: string) => void;
  unapply: () => void;
};

const Ctx = createContext<State | null>(null);

function read<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  try {
    const v = window.localStorage.getItem(key);
    return v ? (JSON.parse(v) as T) : fallback;
  } catch {
    return fallback;
  }
}

export function AppStateProvider({ children }: { children: ReactNode }) {
  const [favorites, setFavorites] = useState<string[]>([]);
  const [appliedId, setAppliedId] = useState<string | null>(null);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setFavorites(read<string[]>(FAV_KEY, []));
    setAppliedId(read<string | null>(APPLIED_KEY, null));
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (hydrated) window.localStorage.setItem(FAV_KEY, JSON.stringify(favorites));
  }, [favorites, hydrated]);

  useEffect(() => {
    if (hydrated) window.localStorage.setItem(APPLIED_KEY, JSON.stringify(appliedId));
  }, [appliedId, hydrated]);

  const toggleFavorite = useCallback((id: string) => {
    setFavorites((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  }, []);

  const isFavorite = useCallback(
    (id: string) => favorites.includes(id),
    [favorites],
  );

  const apply = useCallback((id: string) => setAppliedId(id), []);
  const unapply = useCallback(() => setAppliedId(null), []);

  const value = useMemo<State>(
    () => ({ favorites, appliedId, toggleFavorite, isFavorite, apply, unapply }),
    [favorites, appliedId, toggleFavorite, isFavorite, apply, unapply],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAppState() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useAppState must be used inside AppStateProvider");
  return ctx;
}
