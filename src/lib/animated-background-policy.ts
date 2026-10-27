export type BackgroundPlatform = "android" | "ios" | "web";

export function canActivateBackground(platform: BackgroundPlatform, hasPlugin: boolean, hasVideo: boolean) {
  return platform === "android" && hasPlugin && hasVideo;
}

export function shouldPlayBackground(requested: boolean, visible: boolean, reducedMotion: boolean) {
  return requested && visible && !reducedMotion;
}