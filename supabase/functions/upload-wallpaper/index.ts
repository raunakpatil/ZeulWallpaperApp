// upload-wallpaper — admin-only CDN file management (Dev A, task A5)
//
//   POST   multipart/form-data  thumb, preview, full (webp, required) + original (jpg/png, optional)
//          → 201 { storage_key, original_ext, urls{...}, cdn_base_url, commit, repo_size_mb }
//          The dashboard then inserts the wallpapers row with that storage_key.
//   DELETE ?storage_key=w/abc123def0   → removes the folder from the CDN repo
//   GET                                → { cdn_base_url, repo_size_mb }  (usage meter)
//
// Auth: caller must send a Supabase user JWT for a profile with role = 'admin'.
// Secrets: GITHUB_TOKEN (fine-grained PAT: Contents read/write on the CDN repo only), CDN_REPO,
//          optional CDN_BRANCH (default main), CDN_BASE_URL (default https://<owner>.github.io/<repo>).
import { createClient } from "jsr:@supabase/supabase-js@2";
import { corsHeaders, json } from "../_shared/cors.ts";
import { cdnBaseUrl, commitFiles, configFromEnv, deleteDir, repoSizeMb } from "../_shared/github.ts";
import { fileName, newStorageKey, STORAGE_KEY_RE, validateVariant, type ValidFile, type Variant } from "../_shared/images.ts";

const VARIANTS: Variant[] = ["thumb", "preview", "full", "original"];

async function requireAdmin(req: Request): Promise<Response | null> {
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
  return null;
}

async function handleUpload(req: Request): Promise<Response> {
  const ct = req.headers.get("content-type") ?? "";
  if (!ct.startsWith("multipart/form-data")) {
    return json({ error: "expected multipart/form-data" }, 415);
  }
  const form = await req.formData();
  const files: ValidFile[] = [];
  const errors: string[] = [];
  for (const v of VARIANTS) {
    const entry = form.get(v);
    const bytes = entry instanceof File ? new Uint8Array(await entry.arrayBuffer()) : null;
    const result = validateVariant(v, bytes);
    if (typeof result === "string") errors.push(result);
    else if (result) files.push(result);
  }
  if (errors.length) return json({ error: "invalid files", details: errors }, 400);

  const cfg = configFromEnv();
  const storageKey = newStorageKey();
  const title = String(form.get("title") ?? "").slice(0, 80);
  const commit = await commitFiles(
    cfg,
    files.map((f) => ({ path: `${storageKey}/${fileName(f)}`, bytes: f.bytes })),
    `add ${storageKey}${title ? ` (${title})` : ""}`,
  );

  const base = cdnBaseUrl(cfg);
  const original = files.find((f) => f.variant === "original");
  const urls = Object.fromEntries(files.map((f) => [f.variant, `${base}/${storageKey}/${fileName(f)}`]));
  return json({
    storage_key: storageKey,
    original_ext: original?.type ?? null,
    urls,
    cdn_base_url: base,
    commit,
    repo_size_mb: await repoSizeMb(cfg),
    note: "GitHub Pages usually publishes new files within ~1 minute.",
  }, 201);
}

async function handleDelete(url: URL): Promise<Response> {
  const key = url.searchParams.get("storage_key") ?? "";
  if (!STORAGE_KEY_RE.test(key)) return json({ error: "invalid storage_key" }, 400);
  const commit = await deleteDir(configFromEnv(), key, `remove ${key}`);
  return json({ storage_key: key, deleted: commit !== null, commit });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const denied = await requireAdmin(req);
    if (denied) return denied;

    switch (req.method) {
      case "POST":
        return await handleUpload(req);
      case "DELETE":
        return await handleDelete(new URL(req.url));
      case "GET": {
        const cfg = configFromEnv();
        return json({ cdn_base_url: cdnBaseUrl(cfg), repo_size_mb: await repoSizeMb(cfg) });
      }
      default:
        return json({ error: "method not allowed" }, 405);
    }
  } catch (e) {
    console.error(e);
    return json({ error: e instanceof Error ? e.message : String(e) }, 500);
  }
});
