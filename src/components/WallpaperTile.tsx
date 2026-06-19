import { Link } from "@tanstack/react-router";
import { Sparkles } from "lucide-react";
import type { Wallpaper } from "@/lib/wallpapers";
import { LiveMedia } from "@/components/LiveMedia";

interface Props {
  wp: Wallpaper;
  className?: string;
  index?: number;
}

export function WallpaperTile({ wp, className = "", index = 0 }: Props) {
  return (
    <Link
      to="/wallpaper/$id"
      params={{ id: wp.id }}
      className={`group relative block aspect-[9/16] overflow-hidden rounded-2xl outline outline-1 -outline-offset-1 outline-white/10 ${className}`}
      style={{ animationDelay: `${index * 60}ms` }}
    >
      <LiveMedia
        src={wp.video}
        poster={wp.src}
        alt={wp.title}
        className="size-full object-cover transition duration-[1200ms] group-hover:scale-105"
      />
      <div className="absolute inset-0 bg-gradient-to-t from-space-black/85 via-space-black/10 to-transparent" />


      {/* Live indicator */}
      <span className="absolute right-2 top-2 flex items-center gap-1 rounded-full bg-space-black/60 px-2 py-1 text-[8px] font-bold uppercase tracking-[0.18em] text-electric-blue backdrop-blur-md">
        <span className="size-1.5 rounded-full bg-electric-blue animate-shimmer" />
        LIVE
      </span>

      {wp.premium && (
        <span className="absolute left-2 top-2 flex items-center gap-1 rounded-full bg-gradient-to-r from-galaxy-purple/80 to-electric-blue/80 px-2 py-1 text-[8px] font-bold uppercase tracking-[0.18em] text-white backdrop-blur-md">
          <Sparkles className="size-2.5" />
          Pro
        </span>
      )}

      <div className="absolute inset-x-3 bottom-3">
        <p className="text-sm font-semibold leading-tight text-ice-white text-display">
          {wp.title}
        </p>
        <p className="mt-0.5 text-[10px] uppercase tracking-[0.18em] text-white/50">
          {wp.resolution} · {wp.fps}fps
        </p>
      </div>
    </Link>
  );
}
