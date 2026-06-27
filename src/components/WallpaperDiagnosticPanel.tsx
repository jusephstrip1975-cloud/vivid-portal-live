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

  const buildMatch =
    diagnostic?.buildId && diagnostic?.jsBuildId
      ? diagnostic.buildId === diagnostic.jsBuildId
      : undefined;

  const rows: Array<[string, string]> = [
    // Build identity (CRITICAL — proves which APK is installed)
    ["APP_VERSION", value(diagnostic?.appVersion) + (diagnostic?.versionCode ? ` (code ${diagnostic.versionCode})` : "")],
    ["BUILD_ID (native)", value(diagnostic?.buildId)],
    ["BUILD_ID (js)", value(diagnostic?.jsBuildId)],
    ["BUILD_ID_MATCH", buildMatch === undefined ? "—" : buildMatch ? "true" : "FALSE (mismatched bundle!)"],
    ["BUILD_TIMESTAMP", value(diagnostic?.buildTimestamp)],
    ["BUILD_VERSION", value(diagnostic?.buildVersion)],
    ["PACKAGE_NAME", value(diagnostic?.packageName)],
    // Signature
    ["APK_SIGNATURE_SHA256", value(diagnostic?.apkSignatureSha256)],
    ["SIGNATURE_VALID", value(diagnostic?.signatureValid)],
    ["INSTALL_SOURCE", value(diagnostic?.installSource)],
    // Runtime
    ["PLUGIN_LOADED", value(diagnostic?.pluginAvailable)],
    ["SERVICE_RUNNING", value(diagnostic?.serviceRunning)],
    ["CURRENT_ACTION", value(diagnostic?.currentAction)],
    ["LAST_STEP", value(diagnostic?.lastStep)],
    // Video file
    ["VIDEO_PATH", value(diagnostic?.KEY_VIDEO_PATH ?? diagnostic?.finalPath)],
    ["VIDEO_EXISTS", value(diagnostic?.fileExists)],
    ["VIDEO_SIZE", value(diagnostic?.fileSize)],
    ["VIDEO_CAN_READ", value(diagnostic?.canRead)],
    ["AUTO_RECOVERED", value(diagnostic?.autoRecovered)],
    ["parentDir", value(diagnostic?.parentDir)],
    ["parentExists", value(diagnostic?.parentExists)],
    ["parentWritable", value(diagnostic?.parentWritable)],
    ["lastDownloadUrl", value(diagnostic?.lastDownloadUrl)],
    ["lastDownloadBytes", value(diagnostic?.lastDownloadBytes)],
    // Probe / transcode
    ["SOURCE_CODEC", value(diagnostic?.sourceCodec)],
    ["SOURCE_WIDTHxHEIGHT", diagnostic?.sourceWidth ? `${diagnostic.sourceWidth}x${diagnostic.sourceHeight}` : "—"],
    ["SOURCE_FPS", value(diagnostic?.sourceFps)],
    ["SOURCE_BITRATE", value(diagnostic?.sourceBitrate)],
    ["SOURCE_HAS_AUDIO", value(diagnostic?.sourceHasAudio)],
    ["REAL_CODEC", value(diagnostic?.realCodec)],
    ["REAL_WIDTHxHEIGHT", diagnostic?.realWidth ? `${diagnostic.realWidth}x${diagnostic.realHeight}` : "—"],
    ["REAL_FPS", value(diagnostic?.realFps)],
    ["REAL_BITRATE", value(diagnostic?.realBitrate)],
    ["REAL_HAS_AUDIO", value(diagnostic?.realHasAudio)],
    ["TRANSCODED", value(diagnostic?.transcoded)],
    ["LAST_TRANSCODE_ERROR", value(diagnostic?.lastTranscodeError)],
    ["FFMPEG_COMMAND", value(diagnostic?.ffmpegCommand)],
    ["FFMPEG_EXIT_CODE", value(diagnostic?.ffmpegExitCode)],
    ["FFPROBE_WIDTH", value(diagnostic?.ffprobeWidth)],
    ["FFPROBE_HEIGHT", value(diagnostic?.ffprobeHeight)],
    ["FFPROBE_ROTATION", value(diagnostic?.ffprobeRotation)],
    // Errors
    ["LAST_NATIVE_EXCEPTION", value(diagnostic?.lastExceptionStacktrace)],
    ["LAST_JS_EXCEPTION", value(diagnostic?.lastError)],
    ["LAST_SERVICE_ERROR", value(diagnostic?.lastServiceError)],
    ["openPickerCalled", value(diagnostic?.openPickerCalled)],
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
            <div key={label} className="grid grid-cols-[160px_1fr] gap-2 border-t border-white/8 pt-2">
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
  if (input === null || input === undefined || input === "") return "—";
  if (typeof input === "boolean") return input ? "true" : "false";
  return String(input);
}
