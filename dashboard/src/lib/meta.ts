import type { WallpaperMeta } from './types';

export function isMetaValid(m: WallpaperMeta) {
  return !!m.title.trim() && !!m.license && (m.status !== 'scheduled' || !!m.publish_at);
}
