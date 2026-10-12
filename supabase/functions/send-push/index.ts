// send-push — push notifications through Firebase Cloud Messaging (Dev A, task A16)
//
//   GET                       → { configured, project_id }                  (dashboard setup status)
//   POST { title, body, topic, env, wallpaper_id? | collection_id? | section? }   → manual send
//   POST { mode: "daily", env? }                                            → today's wallpaper push
//
// Auth: an admin's Supabase JWT, or (daily only) header x-cron-secret = CRON_SECRET (used by pg_cron).
// Secrets: FIREBASE_SERVICE_ACCOUNT (the service-account JSON, raw or base64), CRON_SECRET.
// Every send is written to public.push_log. Contract: docs/CONTRACTS.md section 8.
import { createClient, type SupabaseClient } from "jsr:@supabase/supabase-js@2";
import { json } from "../_shared/cors.ts";
import {
  buildMessage, linkFor, parseManual, parseServiceAccount, PROD_SENDS_PER_DAY, sendFcm,
  type Env, type PushData, type PushType,
} from "../_shared/push.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-cron-secret",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

const admin = () => createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

async function adminUserId(req: Request): Promise<string | Response> {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "missing Authorization header" }, 401);
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, (Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY"))!, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data: userData, error: userErr } = await supabase.auth.getUser();
  if (userErr || !userData.user) return json({ error: "invalid session" }, 401);
  const { data: isAdmin, error } = await supabase.rpc("is_admin");
  if (error) return json({ error: `admin check failed: ${error.message}` }, 500);
  if (isAdmin !== true) return json({ error: "admin only" }, 403);
  return userData.user.id;
}

async function cdnBase(db: SupabaseClient): Promise<string> {
  const { data } = await db.from("app_config").select("value").eq("key", "cdn_base_url").maybeSingle();
  return String(data?.value ?? "").replace(/\/+$/, "");
}

/** Image + validity for the thing the notification opens. */
async function target(db: SupabaseClient, wallpaperId: string | null, collectionId: string | null, env: Env) {
  const base = await cdnBase(db);
  if (wallpaperId) {
    const { data: w } = await db.from("wallpapers").select("title, storage_key, is_published, publish_at").eq("id", wallpaperId).maybeSingle();
    if (!w) return { error: "wallpaper not found" };
    const live = w.is_published && (!w.publish_at || new Date(w.publish_at) <= new Date());
    if (!live && env === "prod") return { error: "that wallpaper isn't published yet; publish it first or use Test mode" };
    return { image_url: `${base}/${w.storage_key}/preview.webp`, title: w.title as string };
  }
  if (collectionId) {
    const { data: cols, error } = await db.rpc("get_collections");
    if (error) return { error: error.message };
    const c = (cols as { id: string; title: string; cover_storage_key: string | null }[]).find((x) => x.id === collectionId);
    if (!c) return { error: "collection not found or has no published wallpapers" };
    return { image_url: c.cover_storage_key ? `${base}/${c.cover_storage_key}/preview.webp` : "", title: c.title };
  }
  return { image_url: "" };
}

async function deliver(db: SupabaseClient, opts: {
  kind: "manual" | "daily"; env: Env; topicName: string; type: PushType; title: string; body: string;
  image_url: string; link: string; wallpaper_id: string | null; collection_id: string | null; sent_by: string | null;
}): Promise<Response> {
  const sa = parseServiceAccount(Deno.env.get("FIREBASE_SERVICE_ACCOUNT"));
  if (!sa) return json({ error: "Firebase isn't set up yet: the FIREBASE_SERVICE_ACCOUNT secret is missing or invalid" }, 503);

  const topic = `${opts.env}_${opts.topicName}`;
  const { data: row, error: insErr } = await db.from("push_log").insert({
    kind: opts.kind, env: opts.env, topic, type: opts.type, title: opts.title, body: opts.body,
    image_url: opts.image_url || null, link: opts.link || null, wallpaper_id: opts.wallpaper_id,
    collection_id: opts.collection_id, sent_by: opts.sent_by, status: "failed", error: "sending",
  }).select("id").single();
  if (insErr) return json({ error: `could not log the send: ${insErr.message}` }, 500);

  const data: PushData = {
    type: opts.type, title: opts.title, body: opts.body, image_url: opts.image_url, link: opts.link,
    wallpaper_id: opts.wallpaper_id ?? "", collection_id: opts.collection_id ?? "", push_id: row.id,
  };
  try {
    const name = await sendFcm(sa, buildMessage(topic, data));
    await db.from("push_log").update({ status: "sent", fcm_message_id: name, error: null }).eq("id", row.id);
    return json({ ok: true, push_id: row.id, topic, fcm_message_id: name });
  } catch (e) {
    const msg = (e as Error).message;
    await db.from("push_log").update({ status: "failed", error: msg.slice(0, 500) }).eq("id", row.id);
    return json({ error: msg, push_id: row.id }, 502);
  }
}

