import dayjs from 'dayjs';
import { supabase, SUPABASE_KEY, SUPABASE_URL } from './supabase';
import type { Processed } from './imageProcessing';
import type { Wallpaper, WallpaperMeta } from './types';

const FN_URL = `${SUPABASE_URL}/functions/v1/upload-wallpaper`;

/** fetch() that turns network/CORS failures into an actionable message. */
async function fnFetch(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch {
    throw new Error("Could not reach the upload service. The upload-wallpaper Edge Function is probably not deployed yet (see supabase/README.md), or you are offline.");
  }
}

export async function authHeaders() {
  const { data } = await supabase.auth.getSession();
  if (!data.session) throw new Error('Not signed in');
  return { Authorization: `Bearer ${data.session.access_token}`, apikey: SUPABASE_KEY };
}

async function fnError(res: Response) {
  let msg = `${res.status} ${res.statusText}`;
  try {
    const body = await res.json();
    msg = body.details ? `${body.error}: ${body.details.join('; ')}` : body.error ?? body.message ?? msg;
  } catch { /* not JSON */ }
  if (res.status === 404) msg += ' (is the upload-wallpaper function deployed?)';
  return new Error(msg);
}

export interface CdnUploadResult {
  storage_key: string;
  original_ext: 'jpg' | 'png' | null;
  repo_size_mb: number;
}

export async function uploadToCdn(p: Processed, title: string, includeOriginal: boolean): Promise<CdnUploadResult> {
  const form = new FormData();
  form.append('thumb', p.thumb, 'thumb.webp');
  form.append('preview', p.preview, 'preview.webp');
  form.append('full', p.full, 'full.webp');
  if (includeOriginal && p.original) form.append('original', p.original, `original.${p.originalExt}`);
  form.append('title', title);
  const res = await fnFetch(FN_URL, { method: 'POST', headers: await authHeaders(), body: form });
  if (!res.ok) throw await fnError(res);
  return res.json();
}

export async function deleteFromCdn(storageKey: string): Promise<void> {
  const res = await fnFetch(`${FN_URL}?storage_key=${encodeURIComponent(storageKey)}`, {
    method: 'DELETE',
    headers: await authHeaders(),
  });
  if (!res.ok) throw await fnError(res);
}

export async function cdnUsage(): Promise<{ cdn_base_url: string; repo_size_mb: number } | null> {
  try {
    const res = await fnFetch(FN_URL, { headers: await authHeaders() });
    return res.ok ? res.json() : null;
  } catch {
    return null;
  }
}

/** Lower-case letters/digits/spaces/hyphens only, 2–30 chars (no "…", quotes, emoji). */
export function cleanTag(t: string) {
  return t.toLowerCase().replace(/[^\p{L}\p{N} -]+/gu, ' ').replace(/\s+/g, ' ').trim().slice(0, 30).trim();
}

export function normalizeTags(tags: string[]) {
  return [...new Set(tags.map(cleanTag).filter((t) => t.length >= 2))].slice(0, 25);
}

/** Makes the wallpaper's tag set exactly `names`, creating new tags as needed. */
export async function syncTags(wallpaperId: string, names: string[]) {
  const clean = normalizeTags(names);
  let ids: string[] = [];
  if (clean.length) {
    const { data, error } = await supabase
      .from('tags')
      .upsert(clean.map((name) => ({ name })), { onConflict: 'name' })
      .select('id');
    if (error) throw new Error(`Saving tags failed: ${error.message}`);
    ids = data.map((t) => t.id);
  }
  let del = supabase.from('wallpaper_tags').delete().eq('wallpaper_id', wallpaperId);
  if (ids.length) del = del.not('tag_id', 'in', `(${ids.join(',')})`);
  const { error: delErr } = await del;
  if (delErr) throw new Error(delErr.message);
  if (ids.length) {
    const { error } = await supabase
      .from('wallpaper_tags')
      .upsert(ids.map((tag_id) => ({ wallpaper_id: wallpaperId, tag_id })), { ignoreDuplicates: true });
    if (error) throw new Error(error.message);
  }
}

