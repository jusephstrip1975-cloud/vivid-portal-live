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
import planets from "@/assets/wp-planets.jpg";
import galaxy from "@/assets/wp-galaxy.jpg";
import snow from "@/assets/wp-snow.jpg";
import waves from "@/assets/wp-waves.jpg";
import shark from "@/assets/wp-shark.jpg";

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
import planetsVid from "@/assets/wp-planets.mp4.asset.json";
import galaxyVid from "@/assets/wp-galaxy.mp4.asset.json";
import snowVid from "@/assets/wp-snow.mp4.asset.json";
import wavesVid from "@/assets/wp-waves.mp4.asset.json";
import sharkVid from "@/assets/wp-shark.mp4.asset.json";

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
  {
    id: "ringed-giant",
    title: "Gigante Anillado",
    subtitle: "Planeta gaseoso orbitado por dos lunas",
    category: "universo",
    src: planets,
    video: planetsVid.url,
    resolution: "4K",
    sizeMb: 17.4,
    fps: 60,
    premium: true,
    sound: false,
    accent: "rgba(168, 85, 247, 0.45)",
  },
  {
    id: "spiral-core",
    title: "Núcleo Espiral",
    subtitle: "Galaxia girando en magenta y azul",
    category: "universo",
    src: galaxy,
    video: galaxyVid.url,
    resolution: "8K",
    sizeMb: 22.9,
    fps: 60,
    premium: true,
    sound: false,
    accent: "rgba(236, 72, 153, 0.4)",
  },
  {
    id: "moonlit-snow",
    title: "Nieve a la Luna",
    subtitle: "Copos cayendo en bosque silencioso",
    category: "clima",
    src: snow,
    video: snowVid.url,
    resolution: "4K",
    sizeMb: 10.3,
    fps: 30,
    premium: false,
    sound: true,
    accent: "rgba(186, 230, 253, 0.4)",
  },
  {
    id: "barrel-wave",
    title: "Tubo Esmeralda",
    subtitle: "Ola perfecta envolviendo el horizonte",
    category: "oceano",
    src: waves,
    video: wavesVid.url,
    resolution: "4K",
    sizeMb: 13.7,
    fps: 60,
    premium: false,
    sound: true,
    accent: "rgba(45, 212, 191, 0.45)",
  },
  {
    id: "apex-predator",
    title: "Gran Blanco",
    subtitle: "Tiburón emergiendo de la penumbra azul",
    category: "oceano",
    src: shark,
    video: sharkVid.url,
    resolution: "4K",
    sizeMb: 16.0,
    fps: 30,
    premium: true,
    sound: true,
    accent: "rgba(30, 64, 175, 0.5)",
  },
];

export function getWallpaper(id: string): Wallpaper | undefined {
  return WALLPAPERS.find((w) => w.id === id);
}
