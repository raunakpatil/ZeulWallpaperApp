// Everything here runs in the browser, so processing costs nothing.
// Produces the CDN variants (contract section 3) plus analysis used to pre-fill the form.
import { encode as encodeBlurhash } from 'blurhash';
import type { DeviceType } from './types';

export const LIMITS = {
  thumb: 300 * 1024,
  preview: 1.5 * 1024 * 1024,
  full: 6 * 1024 * 1024,
  original: 15 * 1024 * 1024,
};
const FULL_MAX_EDGE = 2560; // app_config.free_max_resolution default

export interface Processed {
  width: number;
  height: number;
  thumb: Blob;
  preview: Blob;
  full: Blob;
  original: Blob | null;
  originalExt: 'jpg' | 'png' | null;
  blurhash: string;
  dominantColor: string;
  amoledRatio: number;
  phash: string;
  suggestedDevice: DeviceType;
  suggestedTitle: string;
  suggestedTags: string[];
  sourceBytes: number;
}

function canvas(w: number, h: number) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  const ctx = c.getContext('2d', { willReadFrequently: true })!;
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  return { c, ctx };
}

function draw(bmp: ImageBitmap, w: number, h: number) {
  const r = canvas(Math.max(1, Math.round(w)), Math.max(1, Math.round(h)));
  r.ctx.drawImage(bmp, 0, 0, r.c.width, r.c.height);
  return r;
}

function toBlob(c: HTMLCanvasElement, type: string, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) =>
    c.toBlob((b) => {
      if (!b) return reject(new Error('Image encoding failed'));
      if (b.type !== type) return reject(new Error(`This browser cannot encode ${type}. Use Chrome or Edge.`));
      resolve(b);
    }, type, quality),
  );
}

/** Encodes as WebP, lowering quality until it fits under maxBytes. */
async function webpUnder(bmp: ImageBitmap, w: number, h: number, maxBytes: number, q = 0.9): Promise<Blob> {
  const { c } = draw(bmp, w, h);
  for (let quality = q; quality >= 0.5; quality -= 0.1) {
    const b = await toBlob(c, 'image/webp', quality);
    if (b.size <= maxBytes) return b;
  }
  throw new Error(`Could not compress below ${(maxBytes / 1048576).toFixed(1)} MB`);
}

function scaleToWidth(w: number, h: number, target: number) {
  const s = Math.min(1, target / w);
  return [w * s, h * s] as const;
}

function scaleLongEdge(w: number, h: number, maxEdge: number) {
  const s = Math.min(1, maxEdge / Math.max(w, h));
  return [w * s, h * s] as const;
}

function dominantColor(data: Uint8ClampedArray): string {
  const buckets = new Map<number, { n: number; r: number; g: number; b: number }>();
  for (let i = 0; i < data.length; i += 4) {
    if (data[i + 3] < 128) continue;
    const key = ((data[i] >> 4) << 8) | ((data[i + 1] >> 4) << 4) | (data[i + 2] >> 4);
    const e = buckets.get(key) ?? { n: 0, r: 0, g: 0, b: 0 };
    e.n++; e.r += data[i]; e.g += data[i + 1]; e.b += data[i + 2];
    buckets.set(key, e);
  }
  let best = { n: 0, r: 0, g: 0, b: 0 };
  for (const e of buckets.values()) if (e.n > best.n) best = e;
  const hex = (v: number) => Math.round(v / Math.max(best.n, 1)).toString(16).padStart(2, '0');
  return `#${hex(best.r)}${hex(best.g)}${hex(best.b)}`;
}

function amoledRatio(data: Uint8ClampedArray): number {
  let black = 0, total = 0;
  for (let i = 0; i < data.length; i += 4) {
    total++;
    if (data[i] <= 10 && data[i + 1] <= 10 && data[i + 2] <= 10) black++;
  }
  return total ? black / total : 0;
}

/** 64-bit difference hash → 16 hex chars. Similar images have a small Hamming distance. */
function dHash(bmp: ImageBitmap): string {
  const { ctx } = draw(bmp, 9, 8);
  const d = ctx.getImageData(0, 0, 9, 8).data;
  const gray = (x: number, y: number) => {
    const i = (y * 9 + x) * 4;
    return d[i] * 0.299 + d[i + 1] * 0.587 + d[i + 2] * 0.114;
  };
  let hex = '';
  for (let y = 0; y < 8; y++) {
    let byte = 0;
    for (let x = 0; x < 8; x++) byte = (byte << 1) | (gray(x, y) > gray(x + 1, y) ? 1 : 0);
    hex += byte.toString(16).padStart(2, '0');
  }
  return hex;
}

