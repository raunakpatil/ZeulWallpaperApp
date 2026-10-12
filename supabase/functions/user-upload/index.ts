// user-upload — signed-in users publish their own wallpapers from the app (Dev A, 2026-10-11)
//
//   GET                               → { enabled, daily_limit, used_today, remaining }
//   POST   multipart/form-data        → 201 { id, storage_key, live_at }
//          files: thumb, preview, full (webp, required), original (jpg/png, optional)
//          fields: title, category_id, tags (comma separated, ≤ 6), device_type, width, height,
//                  blurhash, dominant_color, is_amoled ("true"/"false"), rights ("yes" = the user
//                  confirmed they own the image or may share it and it follows the content rules)
//   DELETE ?id=<wallpaper uuid>       → removes one of the caller's own uploads (row + CDN files)
//
// The phone makes the variants (same sizes as the dashboard, CONTRACTS §3); this function checks
// them, commits them to the CDN and inserts the row with the service role. Uploads go live
// straight away (publish_at = now + 90 s so GitHub Pages has the files before the app shows them).
// Guardrails: real (non-anonymous) account, not upload_banned, app_config.user_uploads.enabled,
// daily_limit per user; reports auto-hide user uploads (migration 1300).
// Deploy: npx supabase functions deploy user-upload --project-ref <ref>   (JWT verified by the gateway)
import { createClient, type SupabaseClient, type User } from "jsr:@supabase/supabase-js@2";
import { corsHeaders, json } from "../_shared/cors.ts";
import { cdnBaseUrl, commitFiles, configFromEnv, deleteDir } from "../_shared/github.ts";
import { fileName, newStorageKey, validateVariant, type ValidFile, type Variant } from "../_shared/images.ts";

const VARIANTS: Variant[] = ["thumb", "preview", "full", "original"];
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const COLOR_RE = /^#[0-9a-fA-F]{6}$/;
const BLURHASH_RE = /^[0-9A-Za-z#$%*+,\-.:;=?@[\]^_{|}~]{6,120}$/;
const TAG_RE = /^[\p{L}\p{N}][\p{L}\p{N} -]{1,23}$/u;
const PUBLISH_DELAY_MS = 90_000;
const MIN_LONG_EDGE = 1280;
const MIN_SHORT_EDGE = 720;

interface UploadConfig { enabled: boolean; daily_limit: number }

const admin = () => createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

async function currentUser(req: Request): Promise<User | Response> {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "sign in to upload" }, 401);
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, (Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY"))!, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data, error } = await supabase.auth.getUser();
  if (error || !data.user) return json({ error: "sign in to upload" }, 401);
  if (data.user.is_anonymous) return json({ error: "sign in to upload" }, 401);
  return data.user;
}

async function uploadConfig(db: SupabaseClient): Promise<UploadConfig> {
  const { data } = await db.from("app_config").select("value").eq("key", "user_uploads").maybeSingle();
  const v = (data?.value ?? {}) as Record<string, unknown>;
  return {
    enabled: v.enabled !== false,
    daily_limit: typeof v.daily_limit === "number" && v.daily_limit >= 0 ? Math.floor(v.daily_limit) : 5,
  };
}

async function usedToday(db: SupabaseClient, userId: string): Promise<number> {
  const since = new Date(Date.now() - 24 * 3600_000).toISOString();
  const { count, error } = await db.from("wallpapers").select("id", { count: "exact", head: true })
    .eq("uploader_id", userId).gte("created_at", since);
  if (error) throw new Error(`limit check failed: ${error.message}`);
  return count ?? 0;
}

function cleanTitle(raw: string): string {
  return raw.replace(/[\p{Cc}\p{Cf}]/gu, "").replace(/\s+/g, " ").trim().slice(0, 60);
}

