import { describe, expect, test } from "bun:test";
import { canActivateBackground, shouldPlayBackground } from "./animated-background-policy";

describe("System wallpaper availability", () => {
  test("Android requires an actual clip and native plugin", () => {
    expect(canActivateBackground("android", true, true)).toBe(true);
    expect(canActivateBackground("android", false, true)).toBe(false);
    expect(canActivateBackground("android", true, false)).toBe(false);
  });
  test("iPhone and browser never report direct system activation", () => {
    expect(canActivateBackground("ios", true, true)).toBe(false);
    expect(canActivateBackground("web", true, true)).toBe(false);
  });
  test("Reduced motion stops automatic playback", () => {
    expect(shouldPlayBackground(true, true, true)).toBe(false);
    expect(shouldPlayBackground(true, true, false)).toBe(true);
  });
  test("Background tabs pause playback", () => {
    expect(shouldPlayBackground(true, false, false)).toBe(false);
  });
});