export function hamming(a: string, b: string): number {
  if (!a || !b || a.length !== b.length) return 64;
  let dist = 0;
  for (let i = 0; i < a.length; i++) {
    let x = parseInt(a[i], 16) ^ parseInt(b[i], 16);
    while (x) { dist += x & 1; x >>= 1; }
  }
  return dist;
}

/** Flat or gradient images give near-empty hashes that "match" each other. */
export function isLowInfoHash(h: string): boolean {
  let bits = 0;
  for (const ch of h) { let x = parseInt(ch, 16); while (x) { bits += x & 1; x >>= 1; } }
  return bits <= 6 || bits >= 58;
}

export function suggestDevice(w: number, h: number): DeviceType {
  const r = w / h;
  if (r < 0.62) return 'phone';   // 9:16, 9:19.5, 9:20 … (too tall to look good on a tablet)
  if (r < 1.2) return 'both';     // other portrait shapes (3:4, 4:5) and near-square: phones crop the sides
  return 'tablet';                // landscape (4:3, 16:10, 16:9): a phone would only show a thin slice
}

const STOP = new Set(['img', 'image', 'wallpaper', 'wallpapers', 'final', 'copy', 'edit', 'the', 'and', 'with', 'hd', 'uhd',
  'from', 'for', 'into', 'over', 'under', 'onto', 'its', 'are', 'was', 'his', 'her', 'their', 'this', 'that', 'some', 'very']);

function fromFileName(name: string) {
  let base = name.replace(/\.[^.]+$/, '');
  // Names cut short by the tool that made them ("…rising from back…"): the word before the
  // ellipsis is usually a fragment ("back" of "background"), so drop it and everything after.
  const cut = base.search(/…|\.\.\./);
  if (cut >= 0) base = base.slice(0, cut).replace(/[\p{L}\p{N}]+$/u, '');
  const words = base.split(/[^\p{L}\p{N}]+/u).filter((w) => w && !/^\d+$/.test(w) && !/^\d+x\d+$/i.test(w));
  const title = words.map((w) => w[0].toUpperCase() + w.slice(1).toLowerCase()).join(' ') || 'Untitled';
  const tags = [...new Set(words.map((w) => w.toLowerCase()).filter((w) => w.length >= 3 && !STOP.has(w)))];
  return { title, tags: tags.slice(0, 6) };
}

export async function processImage(file: File): Promise<Processed> {
  const bmp = await createImageBitmap(file);
  try {
    const { width, height } = bmp;
    const thumb = await webpUnder(bmp, ...scaleToWidth(width, height, 400), LIMITS.thumb, 0.8);
    const preview = await webpUnder(bmp, ...scaleToWidth(width, height, 1080), LIMITS.preview, 0.85);
    const full = await webpUnder(bmp, ...scaleLongEdge(width, height, FULL_MAX_EDGE), LIMITS.full, 0.9);

    let original: Blob | null = null;
    let originalExt: 'jpg' | 'png' | null = null;
    if ((file.type === 'image/jpeg' || file.type === 'image/png') && file.size <= LIMITS.original) {
      original = file;
      originalExt = file.type === 'image/png' ? 'png' : 'jpg';
    } else {
      const jpg = await toBlob(draw(bmp, width, height).c, 'image/jpeg', 0.95).catch(() => null);
      if (jpg && jpg.size <= LIMITS.original) {
        original = jpg;
        originalExt = 'jpg';
      }
    }

    const [aw, ah] = scaleLongEdge(width, height, 128);
    const small = draw(bmp, aw, ah);
    const px = small.ctx.getImageData(0, 0, small.c.width, small.c.height).data;

    const [bw, bh] = scaleLongEdge(width, height, 32);
    const tiny = draw(bmp, bw, bh);
    const tinyPx = tiny.ctx.getImageData(0, 0, tiny.c.width, tiny.c.height).data;
    const portrait = height >= width;
    const hash = encodeBlurhash(tinyPx, tiny.c.width, tiny.c.height, portrait ? 4 : 5, portrait ? 5 : 4);

    const { title, tags } = fromFileName(file.name);
    return {
      width, height, thumb, preview, full, original, originalExt,
      blurhash: hash,
      dominantColor: dominantColor(px),
      amoledRatio: amoledRatio(px),
      phash: dHash(bmp),
      suggestedDevice: suggestDevice(width, height),
      suggestedTitle: title,
      suggestedTags: tags,
      sourceBytes: file.size,
    };
  } finally {
    bmp.close();
  }
}
