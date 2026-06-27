import { useState } from "react";
import {
  getWallpaperDiagnostic,
  type WallpaperDiagnostic,
} from "@/lib/native-wallpaper";

export function WallpaperDiagnosticPanel() {
  const [diagnostic, setDiagnostic] = useState<WallpaperDiagnostic | null>(null);
  const [loading, setLoading] = useState(false);

  async function loadDiagnostic() {
    setLoading(true);
    try {
      setDiagnostic(await getWallpaperDiagnostic());
    } finally {
      setLoading(false);
    }
  }

  const rows: Array<[string, string]> = [
    ["PLUGIN_AVAILABLE", value(diagnostic?.pluginAvailable)],
    ["CURRENT_ACTION", value(diagnostic?.currentAction)],
    ["LAST_STEP", value(diagnostic?.lastStep)],
    ["finalPath", value(diagnostic?.finalPath)],
    ["parentDir", value(diagnostic?.parentDir)],
    ["parentExists", value(diagnostic?.parentExists)],
    ["parentWritable", value(diagnostic?.parentWritable)],
    ["fileExists", value(diagnostic?.fileExists)],
    ["fileSize", value(diagnostic?.fileSize)],
    ["canRead", value(diagnostic?.canRead)],
    ["KEY_VIDEO_PATH", value(diagnostic?.KEY_VIDEO_PATH)],
    ["lastDownloadUrl", value(diagnostic?.lastDownloadUrl)],
    ["lastDownloadBytes", value(diagnostic?.lastDownloadBytes)],
    ["lastError", value(diagnostic?.lastError)],
    ["openPickerCalled", value(diagnostic?.openPickerCalled)],
    ["LAST_EXCEPTION_STACKTRACE", value(diagnostic?.lastExceptionStacktrace)],
  ];

  return (
    <div className="mt-5 rounded-2xl border border-white/10 bg-white/5 p-4">
      <button
        type="button"
        onClick={loadDiagnostic}
        disabled={loading}
        className="w-full rounded-xl border border-electric-blue/40 px-4 py-3 text-xs font-bold uppercase text-electric-blue disabled:opacity-60"
      >
        {loading ? "Leyendo diagnóstico..." : "VER DIAGNÓSTICO"}
      </button>

      {diagnostic && (
        <dl className="mt-4 space-y-2 text-left text-[11px] leading-relaxed">
          {rows.map(([label, rowValue]) => (
            <div key={label} className="grid grid-cols-[130px_1fr] gap-2 border-t border-white/8 pt-2">
              <dt className="font-bold text-electric-blue">{label}</dt>
              <dd className="break-all text-white/70">{rowValue}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}

function value(input: unknown): string {
  if (input === null || input === undefined || input === "") return "null";
  if (typeof input === "boolean") return input ? "true" : "false";
  return String(input);
}