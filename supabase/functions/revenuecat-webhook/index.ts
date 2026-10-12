// revenuecat-webhook — keeps profiles.is_premium / premium_until in sync with RevenueCat (Dev A, task A11)
//
//   POST (from RevenueCat)  body { api_version, event: {...} }  →  200 always once authorized, so RevenueCat
//                           doesn't retry events we deliberately ignore; 401 if the header is wrong.
//
// Setup: RevenueCat → Project → Integrations → Webhooks → URL = https://<ref>.supabase.co/functions/v1/revenuecat-webhook,
//        Authorization header = the same value as the REVENUECAT_WEBHOOK_AUTH secret.
// Deploy with --no-verify-jwt (RevenueCat doesn't send a Supabase JWT).
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json } from "../_shared/cors.ts";
import { authorized, decide, supabaseIds, type RcEvent } from "../_shared/revenuecat.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);
  if (!authorized(req.headers.get("Authorization"), Deno.env.get("REVENUECAT_WEBHOOK_AUTH"))) {
    return json({ error: "unauthorized" }, 401);
  }

  const body = await req.json().catch(() => null);
  const event = body?.event as RcEvent | undefined;
  if (!event?.id || !event.type) return json({ error: "no event in body" }, 400);

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  // Idempotency: RevenueCat retries until it gets a 200, so the same event can arrive twice.
  const { data: seen } = await db.from("billing_events").select("id").eq("id", event.id).maybeSingle();
  if (seen) return json({ ok: true, duplicate: true });

  const changes = decide(event) ?? [];
  const written: { userId: string; premium: boolean | null; until: string | null | undefined }[] = [];
  for (const c of changes) {
    const patch: Record<string, unknown> = {};
    if (c.premium !== null) patch.is_premium = c.premium;
    if (c.premiumUntil !== undefined) patch.premium_until = c.premiumUntil;
    if (!Object.keys(patch).length) continue;
    for (const id of c.userIds) {
      const { error } = await db.from("profiles").update(patch).eq("id", id);
      if (error) return json({ error: `profile update failed: ${error.message}` }, 500); // RevenueCat retries
      written.push({ userId: id, premium: c.premium, until: c.premiumUntil });
    }
  }

  const firstUser = written[0]?.userId ?? supabaseIds([event.app_user_id, event.original_app_user_id])[0] ?? null;
  const { error: logErr } = await db.from("billing_events").insert({
    id: event.id,
    type: event.type,
    environment: event.environment ?? null,
    app_user_id: event.app_user_id ?? null,
    user_id: firstUser,
    premium: written[0]?.premium ?? null,
    premium_until: written[0]?.until ?? null,
    payload: body,
  });
  // A foreign-key miss (user deleted) shouldn't make RevenueCat retry forever.
  if (logErr && !logErr.message.includes("foreign key")) return json({ error: `log failed: ${logErr.message}` }, 500);

  return json({ ok: true, updated: written.length });
});
