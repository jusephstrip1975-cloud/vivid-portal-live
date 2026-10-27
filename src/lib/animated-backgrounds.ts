import orbitImage from "@/assets/premium-orbit-original.png.asset.json";
import robotImage from "@/assets/premium-robot-original.png.asset.json";
import fitnessImage from "@/assets/premium-fitness-poster.png.asset.json";
import orbitVideo from "@/assets/premium-orbit-android.mp4.asset.json";
import robotVideo from "@/assets/premium-robot-android.mp4.asset.json";
import fitnessVideo from "@/assets/premium-fitness-android.mp4.asset.json";

export interface AnimatedBackground {
  id: string;
  title: string;
  image: string;
  video?: string;
  status: string;
}

export const ANIMATED_BACKGROUNDS: AnimatedBackground[] = [
  { id: "premium-orbit", title: "Estación espacial", image: `https://aetherx.org${orbitImage.url}`, video: `https://aetherx.org${orbitVideo.url}`, status: "Animado" },
  { id: "premium-robot", title: "Inteligencia artificial", image: `https://aetherx.org${robotImage.url}`, video: `https://aetherx.org${robotVideo.url}`, status: "Animado" },
  { id: "premium-fitness", title: "Fuerza y equilibrio", image: `https://aetherx.org${fitnessImage.url}`, video: `https://aetherx.org${fitnessVideo.url}`, status: "Animado" },
];