async function handleManual(body: unknown, userId: string): Promise<Response> {
  const req = parseManual(body);
  if (Array.isArray(req)) return json({ error: "invalid request", details: req }, 400);
  const db = admin();

  if (req.env === "prod") {
    const since = new Date(Date.now() - 864e5).toISOString();
    const { count } = await db.from("push_log").select("id", { count: "exact", head: true })
      .eq("kind", "manual").eq("env", "prod").eq("status", "sent").gte("created_at", since);
    if ((count ?? 0) >= PROD_SENDS_PER_DAY) {
      return json({ error: `daily limit reached (${PROD_SENDS_PER_DAY} notifications to users per 24 h). Too many pushes make people turn them off.` }, 429);
    }
  }

  const t = await target(db, req.wallpaper_id ?? null, req.collection_id ?? null, req.env);
  if ("error" in t && t.error) return json({ error: t.error }, 400);
  return deliver(db, {
    kind: "manual", env: req.env, topicName: req.topic, type: req.type, title: req.title, body: req.body,
    image_url: t.image_url ?? "", link: linkFor(req), wallpaper_id: req.wallpaper_id ?? null,
    collection_id: req.collection_id ?? null, sent_by: userId,
  });
}

/** Daily push: optionally auto-picks a fresh popular wallpaper, then pushes daily_wallpaper_id. */
async function handleDaily(env: Env, userId: string | null): Promise<Response> {
  const db = admin();
  const { data: rows } = await db.from("app_config").select("key, value").in("key", ["daily_push", "daily_wallpaper_id"]);
  const cfg = (rows?.find((r) => r.key === "daily_push")?.value ?? {}) as { auto_pick?: boolean; title?: string };
  let wallpaperId = (rows?.find((r) => r.key === "daily_wallpaper_id")?.value as string | null) ?? null;

  if (cfg.auto_pick && env === "prod") {
    const { data: recent } = await db.from("push_log").select("wallpaper_id")
      .eq("kind", "daily").gte("created_at", new Date(Date.now() - 30 * 864e5).toISOString());
    const used = new Set((recent ?? []).map((r) => r.wallpaper_id).filter(Boolean));
    const { data: candidates } = await db.from("wallpapers_public").select("id")
      .order("popularity_score", { ascending: false }).limit(60);
    const fresh = (candidates ?? []).map((c) => c.id as string).filter((id) => !used.has(id));
    if (fresh.length) {
      wallpaperId = fresh[Math.floor(Math.random() * Math.min(fresh.length, 15))];
      await db.from("app_config").upsert({ key: "daily_wallpaper_id", value: wallpaperId }, { onConflict: "key" });
    }
  }
  if (!wallpaperId) return json({ skipped: "no daily wallpaper set (App settings → Daily wallpaper, or turn on auto-pick)" }, 200);

  const t = await target(db, wallpaperId, null, env);
  if ("error" in t && t.error) return json({ error: t.error }, 400);
  return deliver(db, {
    kind: "daily", env, topicName: "daily_wallpaper", type: "daily",
    title: (cfg.title || "Today's wallpaper ✨").slice(0, 65), body: (t.title ?? "Tap to see it").slice(0, 240),
    image_url: t.image_url ?? "", link: linkFor({ wallpaper_id: wallpaperId }), wallpaper_id: wallpaperId,
    collection_id: null, sent_by: userId,
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const res = await (async () => {
    try {
      if (req.method === "GET") {
        const who = await adminUserId(req);
        if (who instanceof Response) return who;
        const sa = parseServiceAccount(Deno.env.get("FIREBASE_SERVICE_ACCOUNT"));
        return json({ configured: !!sa, project_id: sa?.project_id ?? null, cron_secret_set: !!Deno.env.get("CRON_SECRET") });
      }
      if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

      const body = await req.json().catch(() => ({}));
      const cronSecret = Deno.env.get("CRON_SECRET");
      const fromCron = !!cronSecret && req.headers.get("x-cron-secret") === cronSecret;

      if (body?.mode === "daily") {
        if (fromCron) return handleDaily("prod", null);
        const who = await adminUserId(req);
        if (who instanceof Response) return who;
        return handleDaily(body.env === "debug" ? "debug" : "prod", who);
      }
      const who = await adminUserId(req);
      if (who instanceof Response) return who;
      return handleManual(body, who);
    } catch (e) {
      return json({ error: (e as Error).message }, 500);
    }
  })();
  for (const [k, v] of Object.entries(cors)) res.headers.set(k, v);
  return res;
});
