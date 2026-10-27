import { createFileRoute } from "@tanstack/react-router";
import { CATEGORIES, WALLPAPERS } from "@/lib/wallpapers";

const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Accept, Content-Type",
  "Cache-Control": "no-store",
};

export const Route = createFileRoute("/api/public/catalog")({
  server: {
    handlers: {
      OPTIONS: () => new Response(null, { status: 204, headers }),
      GET: ({ request }) => {
        // This endpoint exposes only the public media catalog, never user data.
        const requestUrl = new URL(request.url);
        const origin = requestUrl.hostname === "localhost" || requestUrl.hostname === "127.0.0.1"
          ? "https://aetherx.org" : requestUrl.origin;
        const absolute = (url: string) => new URL(url, `${origin}/`).href;
        return Response.json({
          schemaVersion: 1,
          categories: CATEGORIES,
          wallpapers: WALLPAPERS.map((wp) => ({ ...wp, src: absolute(wp.src), video: absolute(wp.video) })),
        }, { headers });
      },
    },
  },
});