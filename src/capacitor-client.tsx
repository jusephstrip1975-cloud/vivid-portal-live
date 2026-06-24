import { StrictMode, startTransition } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "@tanstack/react-router";

import "./styles.css";
import { getRouter } from "./router";

const bootLog = (...args: unknown[]) => console.info("[AetherX Android SPA]", ...args);

declare global {
  interface Window {
    __aetherxShowBootError?: (
      kind: string,
      value: unknown,
      source?: string,
      line?: number,
      column?: number,
    ) => void;
  }
}

window.addEventListener("error", (event) => {
  console.error("[AetherX Android SPA] uncaught error", event.error ?? event.message);
  document.documentElement.dataset.aetherxBootError = "true";
  window.__aetherxShowBootError?.(
    "React startup error",
    event.error ?? event.message,
    event.filename,
    event.lineno,
    event.colno,
  );
});

window.addEventListener("unhandledrejection", (event) => {
  console.error("[AetherX Android SPA] unhandled rejection", event.reason);
  document.documentElement.dataset.aetherxBootError = "true";
  window.__aetherxShowBootError?.("React startup rejection", event.reason);
});

const rootElement = document.getElementById("root");

if (!rootElement) {
  throw new Error("AetherX Capacitor root element not found");
}

if (window.location.pathname.endsWith("/index.html")) {
  window.history.replaceState(null, document.title, "./#/");
}

bootLog("mounting local hash router", window.location.href);

try {
  const router = getRouter({ history: "hash" });

  startTransition(() => {
    createRoot(rootElement).render(
      <StrictMode>
        <RouterProvider router={router} />
      </StrictMode>,
    );
    document.documentElement.dataset.aetherxBooted = "true";
    bootLog("mounted", window.location.href);
  });
} catch (error) {
  console.error("[AetherX Android SPA] fatal mount error", error);
  document.documentElement.dataset.aetherxBootError = "true";
  window.__aetherxShowBootError?.("Fatal React mount error", error);
}