function cleanTags(raw: string): string[] {
  const out = new Set<string>();
  for (const t of raw.split(",")) {
    const tag = t.replace(/[\p{Cc}\p{Cf}#]/gu, "").replace(/\s+/g, " ").trim().toLowerCase();
    if (TAG_RE.test(tag)) out.add(tag);
    if (out.size >= 6) break;
  }
  return [...out];
}

async function handleStatus(db: SupabaseClient, user: User): Promise<Response> {
  const cfg = await uploadConfig(db);
  const used = await usedToday(db, user.id);
  return json({ enabled: cfg.enabled, daily_limit: cfg.daily_limit, used_today: used, remaining: Math.max(cfg.daily_limit - used, 0) });
}

async function handleUpload(req: Request, db: SupabaseClient, user: User): Promise<Response> {
  const cfg = await uploadConfig(db);
  if (!cfg.enabled) return json({ error: "Uploads are paused right now. Please try again later." }, 403);

  const { data: profile } = await db.from("profiles").select("display_name, upload_banned").eq("id", user.id).maybeSingle();
  if (profile?.upload_banned) return json({ error: "Your account can't upload wallpapers." }, 403);
  if (await usedToday(db, user.id) >= cfg.daily_limit) {
    return json({ error: `You can upload ${cfg.daily_limit} wallpapers a day. Please try again tomorrow.` }, 429);
  }

  if (!(req.headers.get("content-type") ?? "").startsWith("multipart/form-data")) {
    return json({ error: "expected multipart/form-data" }, 415);
  }
  const form = await req.formData();
  const field = (k: string) => String(form.get(k) ?? "");

  const errors: string[] = [];
  if (field("rights") !== "yes") errors.push("confirm that you own this image or have the right to share it");
  const title = cleanTitle(field("title"));
  if (title.length < 2) errors.push("title must be at least 2 characters");
  const categoryId = field("category_id");
  if (!UUID_RE.test(categoryId)) errors.push("pick a category");
  const deviceType = field("device_type");
  if (!["phone", "tablet", "both"].includes(deviceType)) errors.push("invalid device_type");
  const width = Number.parseInt(field("width"), 10);
  const height = Number.parseInt(field("height"), 10);
  if (!(width > 0 && height > 0 && width <= 16000 && height <= 16000)) errors.push("invalid width/height");
  else if (Math.max(width, height) < MIN_LONG_EDGE || Math.min(width, height) < MIN_SHORT_EDGE) {
    errors.push(`image is too small (${width}×${height}); wallpapers need at least ${MIN_SHORT_EDGE}×${MIN_LONG_EDGE}`);
  }
  const blurhash = field("blurhash");
  if (!BLURHASH_RE.test(blurhash)) errors.push("invalid blurhash");
  const color = field("dominant_color");
  if (!COLOR_RE.test(color)) errors.push("invalid dominant_color");

  const files: ValidFile[] = [];
  for (const v of VARIANTS) {
    const entry = form.get(v);
    const bytes = entry instanceof File ? new Uint8Array(await entry.arrayBuffer()) : null;
    const result = validateVariant(v, bytes);
    if (typeof result === "string") errors.push(result);
    else if (result) files.push(result);
  }
  if (errors.length) return json({ error: errors[0], details: errors }, 400);

  const { data: category } = await db.from("categories").select("id").eq("id", categoryId).maybeSingle();
  if (!category) return json({ error: "pick a category" }, 400);

  const cdn = configFromEnv();
  const storageKey = newStorageKey();
  await commitFiles(cdn, files.map((f) => ({ path: `${storageKey}/${fileName(f)}`, bytes: f.bytes })), `user upload ${storageKey}`);

  const original = files.find((f) => f.variant === "original");
  const full = files.find((f) => f.variant === "full")!;
  const liveAt = new Date(Date.now() + PUBLISH_DELAY_MS).toISOString();
  const { data: row, error } = await db.from("wallpapers").insert({
    title,
    storage_key: storageKey,
    original_ext: original?.type ?? "jpg",
    width,
    height,
    device_type: deviceType,
    is_amoled: field("is_amoled") === "true",
    is_premium: false,
    is_published: true,
    publish_at: liveAt,
    dominant_color: color.toUpperCase(),
    blurhash,
    category_id: categoryId,
    file_size_kb: Math.round(full.bytes.length / 1024),
    author_name: cleanTitle(profile?.display_name ?? "") || "Zeul user",
    license: "User upload",
    uploader_id: user.id,
  }).select("id").single();
  if (error || !row) {
    await deleteDir(cdn, storageKey, `rollback ${storageKey}`).catch(() => undefined);
    throw new Error(`saving the wallpaper failed: ${error?.message}`);
  }

  const tags = cleanTags(field("tags"));
  if (tags.length) {
    const { data: tagRows, error: tagErr } = await db.from("tags").upsert(tags.map((name) => ({ name })), { onConflict: "name" }).select("id");
    if (!tagErr && tagRows?.length) {
      await db.from("wallpaper_tags").upsert(tagRows.map((t) => ({ wallpaper_id: row.id, tag_id: t.id })), { ignoreDuplicates: true });
    }
  }

  return json({ id: row.id, storage_key: storageKey, live_at: liveAt, cdn_base_url: cdnBaseUrl(cdn) }, 201);
}

async function handleDelete(url: URL, db: SupabaseClient, user: User): Promise<Response> {
  const id = url.searchParams.get("id") ?? "";
  if (!UUID_RE.test(id)) return json({ error: "invalid id" }, 400);
  const { data: w } = await db.from("wallpapers").select("id, storage_key, uploader_id").eq("id", id).maybeSingle();
  if (!w || w.uploader_id !== user.id) return json({ error: "not found" }, 404);
  const { error } = await db.from("wallpapers").delete().eq("id", id);
  if (error) throw new Error(`delete failed: ${error.message}`);
  await deleteDir(configFromEnv(), w.storage_key, `user removed ${w.storage_key}`).catch((e) => console.error(e));
  return json({ id, deleted: true });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const user = await currentUser(req);
    if (user instanceof Response) return user;
    const db = admin();
    switch (req.method) {
      case "GET": return await handleStatus(db, user);
      case "POST": return await handleUpload(req, db, user);
      case "DELETE": return await handleDelete(new URL(req.url), db, user);
      default: return json({ error: "method not allowed" }, 405);
    }
  } catch (e) {
    console.error(e);
    return json({ error: "Upload failed. Please try again." }, 500);
  }
});
