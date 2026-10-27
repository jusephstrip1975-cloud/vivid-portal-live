import { describe, expect, test } from "bun:test";
import { catalogSchema } from "./catalog-policy";

const wallpaper = { id: "new-fitness", title: "Fitness", subtitle: "", category: "fitness", src: "https://aetherx.org/poster.jpg", video: "https://aetherx.org/fitness.mp4", resolution: "4K", sizeMb: 2, fps: 30, premium: true, sound: false, accent: "gold" };
const catalog = { schemaVersion: 1, categories: [{ id: "fitness", label: "Fitness" }], wallpapers: [wallpaper] };
describe("published catalog update policy", () => {
  test("accepts new backgrounds for the existing picker flow", () => {
    const parsed = catalogSchema.parse(catalog);
    expect(parsed.wallpapers[0].id).toBe("new-fitness");
    expect(parsed.wallpapers[0].video).toBe("https://aetherx.org/fitness.mp4");
  });
  test("rejects empty updates so offline backgrounds are preserved", () => {
    expect(catalogSchema.safeParse({ ...catalog, wallpapers: [] }).success).toBe(false);
  });
  test("rejects unsafe media and duplicate ids", () => {
    expect(catalogSchema.safeParse({ ...catalog, wallpapers: [{ ...wallpaper, video: "javascript:alert(1)" }] }).success).toBe(false);
    expect(catalogSchema.safeParse({ ...catalog, wallpapers: [wallpaper, wallpaper] }).success).toBe(false);
  });
});