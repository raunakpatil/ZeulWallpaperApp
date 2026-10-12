// Minimal GitHub client that commits several files in ONE commit (one Pages build)
// using the Git Data API: blobs → tree → commit → move branch ref.
import { encodeBase64 } from "jsr:@std/encoding@1/base64";

export interface GitHubConfig {
  token: string;
  repo: string;   // "owner/name"
  branch: string; // "main"
}

export interface NewFile {
  path: string;
  bytes: Uint8Array;
}

export class GitHubError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export function configFromEnv(): GitHubConfig {
  const token = Deno.env.get("GITHUB_TOKEN");
  const repo = Deno.env.get("CDN_REPO");
  if (!token || !repo) throw new Error("GITHUB_TOKEN and CDN_REPO secrets must be set");
  return { token, repo, branch: Deno.env.get("CDN_BRANCH") ?? "main" };
}

export function cdnBaseUrl(cfg: GitHubConfig): string {
  const fromEnv = Deno.env.get("CDN_BASE_URL");
  if (fromEnv) return fromEnv.replace(/\/+$/, "");
  const [owner, name] = cfg.repo.split("/");
  return `https://${owner.toLowerCase()}.github.io/${name}`;
}

async function gh<T>(cfg: GitHubConfig, method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(`https://api.github.com/repos/${cfg.repo}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${cfg.token}`,
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      "User-Agent": "wallpaper-app-upload",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    throw new GitHubError(res.status, `GitHub ${method} ${path} → ${res.status}: ${await res.text()}`);
  }
  return res.status === 204 ? (undefined as T) : await res.json() as T;
}

type TreeEntry = { path: string; mode: "100644"; type: "blob"; sha: string | null };

/** Applies tree changes on top of the branch head, retrying if the branch moved meanwhile. */
async function commitTree(cfg: GitHubConfig, entries: TreeEntry[], message: string): Promise<string> {
  for (let attempt = 1; ; attempt++) {
    const ref = await gh<{ object: { sha: string } }>(cfg, "GET", `/git/ref/heads/${cfg.branch}`);
    const head = await gh<{ tree: { sha: string } }>(cfg, "GET", `/git/commits/${ref.object.sha}`);
    const tree = await gh<{ sha: string }>(cfg, "POST", "/git/trees", { base_tree: head.tree.sha, tree: entries });
    const commit = await gh<{ sha: string }>(cfg, "POST", "/git/commits", {
      message,
      tree: tree.sha,
      parents: [ref.object.sha],
    });
    try {
      await gh(cfg, "PATCH", `/git/refs/heads/${cfg.branch}`, { sha: commit.sha, force: false });
      return commit.sha;
    } catch (e) {
      // 422 = not a fast-forward (another upload landed first) → rebuild on the new head
      if (e instanceof GitHubError && e.status === 422 && attempt < 4) continue;
      throw e;
    }
  }
}

export async function commitFiles(cfg: GitHubConfig, files: NewFile[], message: string): Promise<string> {
  const entries: TreeEntry[] = [];
  for (const f of files) {
    const blob = await gh<{ sha: string }>(cfg, "POST", "/git/blobs", {
      content: encodeBase64(f.bytes),
      encoding: "base64",
    });
    entries.push({ path: f.path, mode: "100644", type: "blob", sha: blob.sha });
  }
  return await commitTree(cfg, entries, message);
}

/** Deletes every file directly inside `dir`. Returns the commit sha, or null if the folder doesn't exist. */
export async function deleteDir(cfg: GitHubConfig, dir: string, message: string): Promise<string | null> {
  let listing: { path: string; type: string }[];
  try {
    listing = await gh(cfg, "GET", `/contents/${dir}?ref=${cfg.branch}`);
  } catch (e) {
    if (e instanceof GitHubError && e.status === 404) return null;
    throw e;
  }
  const entries: TreeEntry[] = listing
    .filter((x) => x.type === "file")
    .map((x) => ({ path: x.path, mode: "100644", type: "blob", sha: null }));
  if (entries.length === 0) return null;
  return await commitTree(cfg, entries, message);
}

/** Repository size in MB (GitHub updates this lazily, so it can lag by a few minutes). */
export async function repoSizeMb(cfg: GitHubConfig): Promise<number> {
  const r = await fetch(`https://api.github.com/repos/${cfg.repo}`, {
    headers: { Authorization: `Bearer ${cfg.token}`, "User-Agent": "wallpaper-app-upload" },
  });
  if (!r.ok) return -1;
  const { size } = await r.json() as { size: number }; // KB
  return Math.round((size / 1024) * 10) / 10;
}
