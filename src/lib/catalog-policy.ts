import { z } from "zod";

const mediaUrl = z.string().url().refine((value) => new URL(value).protocol === "https:");
export const catalogSchema = z.object({
  schemaVersion: z.literal(1),
  categories: z.array(z.object({ id: z.string().min(1).max(80), label: z.string().min(1).max(100) })).min(1).max(100),
  wallpapers: z.array(z.object({
    id: z.string().regex(/^[a-z0-9-]+$/).max(100),
    title: z.string().min(1).max(200), subtitle: z.string().max(500),
    category: z.string().min(1).max(80), src: mediaUrl, video: mediaUrl,
    resolution: z.enum(["4K", "8K"]), sizeMb: z.number().nonnegative(),
    fps: z.union([z.literal(30), z.literal(60)]), premium: z.boolean(),
    sound: z.boolean(), accent: z.string().max(100),
  })).min(1).max(5000),
}).superRefine((catalog, ctx) => {
  const ids = new Set<string>();
  const categories = new Set(catalog.categories.map((c) => c.id));
  for (const wallpaper of catalog.wallpapers) {
    if (ids.has(wallpaper.id) || !categories.has(wallpaper.category)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "Invalid catalog entry" });
    }
    ids.add(wallpaper.id);
  }
});

export type RemoteCatalog = z.infer<typeof catalogSchema>;
export const CATALOG_CACHE_KEY = "aetherx-catalog-v1";