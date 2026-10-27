import orbitImage from "@/assets/premium-orbit-original.png.asset.json";
import robotImage from "@/assets/premium-robot-original.png.asset.json";
import fitnessImage from "@/assets/premium-fitness-original.png.asset.json";
import orbitVideo from "@/assets/premium-orbit.mp4.asset.json";
import robotVideo from "@/assets/premium-robot.mp4.asset.json";

export interface AnimatedBackground {
  id: string;
  title: string;
  image: string;
  video?: string;
  status: string;
}

export const ANIMATED_BACKGROUNDS: AnimatedBackground[] = [
  { id: "premium-orbit", title: "Estación espacial", image: orbitImage.url, video: orbitVideo.url, status: "Animado" },
  { id: "premium-robot", title: "Inteligencia artificial", image: robotImage.url, video: robotVideo.url, status: "Animado" },
  { id: "premium-fitness", title: "Fuerza y equilibrio", image: fitnessImage.url, status: "Imagen · animación no disponible" },
];