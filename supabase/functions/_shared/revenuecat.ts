// RevenueCat webhook → premium state (A11). Pure logic, unit-tested in tests/revenuecat_test.ts.
// Docs: https://www.revenuecat.com/docs/integrations/webhooks/event-types-and-fields

export const ENTITLEMENT = "premium";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface RcEvent {
  id: string;
  type: string;
  environment?: string;
  app_user_id?: string;
  original_app_user_id?: string;
  aliases?: string[];
  transferred_from?: string[];
  transferred_to?: string[];
  entitlement_ids?: string[] | null;
  entitlement_id?: string | null;
  expiration_at_ms?: number | null;
}

/** Events that start or continue access until expiration_at_ms. */
const GRANTS = new Set([
  "INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION", "PRODUCT_CHANGE", "NON_RENEWING_PURCHASE",
  "SUBSCRIPTION_EXTENDED", "TEMPORARY_ENTITLEMENT_GRANT",
]);
/** Events that keep access until it expires (auto-renew off, payment trouble): update the date only. */
const KEEPS = new Set(["CANCELLATION", "BILLING_ISSUE", "SUBSCRIPTION_PAUSED"]);

export interface Change {
  userIds: string[];          // Supabase user ids (signed-in users only; anonymous RC ids are skipped)
  premium: boolean | null;    // null = leave is_premium as it is
  premiumUntil: string | null | undefined; // ISO; null = lifetime/none; undefined = leave as it is
}

/** Supabase user ids among the ids RevenueCat knows for this customer. */
export function supabaseIds(ids: (string | undefined | null)[]): string[] {
  return [...new Set(ids.filter((v): v is string => !!v && UUID_RE.test(v)).map((v) => v.toLowerCase()))];
}

function touchesPremium(e: RcEvent): boolean {
  const ids = e.entitlement_ids ?? (e.entitlement_id ? [e.entitlement_id] : null);
  return ids === null || ids.includes(ENTITLEMENT); // older events may not list entitlements
}

/** Decides what to write for one event. Returns null when there's nothing to do. */
export function decide(e: RcEvent, now = Date.now()): Change[] | null {
  if (e.type === "TEST") return null;

  if (e.type === "TRANSFER") {
    // Access moved to another account; the receiving side gets its own grant events from RevenueCat.
    const from = supabaseIds(e.transferred_from ?? []);
    return from.length ? [{ userIds: from, premium: false, premiumUntil: null }] : null;
  }

  const userIds = supabaseIds([e.app_user_id, e.original_app_user_id, ...(e.aliases ?? [])]);
  if (!userIds.length || !touchesPremium(e)) return null;
  const until = e.expiration_at_ms ? new Date(e.expiration_at_ms).toISOString() : null;

  if (GRANTS.has(e.type)) {
    const active = !e.expiration_at_ms || e.expiration_at_ms > now;
    return [{ userIds, premium: active, premiumUntil: until }];
  }
  if (KEEPS.has(e.type)) {
    const active = !!e.expiration_at_ms && e.expiration_at_ms > now;
    return [{ userIds, premium: active ? null : false, premiumUntil: until ?? undefined }];
  }
  if (e.type === "EXPIRATION") {
    return [{ userIds, premium: false, premiumUntil: until ?? undefined }];
  }
  return null;
}

/** Constant-time comparison of the Authorization header with the configured secret. */
export function authorized(header: string | null, secret: string | undefined): boolean {
  if (!secret || !header) return false;
  const a = new TextEncoder().encode(header.replace(/^Bearer\s+/i, "").trim());
  const b = new TextEncoder().encode(secret.trim());
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}