/** Converts form metadata into wallpaper columns. */
export function metaToRow(m: WallpaperMeta) {
  return {
    title: m.title.trim() || 'Untitled',
    category_id: m.category_id,
    device_type: m.device_type,
    is_amoled: m.is_amoled,
    is_curated: m.is_curated,
    is_premium: m.is_premium,
    is_published: m.status !== 'draft',
    publish_at: m.status === 'scheduled' && m.publish_at ? dayjs(m.publish_at).toISOString() : null,
    author_name: m.author_name.trim() || null,
    license: m.license || null,
    source_url: m.source_url.trim() || null,
  };
}

export function rowToMeta(w: Wallpaper): WallpaperMeta {
  const scheduled = w.is_published && w.publish_at && dayjs(w.publish_at).isAfter(dayjs());
  return {
    title: w.title,
    category_id: w.category_id,
    tags: (w.wallpaper_tags ?? []).map((t) => t.tags?.name).filter((n): n is string => !!n),
    device_type: w.device_type,
    is_amoled: w.is_amoled,
    is_curated: w.is_curated,
    is_premium: w.is_premium,
    status: !w.is_published ? 'draft' : scheduled ? 'scheduled' : 'published',
    publish_at: w.publish_at ? dayjs(w.publish_at).format('YYYY-MM-DD HH:mm:ss') : null,
    author_name: w.author_name ?? '',
    license: w.license ?? '',
    source_url: w.source_url ?? '',
  };
}

export async function nextCuratedOrder() {
  const { data } = await supabase
    .from('wallpapers')
    .select('curated_order')
    .eq('is_curated', true)
    .order('curated_order', { ascending: false, nullsFirst: false })
    .limit(1);
  return (data?.[0]?.curated_order ?? 0) + 1;
}

/** Full upload: CDN files → DB row → tags. Rolls the CDN back if the DB insert fails. */
export async function createWallpaper(p: Processed, meta: WallpaperMeta, includeOriginal: boolean) {
  const cdn = await uploadToCdn(p, meta.title, includeOriginal);
  const row = {
    ...metaToRow(meta),
    storage_key: cdn.storage_key,
    original_ext: cdn.original_ext ?? 'jpg',
    width: p.width,
    height: p.height,
    dominant_color: p.dominantColor,
    blurhash: p.blurhash,
    phash: p.phash,
    file_size_kb: Math.round(p.full.size / 1024),
    curated_order: meta.is_curated ? await nextCuratedOrder() : null,
  };
  const { data, error } = await supabase.from('wallpapers').insert(row).select('id').single();
  if (error) {
    await deleteFromCdn(cdn.storage_key).catch(() => undefined);
    throw new Error(`Saving wallpaper failed: ${error.message}`);
  }
  await syncTags(data.id, meta.tags);
  return { id: data.id as string, repoSizeMb: cdn.repo_size_mb };
}

export async function updateWallpaper(w: Wallpaper, meta: WallpaperMeta) {
  const row: Record<string, unknown> = metaToRow(meta);
  if (meta.is_curated && !w.is_curated) row.curated_order = await nextCuratedOrder();
  if (!meta.is_curated) row.curated_order = null;
  const { error } = await supabase.from('wallpapers').update(row).eq('id', w.id);
  if (error) throw new Error(error.message);
  await syncTags(w.id, meta.tags);
}

/** Deletes the DB row first (so the app stops showing it), then the CDN files. */
export async function deleteWallpaper(w: Pick<Wallpaper, 'id' | 'storage_key'>): Promise<string | null> {
  const { error } = await supabase.from('wallpapers').delete().eq('id', w.id);
  if (error) throw new Error(error.message);
  try {
    await deleteFromCdn(w.storage_key);
    return null;
  } catch (e) {
    return `Removed from the app, but CDN cleanup failed: ${(e as Error).message}`;
  }
}

export async function loadAllTagNames(): Promise<string[]> {
  const { data } = await supabase.from('tags').select('name').order('name').limit(2000);
  return (data ?? []).map((t) => t.name);
}

export type PhashRow = { id: string; title: string; phash: string; dominant_color: string | null };

export async function loadPhashes(): Promise<PhashRow[]> {
  const { data } = await supabase.from('wallpapers').select('id,title,phash,dominant_color').not('phash', 'is', null).limit(10000);
  return (data ?? []) as PhashRow[];
}
