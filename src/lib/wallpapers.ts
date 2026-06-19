import blackhole from "@/assets/wp-blackhole.jpg";
import jellyfish from "@/assets/wp-jellyfish.jpg";
import cyberpunk from "@/assets/wp-cyberpunk.jpg";
import whale from "@/assets/wp-whale.jpg";
import forest from "@/assets/wp-forest.jpg";
import nebula from "@/assets/wp-nebula.jpg";
import lightning from "@/assets/wp-lightning.jpg";
import mountain from "@/assets/wp-mountain.jpg";
import aquarium from "@/assets/wp-aquarium.jpg";
import lava from "@/assets/wp-lava.jpg";

export type Category =
  | "universo"
  | "oceano"
  | "naturaleza"
  | "clima"
  | "cyberpunk"
  | "zen";

export interface Wallpaper {
  id: string;
  title: string;
  subtitle: string;
  category: Category;
  src: string;
  resolution: "4K" | "8K";
  sizeMb: number;
  fps: 30 | 60;
  premium: boolean;
  sound: boolean;
  accent: string;
}

export const CATEGORIES: { id: Category | "todos"; label: string }[] = [
  { id: "todos", label: "Todos" },
  { id: "universo", label: "Universo" },
  { id: "oceano", label: "Océano" },
  { id: "naturaleza", label: "Naturaleza" },
  { id: "clima", label: "Clima" },
  { id: "cyberpunk", label: "Cyberpunk" },
  { id: "zen", label: "Zen" },
];

export const WALLPAPERS: Wallpaper[] = [
  {
    id: "event-horizon",
    title: "Horizonte de Eventos",
    subtitle: "Parallax gravitacional interactivo",
    category: "universo",
    src: blackhole,
    resolution: "4K",
    sizeMb: 18.2,
    fps: 60,
    premium: true,
    sound: true,
    accent: "rgba(139, 92, 246, 0.4)",
  },
  {
    id: "nebula-drift",
    title: "Nebulosa Cinemática",
    subtitle: "Nubes de polvo cósmico en deriva",
    category: "universo",
    src: nebula,
    resolution: "8K",
    sizeMb: 24.6,
    fps: 60,
    premium: true,
    sound: false,
    accent: "rgba(168, 85, 247, 0.45)",
  },
  {
    id: "neon-medusa",
    title: "Medusa Neón",
    subtitle: "Bioluminiscencia en el abismo",
    category: "oceano",
    src: jellyfish,
    resolution: "4K",
    sizeMb: 12.1,
    fps: 60,
    premium: false,
    sound: true,
    accent: "rgba(34, 211, 238, 0.4)",
  },
  {
    id: "neon-torrent",
    title: "Lluvia Neón",
    subtitle: "Tormenta en metrópolis cyberpunk",
    category: "cyberpunk",
    src: cyberpunk,
    resolution: "4K",
    sizeMb: 16.4,
    fps: 60,
    premium: true,
    sound: true,
    accent: "rgba(236, 72, 153, 0.4)",
  },
  {
    id: "leviathan",
    title: "Leviatán Azul",
    subtitle: "Ballena jorobada en aguas profundas",
    category: "oceano",
    src: whale,
    resolution: "4K",
    sizeMb: 14.8,
    fps: 30,
    premium: false,
    sound: true,
    accent: "rgba(14, 165, 233, 0.4)",
  },
  {
    id: "aurora-pines",
    title: "Bosque Onírico",
    subtitle: "Aurora boreal entre pinos brumosos",
    category: "naturaleza",
    src: forest,
    resolution: "4K",
    sizeMb: 11.5,
    fps: 30,
    premium: false,
    sound: true,
    accent: "rgba(34, 211, 238, 0.35)",
  },
  {
    id: "violet-storm",
    title: "Tormenta Violeta",
    subtitle: "Rayos eléctricos cinematográficos",
    category: "clima",
    src: lightning,
    resolution: "4K",
    sizeMb: 9.8,
    fps: 60,
    premium: true,
    sound: true,
    accent: "rgba(139, 92, 246, 0.5)",
  },
  {
    id: "fog-peaks",
    title: "Cumbres en Niebla",
    subtitle: "Amanecer sobre montañas nevadas",
    category: "zen",
    src: mountain,
    resolution: "4K",
    sizeMb: 8.6,
    fps: 30,
    premium: false,
    sound: false,
    accent: "rgba(248, 250, 252, 0.3)",
  },
  {
    id: "coral-bay",
    title: "Arrecife Tropical",
    subtitle: "Peces exóticos en agua turquesa",
    category: "oceano",
    src: aquarium,
    resolution: "4K",
    sizeMb: 13.2,
    fps: 60,
    premium: false,
    sound: true,
    accent: "rgba(34, 211, 238, 0.45)",
  },
  {
    id: "lava-river",
    title: "Río de Lava",
    subtitle: "Magma fluyendo en la noche",
    category: "naturaleza",
    src: lava,
    resolution: "4K",
    sizeMb: 15.1,
    fps: 30,
    premium: true,
    sound: true,
    accent: "rgba(249, 115, 22, 0.4)",
  },
];

export function getWallpaper(id: string): Wallpaper | undefined {
  return WALLPAPERS.find((w) => w.id === id);
}
