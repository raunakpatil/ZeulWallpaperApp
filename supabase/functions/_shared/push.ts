// FCM HTTP v1 helpers for send-push (A16). Contract: docs/CONTRACTS.md section 8.

export const TOPICS = ["all", "new_drops", "daily_wallpaper"] as const;
export type Topic = typeof TOPICS[number];
export type Env = "prod" | "debug";
export type PushType = "new_drop" | "daily" | "collection" | "announcement";

export const SECTIONS = ["for_you", "curated", "popular", "new", "amoled", "tablet", "premium"];
export const TITLE_MAX = 65;
export const BODY_MAX = 240;
export const PROD_SENDS_PER_DAY = 6;

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface ManualRequest {
  title: string;
  body: string;
  topic: Topic;
  env: Env;
  type: PushType;
  wallpaper_id?: string | null;
  collection_id?: string | null;
  section?: string | null;
}

/** Validates the dashboard's request body. Returns the cleaned request or a list of problems. */
export function parseManual(input: unknown): ManualRequest | string[] {
  const b = (input ?? {}) as Record<string, unknown>;
  const errors: string[] = [];
  const title = String(b.title ?? "").trim();
  const body = String(b.body ?? "").trim();
  if (!title) errors.push("title is required");
  if (title.length > TITLE_MAX) errors.push(`title is longer than ${TITLE_MAX} characters`);
  if (body.length > BODY_MAX) errors.push(`message is longer than ${BODY_MAX} characters`);
  const topic = b.topic as Topic;
  if (!TOPICS.includes(topic)) errors.push(`topic must be one of ${TOPICS.join(", ")}`);
  const env = b.env as Env;
  if (env !== "prod" && env !== "debug") errors.push("env must be prod or debug");
  const ids = [b.wallpaper_id, b.collection_id, b.section].filter((v) => v);
  if (ids.length > 1) errors.push("open either a wallpaper, a collection or a section, not several");
  for (const k of ["wallpaper_id", "collection_id"] as const) {
    if (b[k] && !UUID_RE.test(String(b[k]))) errors.push(`${k} is not a valid id`);
  }
  if (b.section && !SECTIONS.includes(String(b.section))) errors.push(`section must be one of ${SECTIONS.join(", ")}`);
  if (errors.length) return errors;

  const type: PushType = b.collection_id ? "collection"
    : topic === "daily_wallpaper" ? "daily"
    : topic === "all" ? "announcement"
    : "new_drop";
  return {
    title, body, topic, env, type,
    wallpaper_id: (b.wallpaper_id as string) || null,
    collection_id: (b.collection_id as string) || null,
    section: (b.section as string) || null,
  };
}

export function linkFor(r: { wallpaper_id?: string | null; collection_id?: string | null; section?: string | null }): string {
  if (r.wallpaper_id) return `zeul://wallpaper/${r.wallpaper_id}`;
  if (r.collection_id) return `zeul://collection/${r.collection_id}`;
  if (r.section) return `zeul://section/${r.section}`;
  return "";
}

export interface PushData {
  type: PushType;
  title: string;
  body: string;
  image_url: string;
  link: string;
  wallpaper_id: string;
  collection_id: string;
  push_id: string;
}

/** FCM v1 message: data-only, high priority, expires after a day. */
export function buildMessage(topic: string, data: PushData) {
  return {
    message: {
      topic,
      data: { ...data } as Record<string, string>,
      android: { priority: "high", ttl: "86400s" },
    },
  };
}

// ---------------------------------------------------------------------------------------------
// Google OAuth for a service account (RS256 JWT → access token), using WebCrypto only.
// ---------------------------------------------------------------------------------------------

export interface ServiceAccount { project_id: string; client_email: string; private_key: string }

/** Accepts the downloaded JSON as-is, or base64 of it. */
export function parseServiceAccount(raw: string | undefined): ServiceAccount | null {
  if (!raw) return null;
  let text = raw.trim();
  if (!text.startsWith("{")) {
    try { text = atob(text); } catch { return null; }
  }
  try {
    const j = JSON.parse(text);
    if (j.project_id && j.client_email && j.private_key) return j as ServiceAccount;
  } catch { /* fall through */ }
  return null;
}

const b64url = (bytes: Uint8Array | string) => {
  const bin = typeof bytes === "string" ? bytes : String.fromCharCode(...bytes);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

export function pemToDer(pem: string): Uint8Array<ArrayBuffer> {
  const b64 = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\\n/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

export async function signJwt(sa: ServiceAccount, nowSec = Math.floor(Date.now() / 1000)): Promise<string> {
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: nowSec,
    exp: nowSec + 3600,
  }));
  const key = await crypto.subtle.importKey(
    "pkcs8", pemToDer(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`)));
  return `${header}.${claims}.${b64url(sig)}`;
}

let cached: { token: string; exp: number } | null = null;

export async function accessToken(sa: ServiceAccount, fetchImpl: typeof fetch = fetch): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 120 > now) return cached.token;
  const res = await fetchImpl("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: await signJwt(sa, now) }),
  });
  const j = await res.json();
  if (!res.ok || !j.access_token) throw new Error(`Google auth failed: ${j.error_description ?? j.error ?? res.status}`);
  cached = { token: j.access_token, exp: now + (j.expires_in ?? 3600) };
  return cached.token;
}

/** Sends one message. Returns the FCM message name (projects/…/messages/…). */
export async function sendFcm(sa: ServiceAccount, msg: ReturnType<typeof buildMessage>, fetchImpl: typeof fetch = fetch): Promise<string> {
  const res = await fetchImpl(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${await accessToken(sa, fetchImpl)}`, "Content-Type": "application/json" },
    body: JSON.stringify(msg),
  });
  const j = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`FCM ${res.status}: ${j?.error?.message ?? "send failed"}`);
  return j.name as string;
}
