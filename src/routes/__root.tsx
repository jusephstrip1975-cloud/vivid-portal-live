import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  Outlet,
  Link,
  createRootRouteWithContext,
  useRouter,
  HeadContent,
  Scripts,
} from "@tanstack/react-router";
import { useEffect, type ReactNode } from "react";

import appCss from "../styles.css?url";
import { reportLovableError } from "../lib/lovable-error-reporting";
import { AppStateProvider } from "@/lib/app-state";
import { BottomNav } from "@/components/BottomNav";
import { AppliedHalo } from "@/components/AppliedHalo";
import { DownloadAppModal } from "@/components/DownloadAppModal";

function NotFoundComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center px-6 ambient-glow">
      <div className="max-w-sm text-center">
        <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
          Señal perdida
        </p>
        <h1 className="mt-3 text-6xl font-bold text-display text-ice-white">404</h1>
        <p className="mt-3 text-sm text-white/60">
          Esa órbita no existe. Vuelve al universo principal.
        </p>
        <div className="mt-6">
          <Link
            to="/"
            className="inline-flex items-center justify-center rounded-full bg-ice-white px-6 py-3 text-xs font-bold uppercase tracking-[0.2em] text-space-black hover:bg-electric-blue hover:text-white transition"
          >
            Volver al inicio
          </Link>
        </div>
      </div>
    </div>
  );
}

function ErrorComponent({ error, reset }: { error: Error; reset: () => void }) {
  console.error(error);
  const router = useRouter();
  useEffect(() => {
    reportLovableError(error, { boundary: "tanstack_root_error_component" });
  }, [error]);

  return (
    <div className="flex min-h-screen items-center justify-center px-6 ambient-glow">
      <div className="glass-card max-w-sm rounded-3xl p-8 text-center">
        <h1 className="text-xl font-bold text-display">Algo se distorsionó</h1>
        <p className="mt-2 text-sm text-white/60">
          La señal cinematográfica se interrumpió. Vuelve a intentarlo.
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <button
            onClick={() => {
              router.invalidate();
              reset();
            }}
            className="rounded-full bg-electric-blue px-5 py-2.5 text-xs font-bold uppercase tracking-[0.2em] text-space-black hover:bg-ocean-cyan transition"
          >
            Reintentar
          </button>
          <a
            href="/"
            className="rounded-full border border-white/15 px-5 py-2.5 text-xs font-bold uppercase tracking-[0.2em] text-white hover:bg-white/5 transition"
          >
            Inicio
          </a>
        </div>
      </div>
    </div>
  );
}

export const Route = createRootRouteWithContext<{ queryClient: QueryClient }>()({
  head: () => ({
    meta: [
      { charSet: "utf-8" },
      {
        name: "viewport",
        content:
          "width=device-width, initial-scale=1, viewport-fit=cover, maximum-scale=1",
      },
      { name: "theme-color", content: "#02040a" },
      { name: "apple-mobile-web-app-capable", content: "yes" },
      { name: "apple-mobile-web-app-status-bar-style", content: "black-translucent" },
      { name: "apple-mobile-web-app-title", content: "AETHERX" },
      { name: "mobile-web-app-capable", content: "yes" },
      { title: "AETHERX — Fondos animados premium en 4K/8K" },
      {
        name: "description",
        content:
          "AETHERX: fondos de pantalla animados ultra realistas en movimiento. Universo, océano, cyberpunk y más, en 4K cinematográfico. Descarga gratuita, solo pagas por fondos 3D exclusivos.",
      },
      { name: "author", content: "AETHERX" },
      { property: "og:title", content: "AETHERX — Fondos animados premium en 4K/8K" },
      {
        property: "og:description",
        content:
          "Convierte tu pantalla en una ventana viva: universo, océano, ballenas, tormentas y ciudades cyberpunk en 4K. Descarga gratuita, solo pagas por fondos 3D exclusivos.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
      { name: "twitter:title", content: "AETHERX — Fondos animados premium en 4K/8K" },
      { name: "twitter:description", content: "Fondos animados ultra realistas en 4K/8K. Universo, océano, cyberpunk y más. Descarga gratuita, solo pagas por fondos 3D exclusivos." },
      { property: "og:image", content: "https://pub-bb2e103a32db4e198524a2e9ed8f35b4.r2.dev/ac492e86-ad5b-4c95-9360-54ee34149006/id-preview-94602ddc--86067037-aec8-403d-b7be-5af9e39ce44c.lovable.app-1781900710226.png" },
      { name: "twitter:image", content: "https://pub-bb2e103a32db4e198524a2e9ed8f35b4.r2.dev/ac492e86-ad5b-4c95-9360-54ee34149006/id-preview-94602ddc--86067037-aec8-403d-b7be-5af9e39ce44c.lovable.app-1781900710226.png" },
    ],
    links: [
      { rel: "stylesheet", href: appCss },
      { rel: "manifest", href: "/manifest.webmanifest" },
      { rel: "apple-touch-icon", href: "/icons/apple-touch-icon.png" },
      { rel: "icon", type: "image/png", sizes: "512x512", href: "/icons/icon-512.png" },
      { rel: "icon", type: "image/png", sizes: "192x192", href: "/icons/icon-192.png" },
      { rel: "preconnect", href: "https://fonts.googleapis.com" },
      {
        rel: "preconnect",
        href: "https://fonts.gstatic.com",
        crossOrigin: "anonymous",
      },
      {
        rel: "stylesheet",
        href: "https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=Space+Grotesk:wght@400;500;600;700&display=swap",
      },
    ],
  }),
  shellComponent: RootShell,
  component: RootComponent,
  notFoundComponent: NotFoundComponent,
  errorComponent: ErrorComponent,
});

function RootShell({ children }: { children: ReactNode }) {
  return (
    <html lang="es">
      <head>
        <HeadContent />
      </head>
      <body>
        {children}
        <Scripts />
      </body>
    </html>
  );
}

function RootComponent() {
  const { queryClient } = Route.useRouteContext();

  return (
    <QueryClientProvider client={queryClient}>
      <AppStateProvider>
        <div className="relative mx-auto flex min-h-screen w-full max-w-md flex-col overflow-x-hidden bg-space-black">
          <AppliedHalo />
          <main className="flex-1 pb-32">
            <Outlet />
          </main>
          <BottomNav />
          <DownloadAppModal />
        </div>
      </AppStateProvider>
    </QueryClientProvider>
  );
}
