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

import blackholeVid from "@/assets/wp-blackhole.mp4.asset.json";
import jellyfishVid from "@/assets/wp-jellyfish.mp4.asset.json";
import cyberpunkVid from "@/assets/wp-cyberpunk.mp4.asset.json";
import whaleVid from "@/assets/wp-whale.mp4.asset.json";
import forestVid from "@/assets/wp-forest.mp4.asset.json";
import nebulaVid from "@/assets/wp-nebula.mp4.asset.json";
import lightningVid from "@/assets/wp-lightning.mp4.asset.json";
import mountainVid from "@/assets/wp-mountain.mp4.asset.json";
import aquariumVid from "@/assets/wp-aquarium.mp4.asset.json";
import lavaVid from "@/assets/wp-lava.mp4.asset.json";

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
  video: string;
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
    subtitle: "Disco de acreción rotando en tiempo real",
    category: "universo",
    src: blackhole,
    video: blackholeVid.url,
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
    subtitle: "Polvo cósmico en deriva volumétrica",
    category: "universo",
    src: nebula,
    video: nebulaVid.url,
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
    subtitle: "Bioluminiscencia pulsando en el abismo",
    category: "oceano",
    src: jellyfish,
    video: jellyfishVid.url,
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
    subtitle: "Tormenta viva en metrópolis cyberpunk",
    category: "cyberpunk",
    src: cyberpunk,
    video: cyberpunkVid.url,
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
    subtitle: "Ballena jorobada nadando en abisal",
    category: "oceano",
    src: whale,
    video: whaleVid.url,
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
    subtitle: "Aurora boreal danzando entre pinos",
    category: "naturaleza",
    src: forest,
    video: forestVid.url,
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
    subtitle: "Rayos eléctricos crujiendo en vivo",
    category: "clima",
    src: lightning,
    video: lightningVid.url,
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
    subtitle: "Niebla rodando sobre montañas nevadas",
    category: "zen",
    src: mountain,
    video: mountainVid.url,
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
    subtitle: "Peces nadando entre rayos de sol",
    category: "oceano",
    src: aquarium,
    video: aquariumVid.url,
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
    subtitle: "Magma fluyendo y brasas ascendiendo",
    category: "naturaleza",
    src: lava,
    video: lavaVid.url,
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
