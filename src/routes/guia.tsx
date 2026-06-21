import { createFileRoute, Link } from "@tanstack/react-router";
import {
  ArrowLeft,
  Github,
  Download,
  Smartphone,
  Settings,
  CheckCircle2,
  AlertCircle,
  Copy,
} from "lucide-react";
import { useState } from "react";

export const Route = createFileRoute("/guia")({
  head: () => ({
    meta: [
      { title: "Guía de instalación — AetherX" },
      {
        name: "description",
        content:
          "Paso a paso para conectar AetherX a GitHub y descargar el APK en Android.",
      },
    ],
  }),
  component: GuiaPage,
});

const steps = [
  {
    number: 1,
    title: "Abre el menú GitHub en Lovable",
    description:
      "En el editor de Lovable, toca el botón + (abajo a la izquierda), selecciona GitHub y luego Conectar proyecto.",
    icon: Github,
    tip: "Asegúrate de tener sesión iniciada en GitHub en tu navegador.",
  },
  {
    number: 2,
    title: "Autoriza y crea el repositorio",
    description:
      "GitHub te pedirá autorizar a la app de Lovable. Acepta, elige tu cuenta y pulsa Crear repositorio.",
    icon: CheckCircle2,
    tip: "El nombre del repo se genera automático; puedes dejarlo así.",
  },
  {
    number: 3,
    title: "Espera la compilación automática",
    description:
      "En cuanto el repo se cree, Lovable sube el código y GitHub inicia la compilación. Ve a la pestaña Actions de tu repo en GitHub.",
    icon: Settings,
    tip: "La compilación tarda entre 5 y 10 minutos. No cierres la pestaña.",
  },
  {
    number: 4,
    title: "Descarga el APK",
    description:
      "Cuando el workflow Build Android APK termine con una palomita verde, abre el run, baja a Artifacts y descarga app-debug.apk.",
    icon: Download,
    tip: "El archivo se descarga como ZIP: descomprímelo para obtener el .apk.",
  },
  {
    number: 5,
    title: "Instala en tu Android",
    description:
      "Pasa el APK a tu móvil (Gmail, Drive, Telegram, cable USB…). Ábrelo y pulsa Instalar. Si pide permiso, activa Origen desconocido para este archivo.",
    icon: Smartphone,
    tip: "En Android 13+ ve a Ajustes > Aplicaciones > Acceso especial > Instalar apps desconocidas y permite tu navegador o gestor de archivos.",
  },
];

function GuiaPage() {
  const [copied, setCopied] = useState(false);

  function copyRepoUrl() {
    const url = "https://github.com/";
    navigator.clipboard.writeText(url).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  return (
    <div className="relative min-h-screen">
      {/* Decorative glows */}
      <div className="pointer-events-none absolute -top-32 -right-32 size-72 rounded-full bg-electric-blue/20 blur-[100px]" />
      <div className="pointer-events-none absolute top-96 -left-32 size-72 rounded-full bg-galaxy-purple/20 blur-[100px]" />

      {/* Header */}
      <header className="glass-nav sticky top-0 z-40 flex items-center gap-3 px-6 py-5">
        <Link
          to="/"
          className="flex size-10 items-center justify-center rounded-full glass-card"
          aria-label="Volver al inicio"
        >
          <ArrowLeft className="size-5 text-white" />
        </Link>
        <div>
          <h1 className="text-xl font-bold uppercase italic tracking-tight text-display">
            Guía <span className="text-electric-blue">rápida</span>
          </h1>
          <p className="text-[10px] font-semibold uppercase tracking-[0.3em] text-white/40">
            Instalación del APK en Android
          </p>
        </div>
      </header>

      <main className="px-6 py-8">
        {/* Intro */}
        <div className="mb-8 text-center">
          <p className="text-sm leading-relaxed text-white/70">
            Sigue estos 5 pasos para conectar tu proyecto a GitHub y obtener el
            APK firmado del Live Wallpaper.
          </p>
          <button
            type="button"
            onClick={copyRepoUrl}
            className="mt-4 inline-flex items-center gap-2 rounded-full border border-white/10 px-4 py-2 text-xs font-semibold text-white/70 transition active:scale-95"
          >
            <Copy className="size-3.5" />
            {copied ? "¡Copiado!" : "Copiar enlace base de GitHub"}
          </button>
        </div>

        {/* Steps */}
        <div className="relative space-y-6">
          {/* Vertical line */}
          <div className="absolute left-7 top-10 bottom-10 w-px bg-gradient-to-b from-electric-blue/40 via-galaxy-purple/40 to-transparent" />

          {steps.map((step) => {
            const Icon = step.icon;
            return (
              <div key={step.number} className="relative pl-16">
                {/* Step number circle */}
                <div className="absolute left-0 top-0 flex size-14 items-center justify-center rounded-2xl bg-gradient-to-tr from-electric-blue to-galaxy-purple shadow-lg shadow-electric-blue/20">
                  <Icon className="size-6 text-white" />
                </div>

                <div className="glass-card rounded-2xl p-5">
                  <div className="mb-2 flex items-center gap-2">
                    <span className="text-[10px] font-bold uppercase tracking-[0.25em] text-electric-blue">
                      Paso {step.number}
                    </span>
                  </div>
                  <h3 className="text-base font-bold text-display">
                    {step.title}
                  </h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-white/70">
                    {step.description}
                  </p>
                  <div className="mt-3 flex items-start gap-2 rounded-xl border border-electric-blue/20 bg-electric-blue/10 px-3 py-2.5">
                    <AlertCircle className="mt-0.5 size-3.5 shrink-0 text-electric-blue" />
                    <p className="text-xs leading-snug text-electric-blue/90">
                      {step.tip}
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Video tip */}
        <div className="mt-10 glass-card rounded-2xl p-5">
          <h4 className="text-sm font-bold text-display">
            ¿Necesitas un vídeo de ejemplo?
          </h4>
          <p className="mt-1.5 text-xs leading-relaxed text-white/60">
            La primera vez que conectes GitHub verás una ventana emergente de
            autorización. Acepta todos los permisos que pida Lovable para poder
            subir el código y ejecutar Actions.
          </p>
        </div>

        {/* CTA */}
        <div className="mt-8 text-center">
          <Link
            to="/"
            className="inline-flex items-center gap-2 rounded-2xl bg-gradient-to-r from-electric-blue to-galaxy-purple px-6 py-3 text-xs font-bold uppercase tracking-[0.18em] text-white shadow-lg shadow-electric-blue/30"
          >
            <ArrowLeft className="size-4" />
            Volver a AetherX
          </Link>
        </div>
      </main>
    </div>
  );
}
